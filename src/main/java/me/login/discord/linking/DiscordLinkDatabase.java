package me.login.discord.linking;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DiscordLinkDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    public DiscordLinkDatabase(Login plugin) {
        this.plugin = plugin;
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists()) dataFolder.mkdirs();
        this.url = "jdbc:sqlite:" + dataFolder.getAbsolutePath() + File.separator + "data.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate("CREATE TABLE IF NOT EXISTS linked_accounts (discord_id BIGINT PRIMARY KEY, uuid VARCHAR(36) NOT NULL UNIQUE)");
            }
            plugin.getLogger().info("Connected to Link SQLite DB (data.db)");
        } catch (Exception e) {
            plugin.getLogger().severe("Failed connect Link SQLite DB!"); e.printStackTrace(); this.connection = null;
        }
    }

    public Connection getConnection() {
        try { if (connection == null || connection.isClosed()) connect(); } catch (SQLException e) { plugin.getLogger().severe("Link DB Conn Error:"); e.printStackTrace(); }
        return connection;
    }

    public void disconnect() {
        try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) { plugin.getLogger().severe("Link DB Disconn Error:"); e.printStackTrace(); }
    }

    public void linkUser(long discordId, UUID uuid) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) { plugin.getLogger().warning("Link DB Conn Null - Cannot link"); return; }
            try (PreparedStatement ps = getConnection().prepareStatement("INSERT OR REPLACE INTO linked_accounts (discord_id, uuid) VALUES (?, ?)")) {
                ps.setLong(1, discordId); ps.setString(2, uuid.toString()); ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().warning("Failed link user DB:"); e.printStackTrace(); }
        });
    }

    public void unlinkUser(long discordId) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (getConnection() == null) { plugin.getLogger().warning("Link DB Conn Null - Cannot unlink"); return; }
            try (PreparedStatement ps = getConnection().prepareStatement("DELETE FROM linked_accounts WHERE discord_id = ?")) {
                ps.setLong(1, discordId); ps.executeUpdate();
            } catch (SQLException e) { plugin.getLogger().warning("Failed unlink user DB:"); e.printStackTrace(); }
        });
    }

    public Map<Long, UUID> loadAllLinks() {
        Map<Long, UUID> map = new HashMap<>(); if (getConnection() == null) { plugin.getLogger().warning("Link DB Conn Null - Cannot load links"); return map; }
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT discord_id, uuid FROM linked_accounts"); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) map.put(rs.getLong("discord_id"), UUID.fromString(rs.getString("uuid")));
        } catch (SQLException e) { plugin.getLogger().warning("Failed load links DB:"); e.printStackTrace(); }
        return map;
    }

    public boolean isLinked(UUID uuid) {
        if (getConnection() == null) { plugin.getLogger().warning("Link DB Conn Null - Cannot check link status"); return false; }
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT 1 FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString()); try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        } catch (SQLException e) { plugin.getLogger().warning("Failed check link DB:"); e.printStackTrace(); }
        return false;
    }

    public Long getLinkedDiscordId(UUID uuid) {
        if (getConnection() == null) {
            plugin.getLogger().warning("Link DB Conn Null - Cannot check link status");
            return null;
        }
        try (PreparedStatement ps = getConnection().prepareStatement("SELECT discord_id FROM linked_accounts WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("discord_id");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed check link DB:");
            e.printStackTrace();
        }
        return null;
    }
}