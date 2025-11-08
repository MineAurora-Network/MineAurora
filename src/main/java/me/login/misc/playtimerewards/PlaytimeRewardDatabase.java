package me.login.misc.playtimerewards;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;

public class PlaytimeRewardDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    /**
     * Internal record to hold player data.
     */
    public record PlayerPlaytimeData(long totalPlaytimeSeconds, int lastClaimedLevel, int notifiedLevel) {}

    public PlaytimeRewardDatabase(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "playtimerewards.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting PlaytimeReward DB...");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect PlaytimeReward DB!", e);
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting PlaytimeReward DB connection", e);
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("PlaytimeReward DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting PlaytimeReward DB", e);
        }
    }

    public void createTables() {
        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS player_playtime (
                    player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                    total_playtime_seconds BIGINT NOT NULL DEFAULT 0,
                    last_claimed_level INT NOT NULL DEFAULT 0,
                    notified_level INT NOT NULL DEFAULT 0
                )""");
            plugin.getLogger().info("Playtime Rewards table created or verified.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create PlaytimeReward tables!", e);
        }
    }

    /**
     * Gets all playtime data for a player.
     * @param uuid The player's UUID.
     * @return CompletableFuture with their data, or defaults (0, 0, 0) if not found.
     */
    public CompletableFuture<PlayerPlaytimeData> getPlayerPlaytimeData(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT total_playtime_seconds, last_claimed_level, notified_level FROM player_playtime WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return new PlayerPlaytimeData(
                                rs.getLong("total_playtime_seconds"),
                                rs.getInt("last_claimed_level"),
                                rs.getInt("notified_level")
                        );
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get playtime data for " + uuid, e);
            }
            return new PlayerPlaytimeData(0, 0, 0); // Default data
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /**
     * Saves all playtime data for a player.
     * @param uuid The player's UUID.
     * @param totalPlaytimeSeconds The total accumulated playtime.
     * @param lastClaimedLevel The highest level they have claimed.
     * @param notifiedLevel The highest level they have been notified for.
     */
    public void savePlayerPlaytimeData(UUID uuid, long totalPlaytimeSeconds, int lastClaimedLevel, int notifiedLevel) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_playtime (player_uuid, total_playtime_seconds, last_claimed_level, notified_level) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET " +
                    "total_playtime_seconds = ?, " +
                    "last_claimed_level = ?, " +
                    "notified_level = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, totalPlaytimeSeconds);
                ps.setInt(3, lastClaimedLevel);
                ps.setInt(4, notifiedLevel);
                // On conflict
                ps.setLong(5, totalPlaytimeSeconds);
                ps.setInt(6, lastClaimedLevel);
                ps.setInt(7, notifiedLevel);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save playtime data for " + uuid, e);
            }
        });
    }
}