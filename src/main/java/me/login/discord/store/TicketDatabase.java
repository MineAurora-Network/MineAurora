package me.login.discord.store;

import me.login.Login;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class TicketDatabase {

    private final Login plugin;
    private Connection connection;
    private final String url;

    public TicketDatabase(Login plugin) {
        this.plugin = plugin;
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
            e.printStackTrace();
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected Store SQLite DB.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTables() {
        if (getConnection() == null) return;
        // Stores pending purchase verifications
        // 'status': 0 = pending, 1 = confirmed, 2 = denied, 3 = hold
        String sql = "CREATE TABLE IF NOT EXISTS purchases (" +
                "message_id BIGINT PRIMARY KEY," +
                "user_id BIGINT NOT NULL," +
                "purchase_item TEXT NOT NULL," +
                "status INTEGER DEFAULT 0" +
                ");";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(sql);
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to create purchases table: " + e.getMessage());
        }
    }

    public void logPurchase(long messageId, long userId, String purchaseItem) {
        if (getConnection() == null) return;
        String sql = "INSERT INTO purchases (message_id, user_id, purchase_item) VALUES (?, ?, ?)";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, userId);
            ps.setString(3, purchaseItem);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to log purchase: " + e.getMessage());
        }
    }

    public PurchaseData getPurchase(long messageId) {
        if (getConnection() == null) return null;
        String sql = "SELECT user_id, purchase_item, status FROM purchases WHERE message_id = ?";
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
            plugin.getLogger().warning("Failed to get purchase: " + e.getMessage());
        }
        return null;
    }

    public void updatePurchaseStatus(long messageId, int status) {
        if (getConnection() == null) return;
        String sql = "UPDATE purchases SET status = ? WHERE message_id = ?";
        try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
            ps.setInt(1, status);
            ps.setLong(2, messageId);
            ps.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to update purchase status: " + e.getMessage());
        }
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