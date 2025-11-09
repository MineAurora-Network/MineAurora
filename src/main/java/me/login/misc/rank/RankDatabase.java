package me.login.misc.rank;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class RankDatabase {

    private final Login plugin;
    private Connection connection;

    public RankDatabase(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Connects to the SQLite database.
     * @return true if connection was successful.
     */
    public boolean connect() {
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        File dbFile = new File(dbFolder, "ranks.db");

        try {
            Class.forName("org.sqlite.JDBC");
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting Rank DB...");
            createTables();
            plugin.getLogger().info("Rank DB Connected.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to Rank DB!", e);
            return false;
        }
    }

    /**
     * Disconnects from the database.
     */
    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Rank DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting Rank DB", e);
        }
    }

    private void createTables() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            // Stores active temporary ranks and permanent ranks set by this system
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS temp_ranks (
                    player_uuid TEXT PRIMARY KEY NOT NULL,
                    player_name TEXT NOT NULL,
                    rank_name TEXT NOT NULL,
                    setter_uuid TEXT NOT NULL,
                    setter_name TEXT NOT NULL,
                    previous_rank TEXT NOT NULL,
                    expiry_time BIGINT NOT NULL
                )""");

            // Stores a log of all rank changes made by this system
            stmt.executeUpdate("""
                CREATE TABLE IF NOT EXISTS rank_history (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    rank_name TEXT NOT NULL,
                    duration_ms BIGINT NOT NULL,
                    setter_uuid TEXT NOT NULL,
                    setter_name TEXT NOT NULL,
                    timestamp BIGINT NOT NULL
                )""");
        }
    }

    /**
     * Saves or updates a player's rank data.
     */
    public void saveRankData(RankData data) {
        String sql = """
            INSERT OR REPLACE INTO temp_ranks 
            (player_uuid, player_name, rank_name, setter_uuid, setter_name, previous_rank, expiry_time) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, data.playerUuid().toString());
                ps.setString(2, data.playerName());
                ps.setString(3, data.rankName());
                ps.setString(4, data.setterUuid().toString());
                ps.setString(5, data.setterName());
                ps.setString(6, data.previousRank());
                ps.setLong(7, data.expiryTime());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save rank data for " + data.playerName(), e);
            }
        });
    }

    /**
     * Removes a player's rank data.
     */
    public void removeRankData(UUID playerUuid) {
        String sql = "DELETE FROM temp_ranks WHERE player_uuid = ?";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, playerUuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to remove rank data for " + playerUuid, e);
            }
        });
    }

    /**
     * Gets a player's current rank data, if managed by this system.
     */
    public RankData getRankData(UUID playerUuid) {
        String sql = "SELECT * FROM temp_ranks WHERE player_uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, playerUuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new RankData(
                            UUID.fromString(rs.getString("player_uuid")),
                            rs.getString("player_name"),
                            rs.getString("rank_name"),
                            UUID.fromString(rs.getString("setter_uuid")),
                            rs.getString("setter_name"),
                            rs.getString("previous_rank"),
                            rs.getLong("expiry_time")
                    );
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get rank data for " + playerUuid, e);
        }
        return null;
    }

    /**
     * Gets all active temporary ranks (expiry_time != -1).
     * This is run on server startup.
     */
    public List<RankData> getActiveTempRanks() {
        List<RankData> ranks = new ArrayList<>();
        // Get all ranks that are not permanent
        String sql = "SELECT * FROM temp_ranks WHERE expiry_time != -1";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                ranks.add(new RankData(
                        UUID.fromString(rs.getString("player_uuid")),
                        rs.getString("player_name"),
                        rs.getString("rank_name"),
                        UUID.fromString(rs.getString("setter_uuid")),
                        rs.getString("setter_name"),
                        rs.getString("previous_rank"),
                        rs.getLong("expiry_time")
                ));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load active temp ranks", e);
        }
        return ranks;
    }

    /**
     * Logs a rank change to the history table.
     */
    public void addRankHistory(RankData data, long durationMs) {
        String sql = """
            INSERT INTO rank_history 
            (player_uuid, player_name, rank_name, duration_ms, setter_uuid, setter_name, timestamp) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, data.playerUuid().toString());
                ps.setString(2, data.playerName());
                ps.setString(3, data.rankName());
                ps.setLong(4, durationMs);
                ps.setString(5, data.setterUuid().toString());
                ps.setString(6, data.setterName());
                ps.setLong(7, System.currentTimeMillis());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to log rank history for " + data.playerName(), e);
            }
        });
    }
}