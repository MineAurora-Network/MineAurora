package me.login.loginsystem; // Correct package

import me.login.Login; // Import base plugin class

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// Database for Login System (login_data.db)
public class LoginDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    public LoginDatabase(Login plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.url = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + File.separator + "database/login.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS player_auth (uuid VARCHAR(36) PRIMARY KEY, hashed_password VARCHAR(255) NOT NULL, registration_ip VARCHAR(45), last_login_ip VARCHAR(45), last_login_timestamp BIGINT)");
            }
            plugin.getLogger().info("Connected to Login SQLite DB (login_data.db)");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed connect Login SQLite DB!"); e.printStackTrace(); this.connection = null;
        }
    }

    public Connection getConnection() {
        try { if (connection == null || connection.isClosed()) connect(); } catch (SQLException e) { e.printStackTrace(); }
        return connection;
    }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) { connection.close(); plugin.getLogger().info("Disconnected Login SQLite DB."); } } catch (SQLException e) { e.printStackTrace(); }
    }

    public boolean isRegistered(UUID uuid) {
        if (getConnection() == null) return false;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT 1 FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public void registerPlayer(UUID uuid, String hashedPassword, String ip) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("INSERT INTO player_auth (uuid, hashed_password, registration_ip) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setString(2, hashedPassword); ps.setString(3, ip); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public String getPasswordHash(UUID uuid) {
        if (getConnection() == null) return null;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT hashed_password FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getString("hashed_password"); }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public void updatePassword(UUID uuid, String newHashedPassword) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("UPDATE player_auth SET hashed_password = ? WHERE uuid = ?")) {
                ps.setString(1, newHashedPassword); ps.setString(2, uuid.toString()); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void updateLoginInfo(UUID uuid, String ip, long timestamp) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("UPDATE player_auth SET last_login_ip = ?, last_login_timestamp = ? WHERE uuid = ?")) {
                ps.setString(1, ip); ps.setLong(2, timestamp); ps.setString(3, uuid.toString()); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public boolean unregisterPlayer(UUID uuid) {
        // Runs sync as called from async task
        if (getConnection() == null) return false;
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public PlayerAuthData getAuthData(UUID uuid) {
        // Runs sync as called from async task
        if (getConnection() == null) return null;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new PlayerAuthData(rs.getString("uuid"), rs.getString("hashed_password"), rs.getString("registration_ip"), rs.getString("last_login_ip"), rs.getLong("last_login_timestamp"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<PlayerAuthData> getPlayersByIp(String ip) {
        // Runs sync as called from async task
        List<PlayerAuthData> accounts = new ArrayList<>(); if (getConnection() == null) return accounts;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM player_auth WHERE registration_ip = ? OR last_login_ip = ?")) {
            ps.setString(1, ip); ps.setString(2, ip); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) accounts.add(new PlayerAuthData(rs.getString("uuid"), rs.getString("hashed_password"), rs.getString("registration_ip"), rs.getString("last_login_ip"), rs.getLong("last_login_timestamp")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return accounts;
    }

    // Public record for data structure
    public record PlayerAuthData(String uuid, String hashedPassword, String registrationIp, String lastLoginIp, long lastLoginTimestamp) {}
}