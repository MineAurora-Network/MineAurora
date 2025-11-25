package me.login.misc.dailyreward;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DailyRewardDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    public DailyRewardDatabase(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "dailyrewards.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting DailyReward DB...");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect DailyReward DB!", e);
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting DailyReward DB connection", e);
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("DailyReward DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting DailyReward DB", e);
        }
    }

    public void createTables() {
        // Only managing daily_reward_claims now
        String dailyRewardsTable = """
            CREATE TABLE IF NOT EXISTS daily_reward_claims (
                player_uuid VARCHAR(36) NOT NULL,
                rank_key VARCHAR(32) NOT NULL,
                last_claim_time BIGINT NOT NULL,
                PRIMARY KEY (player_uuid, rank_key)
            );""";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(dailyRewardsTable);
            plugin.getLogger().info("Daily Reward table created or verified.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create DailyReward tables!", e);
        }
    }

    // --- Daily Reward Methods ---

    public CompletableFuture<Long> getLastClaimTime(UUID uuid, String rankKey) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_claim_time FROM daily_reward_claims WHERE player_uuid = ? AND rank_key = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankKey);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong("last_claim_time");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get last claim time for " + uuid + " and rank " + rankKey, e);
            }
            return 0L;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<List<String>> getClaimedRanksToday(UUID uuid, long startOfDay) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> claimedRanks = new ArrayList<>();
            String sql = "SELECT rank_key FROM daily_reward_claims WHERE player_uuid = ? AND last_claim_time >= ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, startOfDay);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    claimedRanks.add(rs.getString("rank_key"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get claimed ranks for " + uuid, e);
            }
            return claimedRanks;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void setLastClaimTime(UUID uuid, long time, String rankKey) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO daily_reward_claims (player_uuid, rank_key, last_claim_time) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player_uuid, rank_key) DO UPDATE SET last_claim_time = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankKey);
                ps.setLong(3, time);
                ps.setLong(4, time);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not set last claim time for " + uuid + " and rank " + rankKey, e);
            }
        });
    }
}