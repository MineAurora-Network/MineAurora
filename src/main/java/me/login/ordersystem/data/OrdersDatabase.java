package me.login.ordersystem.data;

import me.login.Login;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class OrdersDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    public OrdersDatabase(Login plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.url = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + File.separator + "orders_data.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting Orders DB...");
            try (Statement stmt = connection.createStatement()) {
                // Main orders table
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS player_orders (
                            order_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            placer_uuid VARCHAR(36) NOT NULL, placer_name VARCHAR(16) NOT NULL,
                            item_stack TEXT NOT NULL, total_amount INTEGER NOT NULL, price_per_item REAL NOT NULL,
                            amount_delivered INTEGER DEFAULT 0,
                            creation_timestamp BIGINT NOT NULL, expiry_timestamp BIGINT NOT NULL,
                            status VARCHAR(20) NOT NULL
                        )""");
                // Storage for filled items
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS order_storage (
                            storage_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            order_id INTEGER NOT NULL,
                            placer_uuid VARCHAR(36) NOT NULL,
                            item_stack TEXT NOT NULL,
                            FOREIGN KEY(order_id) REFERENCES player_orders(order_id) ON DELETE CASCADE
                        )""");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_order_storage_order_id ON order_storage (order_id)");

                // (Points 4, 5) New tables for offline delivery
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS offline_delivery (
                            delivery_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            player_uuid VARCHAR(36) NOT NULL,
                            refund_amount REAL DEFAULT 0
                        )""");
                stmt.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS offline_delivery_items (
                            item_id INTEGER PRIMARY KEY AUTOINCREMENT,
                            delivery_id INTEGER NOT NULL,
                            item_stack TEXT NOT NULL,
                            FOREIGN KEY(delivery_id) REFERENCES offline_delivery(delivery_id) ON DELETE CASCADE
                        )""");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_offline_delivery_uuid ON offline_delivery (player_uuid)");
            }
            plugin.getLogger().info("Orders DB Connected.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed connect Orders DB!", e);
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connect();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting DB connection", e);
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Orders DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting DB", e);
        }
    }

    // --- Order Methods ---

    public CompletableFuture<Long> saveOrder(Order order) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String query = "INSERT INTO player_orders (placer_uuid, placer_name, item_stack, total_amount, price_per_item, amount_delivered, creation_timestamp, expiry_timestamp, status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, order.getPlacerUUID().toString()); ps.setString(2, order.getPlacerName());
                ps.setString(3, itemStackToBase64(order.getItem())); ps.setInt(4, order.getTotalAmount()); ps.setDouble(5, order.getPricePerItem());
                ps.setInt(6, order.getAmountDelivered());
                ps.setLong(7, order.getCreationTimestamp());
                ps.setLong(8, order.getExpiryTimestamp());
                ps.setString(9, order.getStatus().name());
                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) { throw new SQLException("Creating order failed, no rows affected."); }
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) { if (generatedKeys.next()) { future.complete(generatedKeys.getLong(1)); } else { throw new SQLException("Creating order failed, no ID obtained."); } }
            } catch (SQLException | IOException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public CompletableFuture<Order> loadOrderById(long orderId) {
        CompletableFuture<Order> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(null); return; }
            updateExpiredOrdersSync();
            String query = "SELECT * FROM player_orders WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(parseOrderFromResult(rs));
                    } else {
                        future.complete(null);
                    }
                }
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }


    public CompletableFuture<Boolean> addItemsToStorage(long orderId, UUID placerUUID, ItemStack items) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String query = "INSERT INTO order_storage (order_id, placer_uuid, item_stack) VALUES (?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, orderId);
                ps.setString(2, placerUUID.toString());
                ps.setString(3, itemStackToBase64(items));
                future.complete(ps.executeUpdate() > 0);
            } catch (SQLException | IOException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<ItemStack>> loadAndRemoveStoredItems(long orderId) {
        CompletableFuture<List<ItemStack>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ItemStack> items = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String selectQuery = "SELECT storage_id, item_stack FROM order_storage WHERE order_id = ?";
            List<Integer> idsToDelete = new ArrayList<>();
            try (PreparedStatement psSelect = conn.prepareStatement(selectQuery)) {
                psSelect.setLong(1, orderId);
                try (ResultSet rs = psSelect.executeQuery()) {
                    while (rs.next()) {
                        try {
                            items.add(itemStackFromBase64(rs.getString("item_stack")));
                            idsToDelete.add(rs.getInt("storage_id"));
                        } catch (IOException | ClassNotFoundException e) {
                            plugin.getLogger().warning("Failed to parse stored item for order " + orderId + ": " + e.getMessage());
                        }
                    }
                }
            } catch (SQLException e) { future.completeExceptionally(e); return; }
            if (!idsToDelete.isEmpty()) {
                String deleteQuery = "DELETE FROM order_storage WHERE storage_id = ?";
                try (PreparedStatement psDelete = conn.prepareStatement(deleteQuery)) {
                    for (Integer storageId : idsToDelete) { psDelete.setInt(1, storageId); psDelete.addBatch(); }
                    psDelete.executeBatch();
                } catch (SQLException e) { plugin.getLogger().log(Level.SEVERE,"Failed to delete stored items for order " + orderId + " after loading!", e); }
            }
            future.complete(items);
        });
        return future;
    }

    public CompletableFuture<Boolean> hasStoredItems(long orderId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String query = "SELECT 1 FROM order_storage WHERE order_id = ? LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, orderId);
                try (ResultSet rs = ps.executeQuery()) {
                    future.complete(rs.next());
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<Order>> loadActiveOrders() {
        CompletableFuture<List<Order>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Order> orders = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.complete(orders); return; }
            updateExpiredOrdersSync();
            String query = "SELECT * FROM player_orders WHERE status = ? AND expiry_timestamp > ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, Order.OrderStatus.ACTIVE.name()); ps.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { try { orders.add(parseOrderFromResult(rs)); } catch (Exception e) { plugin.getLogger().warning("Failed parse active order ID " + rs.getLong("order_id") + ": " + e.getMessage()); }}}
                future.complete(orders);
            } catch (SQLException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public CompletableFuture<List<Order>> loadPlayerOrders(UUID playerUUID) {
        CompletableFuture<List<Order>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Order> orders = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.complete(orders); return; }
            updateExpiredOrdersSync();
            String query = "SELECT * FROM player_orders WHERE placer_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) { while (rs.next()) { try { orders.add(parseOrderFromResult(rs)); } catch (Exception e) { plugin.getLogger().warning("Failed parse player order ID " + rs.getLong("order_id") + ": " + e.getMessage()); }}}
                future.complete(orders);
            } catch (SQLException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public CompletableFuture<Boolean> updateOrderAmountAndStatus(long orderId, int newAmountDelivered, Order.OrderStatus newStatus) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(false); return; }
            String query = "UPDATE player_orders SET amount_delivered = ?, status = ? WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setInt(1, newAmountDelivered); ps.setString(2, newStatus.name()); ps.setLong(3, orderId);
                future.complete(ps.executeUpdate() > 0);
            } catch (SQLException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public CompletableFuture<Boolean> updateOrderStatus(long orderId, Order.OrderStatus newStatus) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(false); return; }
            String query = "UPDATE player_orders SET status = ? WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, newStatus.name()); ps.setLong(2, orderId);
                future.complete(ps.executeUpdate() > 0);
            } catch (SQLException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    public CompletableFuture<Boolean> deleteOrder(long orderId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(false); return; }
            String query = "DELETE FROM player_orders WHERE order_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, orderId);
                future.complete(ps.executeUpdate() > 0);
            } catch (SQLException e) { future.completeExceptionally(e); }
        });
        return future;
    }

    private void updateExpiredOrdersSync() {
        Connection conn = getConnection();
        if (conn == null) return;
        String query = "UPDATE player_orders SET status = ? WHERE status = ? AND expiry_timestamp <= ?";
        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, Order.OrderStatus.EXPIRED.name()); ps.setString(2, Order.OrderStatus.ACTIVE.name()); ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) { plugin.getLogger().log(Level.SEVERE, "Failed update expired orders", e); }
    }

    private Order parseOrderFromResult(ResultSet rs) throws SQLException, IOException, ClassNotFoundException {
        return new Order(
                rs.getLong("order_id"), UUID.fromString(rs.getString("placer_uuid")), rs.getString("placer_name"),
                itemStackFromBase64(rs.getString("item_stack")), rs.getInt("total_amount"), rs.getDouble("price_per_item"),
                rs.getInt("amount_delivered"),
                rs.getLong("creation_timestamp"), rs.getLong("expiry_timestamp"), Order.OrderStatus.valueOf(rs.getString("status"))
        );
    }

    // --- (Points 4, 5) New Offline Delivery Methods ---

    public CompletableFuture<Void> saveOfflineDelivery(UUID playerUUID, double refundAmount, List<ItemStack> items) {
        // --- FIX: Use correct async executor ---
        return CompletableFuture.runAsync(() -> {
            Connection conn = getConnection();
            if (conn == null) { throw new RuntimeException("DB not connected"); }
            String insertDelivery = "INSERT INTO offline_delivery (player_uuid, refund_amount) VALUES (?, ?)";
            String insertItem = "INSERT INTO offline_delivery_items (delivery_id, item_stack) VALUES (?, ?)";

            try (PreparedStatement psDelivery = conn.prepareStatement(insertDelivery, Statement.RETURN_GENERATED_KEYS)) {
                conn.setAutoCommit(false); // Start transaction

                psDelivery.setString(1, playerUUID.toString());
                psDelivery.setDouble(2, refundAmount);
                psDelivery.executeUpdate();

                long deliveryId;
                try (ResultSet generatedKeys = psDelivery.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        deliveryId = generatedKeys.getLong(1);
                    } else {
                        throw new SQLException("Creating delivery failed, no ID obtained.");
                    }
                }

                if (items != null && !items.isEmpty()) {
                    try (PreparedStatement psItem = conn.prepareStatement(insertItem)) {
                        for (ItemStack item : items) {
                            psItem.setLong(1, deliveryId);
                            psItem.setString(2, itemStackToBase64(item));
                            psItem.addBatch();
                        }
                        psItem.executeBatch();
                    }
                }
                conn.commit(); // Commit transaction
            } catch (Exception e) {
                try { conn.rollback(); } catch (SQLException ex) { plugin.getLogger().log(Level.SEVERE, "Failed to rollback delivery transaction", ex); }
                throw new RuntimeException("Failed to save offline delivery", e);
            } finally {
                try { conn.setAutoCommit(true); } catch (SQLException e) { /* ignored */ }
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable)); // --- FIX: Use correct async executor ---
    }

    public CompletableFuture<OfflineDelivery> loadAndRemoveOfflineDelivery(UUID playerUUID) {
        // --- FIX: Use correct async executor ---
        return CompletableFuture.supplyAsync(() -> {
            Connection conn = getConnection();
            if (conn == null) { throw new RuntimeException("DB not connected"); }

            String selectDelivery = "SELECT * FROM offline_delivery WHERE player_uuid = ? LIMIT 1";
            String selectItems = "SELECT item_stack FROM offline_delivery_items WHERE delivery_id = ?";
            String deleteDelivery = "DELETE FROM offline_delivery WHERE delivery_id = ?"; // Cascade delete handles items

            try (PreparedStatement psSelectDelivery = conn.prepareStatement(selectDelivery)) {
                psSelectDelivery.setString(1, playerUUID.toString());

                long deliveryId;
                double refundAmount;
                List<ItemStack> items = new ArrayList<>();

                try (ResultSet rsDelivery = psSelectDelivery.executeQuery()) {
                    if (!rsDelivery.next()) {
                        return null; // No delivery found
                    }
                    deliveryId = rsDelivery.getLong("delivery_id");
                    refundAmount = rsDelivery.getDouble("refund_amount");
                }

                try (PreparedStatement psSelectItems = conn.prepareStatement(selectItems)) {
                    psSelectItems.setLong(1, deliveryId);
                    try (ResultSet rsItems = psSelectItems.executeQuery()) {
                        while (rsItems.next()) {
                            items.add(itemStackFromBase64(rsItems.getString("item_stack")));
                        }
                    }
                }

                // Now delete the delivery
                try (PreparedStatement psDelete = conn.prepareStatement(deleteDelivery)) {
                    psDelete.setLong(1, deliveryId);
                    psDelete.executeUpdate();
                }

                return new OfflineDelivery(refundAmount, items);

            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "Error loading/removing offline delivery", e);
                throw new RuntimeException("Failed to load offline delivery", e);
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable)); // --- FIX: Use correct async executor ---
    }


    // --- Serialization ---
    private String itemStackToBase64(ItemStack item) throws IOException { if (item == null) return null; try (ByteArrayOutputStream os = new ByteArrayOutputStream(); BukkitObjectOutputStream dos = new BukkitObjectOutputStream(os)) { dos.writeObject(item); return Base64Coder.encodeLines(os.toByteArray()); } }
    private ItemStack itemStackFromBase64(String data) throws IOException, ClassNotFoundException { if (data == null || data.isEmpty()) return null; try (ByteArrayInputStream is = new ByteArrayInputStream(Base64Coder.decodeLines(data)); BukkitObjectInputStream dis = new BukkitObjectInputStream(is)) { return (ItemStack) dis.readObject(); } }
}