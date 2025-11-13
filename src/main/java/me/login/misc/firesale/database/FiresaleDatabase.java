package me.login.misc.firesale.database;

import me.login.Login;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class FiresaleDatabase {

    private final Login plugin;
    private Connection connection;

    public FiresaleDatabase(Login plugin) {
        this.plugin = plugin;
    }

    // Synchronize getting connection to be safe
    private synchronized Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            File dbFile = new File(plugin.getDataFolder(), "database/firesale.db");
            dbFile.getParentFile().mkdirs(); // Ensure directory exists
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
        }
        return connection;
    }

    public void init() {
        // ... (init SQL strings same as before) ...
        String createActiveTable = "CREATE TABLE IF NOT EXISTS active_sales (sale_id INTEGER PRIMARY KEY AUTOINCREMENT, item_stack TEXT NOT NULL, price DOUBLE NOT NULL, initial_quantity INTEGER NOT NULL, remaining_quantity INTEGER NOT NULL, start_time_epoch BIGINT NOT NULL, end_time_epoch BIGINT NOT NULL, creator_uuid TEXT NOT NULL, creator_name TEXT NOT NULL, status TEXT NOT NULL, total_sold INTEGER NOT NULL);";
        String createHistoryTable = "CREATE TABLE IF NOT EXISTS sales_history (sale_id INTEGER PRIMARY KEY, item_stack TEXT NOT NULL, price DOUBLE NOT NULL, initial_quantity INTEGER NOT NULL, remaining_quantity INTEGER NOT NULL, start_time_epoch BIGINT NOT NULL, end_time_epoch BIGINT NOT NULL, creator_uuid TEXT NOT NULL, creator_name TEXT NOT NULL, status TEXT NOT NULL, total_sold INTEGER NOT NULL);";

        try (Connection conn = getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(createActiveTable);
            stmt.execute(createHistoryTable);
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to initialize Firesale database: " + e.getMessage());
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to close Firesale database connection: " + e.getMessage());
        }
    }

    // FIX: Synchronized to prevent async task and main thread colliding
    public synchronized Firesale saveSale(Firesale sale) {
        String sql = "INSERT INTO active_sales (item_stack, price, initial_quantity, remaining_quantity, start_time_epoch, end_time_epoch, creator_uuid, creator_name, status, total_sold) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            // ... (set params)
            pstmt.setString(1, serializeItemStack(sale.getItem()));
            pstmt.setDouble(2, sale.getPrice());
            pstmt.setInt(3, sale.getInitialQuantity());
            pstmt.setInt(4, sale.getRemainingQuantity());
            pstmt.setLong(5, sale.getStartTime().toEpochMilli());
            pstmt.setLong(6, sale.getEndTime().toEpochMilli());
            pstmt.setString(7, sale.getCreatorUuid().toString());
            pstmt.setString(8, sale.getCreatorName());
            pstmt.setString(9, sale.getStatus().toString());
            pstmt.setInt(10, sale.getTotalSold());

            int affectedRows = pstmt.executeUpdate();
            if (affectedRows > 0) {
                try (ResultSet rs = pstmt.getGeneratedKeys()) {
                    if (rs.next()) sale.setSaleId(rs.getInt(1));
                }
            }
            return sale;
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to save sale: " + e.getMessage());
            return null;
        }
    }

    public synchronized void archiveSale(Firesale sale) {
        String deleteSql = "DELETE FROM active_sales WHERE sale_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(deleteSql)) {
            pstmt.setInt(1, sale.getSaleId());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to delete sale from active: " + e.getMessage());
        }

        String insertSql = "INSERT INTO sales_history (sale_id, item_stack, price, initial_quantity, remaining_quantity, start_time_epoch, end_time_epoch, creator_uuid, creator_name, status, total_sold) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(insertSql)) {
            // ... (set params exactly as before)
            pstmt.setInt(1, sale.getSaleId());
            pstmt.setString(2, serializeItemStack(sale.getItem()));
            pstmt.setDouble(3, sale.getPrice());
            pstmt.setInt(4, sale.getInitialQuantity());
            pstmt.setInt(5, sale.getRemainingQuantity());
            pstmt.setLong(6, sale.getStartTime().toEpochMilli());
            pstmt.setLong(7, sale.getEndTime().toEpochMilli());
            pstmt.setString(8, sale.getCreatorUuid().toString());
            pstmt.setString(9, sale.getCreatorName());
            pstmt.setString(10, sale.getStatus().toString());
            pstmt.setInt(11, sale.getTotalSold());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to archive sale: " + e.getMessage());
        }
    }

    public synchronized List<Firesale> loadActiveSales() {
        // ... (same code)
        List<Firesale> sales = new ArrayList<>();
        String sql = "SELECT * FROM active_sales";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) sales.add(deserializeFiresale(rs));
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed load active: " + e.getMessage());
        }
        return sales;
    }

    public synchronized List<Firesale> loadSalesHistory(int page, int itemsPerPage) {
        // ... (same code)
        List<Firesale> sales = new ArrayList<>();
        String sql = "SELECT * FROM sales_history ORDER BY start_time_epoch DESC LIMIT ? OFFSET ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, itemsPerPage);
            pstmt.setInt(2, page * itemsPerPage);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) sales.add(deserializeFiresale(rs));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed load history: " + e.getMessage());
        }
        return sales;
    }

    public synchronized int getHistoryPageCount(int itemsPerPage) {
        // ... (same code)
        String sql = "SELECT COUNT(*) FROM sales_history";
        try (Connection conn = getConnection(); Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                int total = rs.getInt(1);
                return (int) Math.ceil((double) total / itemsPerPage);
            }
        } catch (SQLException e) { }
        return 0;
    }

    public synchronized void updateSaleQuantity(int saleId, int newQuantity, int newTotalSold) {
        String sql = "UPDATE active_sales SET remaining_quantity = ?, total_sold = ? WHERE sale_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, newQuantity);
            pstmt.setInt(2, newTotalSold);
            pstmt.setInt(3, saleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update quantity: " + e.getMessage());
        }
    }

    public synchronized void updateSaleStatus(int saleId, SaleStatus status) {
        String sql = "UPDATE active_sales SET status = ? WHERE sale_id = ?";
        try (Connection conn = getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, status.toString());
            pstmt.setInt(2, saleId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Failed to update status: " + e.getMessage());
        }
    }

    // Helpers serializeItemStack and deserializeItemStack remain unchanged ...
    private String serializeItemStack(ItemStack item) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {
            dataOutput.writeObject(item);
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) { return ""; }
    }

    private ItemStack deserializeItemStack(String data) {
        if (data == null || data.isEmpty()) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {
            return (ItemStack) dataInput.readObject();
        } catch (Exception e) { return null; }
    }

    private Firesale deserializeFiresale(ResultSet rs) throws SQLException {
        return new Firesale(
                rs.getInt("sale_id"),
                deserializeItemStack(rs.getString("item_stack")),
                rs.getDouble("price"),
                rs.getInt("initial_quantity"),
                rs.getInt("remaining_quantity"),
                Instant.ofEpochMilli(rs.getLong("start_time_epoch")),
                Instant.ofEpochMilli(rs.getLong("end_time_epoch")),
                java.util.UUID.fromString(rs.getString("creator_uuid")),
                rs.getString("creator_name"),
                SaleStatus.valueOf(rs.getString("status")),
                rs.getInt("total_sold")
        );
    }
}