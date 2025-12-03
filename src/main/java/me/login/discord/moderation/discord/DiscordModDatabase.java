package me.login.discord.moderation.discord;

import me.login.Login;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DiscordModDatabase {

    private final Login plugin;
    private Connection connection;
    private final String url;

    public DiscordModDatabase(Login plugin) {
        this(plugin, "discord_moderation.db");
    }

    public DiscordModDatabase(Login plugin, String filename) {
        this.plugin = plugin;
        File dbDir = new File(plugin.getDataFolder(), "database");
        if (!dbDir.exists()) dbDir.mkdirs();
        this.url = "jdbc:sqlite:" + dbDir.getAbsolutePath() + File.separator + filename;
        connect();
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTables();
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to connect to Discord Mod DB: " + e.getMessage());
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS discord_warnings (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id BIGINT NOT NULL," +
                    "staff_id BIGINT NOT NULL," +
                    "staff_name TEXT," +
                    "reason TEXT," +
                    "timestamp BIGINT," +
                    "active BOOLEAN DEFAULT 1)");

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS discord_punishments (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "user_id BIGINT NOT NULL," +
                    "staff_id BIGINT NOT NULL," +
                    "staff_name TEXT," +
                    "type TEXT," +
                    "reason TEXT," +
                    "duration TEXT," +
                    "timestamp BIGINT)");
        }
    }

    // ... [Rest of the methods: addWarning, getActiveWarnings, etc. - Identical to previous] ...

    public void addWarning(long userId, long staffId, String staffName, String reason) {
        String sql = "INSERT INTO discord_warnings (user_id, staff_id, staff_name, reason, timestamp, active) VALUES (?, ?, ?, ?, ?, 1)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, staffId);
            ps.setString(3, staffName);
            ps.setString(4, reason);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<String> getActiveWarnings(long userId) {
        List<String> warnings = new ArrayList<>();
        String sql = "SELECT * FROM discord_warnings WHERE user_id = ? AND active = 1 ORDER BY id ASC";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String w = String.format("[ID: %d] %s - by %s", rs.getInt("id"), rs.getString("reason"), rs.getString("staff_name"));
                warnings.add(w);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return warnings;
    }

    public int getActiveWarningCount(long userId) {
        String sql = "SELECT COUNT(*) FROM discord_warnings WHERE user_id = ? AND active = 1";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) { e.printStackTrace(); }
        return 0;
    }

    public boolean removeWarning(long userId, int warningId) {
        String sql = "UPDATE discord_warnings SET active = 0 WHERE id = ? AND user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, warningId);
            ps.setLong(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) { e.printStackTrace(); return false; }
    }

    public void clearWarnings(long userId) {
        String sql = "UPDATE discord_warnings SET active = 0 WHERE user_id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public void logPunishment(long userId, long staffId, String staffName, String type, String reason, String duration) {
        String sql = "INSERT INTO discord_punishments (user_id, staff_id, staff_name, type, reason, duration, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setLong(2, staffId);
            ps.setString(3, staffName);
            ps.setString(4, type);
            ps.setString(5, reason);
            ps.setString(6, duration);
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) { e.printStackTrace(); }
    }

    public List<String> getPunishmentHistory(long userId) {
        List<String> history = new ArrayList<>();
        String sql = "SELECT * FROM discord_punishments WHERE user_id = ? ORDER BY timestamp DESC LIMIT 10";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                String dur = rs.getString("duration");
                String p = String.format("**%s**: %s %s- by %s", rs.getString("type"), rs.getString("reason"), (dur != null && !dur.isEmpty() ? "(" + dur + ") " : ""), rs.getString("staff_name"));
                history.add(p);
            }
        } catch (SQLException e) { e.printStackTrace(); }
        return history;
    }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) {}
    }
}