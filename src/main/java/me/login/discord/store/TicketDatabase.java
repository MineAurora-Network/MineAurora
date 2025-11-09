package me.login.discord.store;

import me.login.Login;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;

public class TicketDatabase {

    private final Login plugin;
    private Connection connection;
    private final String url;

    public TicketDatabase(Login plugin) {
        this.plugin = plugin;
        // Updated path to be inside /database/ folder per your request
        File dbDir = new File(plugin.getDataFolder(), "database");
        if (!dbDir.exists()) {
            dbDir.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbDir.getAbsolutePath() + File.separator + "store.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to Store SQLite DB (store.db)");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Store SQLite DB!");
            e.printStackTrace();
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to get Store DB connection: " + e.getMessage());
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from Store DB");
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to disconnect from Store DB: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        if (getConnection() == null) {
            plugin.getLogger().severe("Cannot create tables, Store DB connection is null.");
            return;
        }
        try (Statement stmt = getConnection().createStatement()) {
            // New table for tracking purchase verifications
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS purchases (
                    message_id BIGINT PRIMARY KEY NOT NULL,
                    user_id BIGINT NOT NULL,
                    purchase_item TEXT NOT NULL,
                    status INTEGER DEFAULT 0 
                )""");
            // status: 0=pending, 1=confirmed, 2=denied, 3=hold
        }
    }

    // --- New Methods for Purchase Tracking ---

    /**
     * Adds a new pending purchase to the database.
     * @param messageId The Discord message ID of the verification embed.
     * @param userId The Discord user ID of the purchaser.
     * @param purchaseItem The name of the item(s) purchased.
     */
    public void addPurchase(long messageId, long userId, String purchaseItem) {
        if (getConnection() == null) return;
        String sql = "INSERT INTO purchases (message_id, user_id, purchase_item, status) VALUES (?, ?, ?, 0)";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, messageId);
                ps.setLong(2, userId);
                ps.setString(3, purchaseItem);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to add purchase: " + e.getMessage(), e);
            }
        });
    }

    /**
     * Retrieves purchase data from the database.
     * This MUST be run synchronously or handled with a callback, as it returns data.
     * @param messageId The message ID to look up.
     * @return PurchaseData object or null if not found.
     */
    public PurchaseData getPurchase(long messageId) {
        if (getConnection() == null) return null;
        String sql = "SELECT * FROM purchases WHERE message_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return new PurchaseData(
                        messageId,
                        rs.getLong("user_id"),
                        rs.getString("purchase_item"),
                        rs.getInt("status")
                );
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to get purchase: " + e.getMessage(), e);
        }
        return null;
    }

    /**
     * Updates the status of a purchase (e.g., to confirmed or denied).
     * @param messageId The message ID of the purchase to update.
     * @param status The new status (1=confirmed, 2=denied, 3=hold).
     */
    public void updatePurchaseStatus(long messageId, int status) {
        if (getConnection() == null) return;
        String sql = "UPDATE purchases SET status = ? WHERE message_id = ?";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setInt(1, status);
                ps.setLong(2, messageId);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to update purchase status: " + e.getMessage(), e);
            }
        });
    }

    // Helper class to store purchase data
    public static class PurchaseData {
        public final long messageId;
        public final long userId;
        public final String purchaseItem;
        public final int status;

        public PurchaseData(long messageId, long userId, String purchaseItem, int status) {
            this.messageId = messageId;
            this.userId = userId;
            this.purchaseItem = purchaseItem;
            this.status = status;
        }
    }
}