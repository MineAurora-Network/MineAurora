package me.login.loginsystem;

import me.login.Login;
import org.bukkit.Location;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

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

                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS login_parkour_points (id INTEGER PRIMARY KEY AUTOINCREMENT, world VARCHAR(50), x INT, y INT, z INT, type VARCHAR(20), point_index INT)");
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS login_parkour_cooldowns (uuid VARCHAR(36) PRIMARY KEY, last_reward BIGINT)");
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
        if (getConnection() == null) return false;
        try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public PlayerAuthData getAuthData(UUID uuid) {
        if (getConnection() == null) return null;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM player_auth WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new PlayerAuthData(rs.getString("uuid"), rs.getString("hashed_password"), rs.getString("registration_ip"), rs.getString("last_login_ip"), rs.getLong("last_login_timestamp"));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public List<PlayerAuthData> getPlayersByIp(String ip) {
        List<PlayerAuthData> accounts = new ArrayList<>(); if (getConnection() == null) return accounts;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT * FROM player_auth WHERE registration_ip = ? OR last_login_ip = ?")) {
            ps.setString(1, ip); ps.setString(2, ip); try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) accounts.add(new PlayerAuthData(rs.getString("uuid"), rs.getString("hashed_password"), rs.getString("registration_ip"), rs.getString("last_login_ip"), rs.getLong("last_login_timestamp")));
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return accounts;
    }

    public void addParkourPoint(Location loc, String type, int index) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("INSERT INTO login_parkour_points (world, x, y, z, type, point_index) VALUES (?, ?, ?, ?, ?, ?)")) {
                ps.setString(1, loc.getWorld().getName()); ps.setInt(2, loc.getBlockX()); ps.setInt(3, loc.getBlockY()); ps.setInt(4, loc.getBlockZ()); ps.setString(5, type); ps.setInt(6, index); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void removeParkourPoint(Location loc) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM login_parkour_points WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
                ps.setString(1, loc.getWorld().getName()); ps.setInt(2, loc.getBlockX()); ps.setInt(3, loc.getBlockY()); ps.setInt(4, loc.getBlockZ()); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public ParkourPoint getParkourPoint(Location loc) {
        if (getConnection() == null) return null;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT type, point_index FROM login_parkour_points WHERE world = ? AND x = ? AND y = ? AND z = ?")) {
            ps.setString(1, loc.getWorld().getName()); ps.setInt(2, loc.getBlockX()); ps.setInt(3, loc.getBlockY()); ps.setInt(4, loc.getBlockZ());
            try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return new ParkourPoint(rs.getString("type"), rs.getInt("point_index")); }
        } catch (SQLException e) { e.printStackTrace(); }
        return null;
    }

    public int getParkourPointCount(String type) {
        if (getConnection() == null) return 0;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT COUNT(*) as count FROM login_parkour_points WHERE type = ?")) {
            ps.setString(1, type); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getInt("count"); }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public List<ParkourPointData> getAllParkourPoints() {
        List<ParkourPointData> points = new ArrayList<>();
        if (getConnection() == null) return points;
        try (Statement stmt = getConnection().createStatement(); ResultSet rs = stmt.executeQuery("SELECT * FROM login_parkour_points")) {
            while (rs.next()) points.add(new ParkourPointData(rs.getString("world"), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"), rs.getString("type"), rs.getInt("point_index")));
        } catch (SQLException e) { e.printStackTrace(); }
        return points;
    }

    public long getLastParkourReward(UUID uuid) {
        if (getConnection() == null) return 0;
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT last_reward FROM login_parkour_cooldowns WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) { if (rs.next()) return rs.getLong("last_reward"); }
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public void setLastParkourReward(UUID uuid, long time) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) return;
            try (PreparedStatement ps = getConnection().prepareStatement("REPLACE INTO login_parkour_cooldowns (uuid, last_reward) VALUES (?, ?)")) {
                ps.setString(1, uuid.toString()); ps.setLong(2, time); ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public record PlayerAuthData(String uuid, String hashedPassword, String registrationIp, String lastLoginIp, long lastLoginTimestamp) {}
    public record ParkourPoint(String type, int index) {}
    public record ParkourPointData(String world, int x, int y, int z, String type, int index) {}
}