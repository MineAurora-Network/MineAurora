package me.login.misc.dailyreward;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class DailyRewardDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    // Record to hold claim data
    public record ClaimData(long lastClaimTime, int streak) {}

    public DailyRewardDatabase(Login plugin) {
        this.plugin = plugin;
        // Requirement 5: Ensure DB is in plugins/Login/database
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
            createTables(); // Ensure tables exist on connect
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
        String dailyRewardsTable = """
            CREATE TABLE IF NOT EXISTS daily_reward_claims (
                player_uuid VARCHAR(36) NOT NULL,
                rank_key VARCHAR(32) NOT NULL,
                last_claim_time BIGINT NOT NULL,
                current_streak INTEGER NOT NULL DEFAULT 0,
                PRIMARY KEY (player_uuid, rank_key)
            );""";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(dailyRewardsTable);

            // Attempt to add column if it doesn't exist (for migration)
            try {
                stmt.executeUpdate("ALTER TABLE daily_reward_claims ADD COLUMN current_streak INTEGER NOT NULL DEFAULT 0;");
                plugin.getLogger().info("Added current_streak column to daily_reward_claims.");
            } catch (SQLException ignored) {
                // Column likely already exists
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create DailyReward tables!", e);
        }
    }

    public CompletableFuture<ClaimData> getClaimData(UUID uuid, String rankKey) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_claim_time, current_streak FROM daily_reward_claims WHERE player_uuid = ? AND rank_key = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankKey);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return new ClaimData(rs.getLong("last_claim_time"), rs.getInt("current_streak"));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get claim data for " + uuid, e);
            }
            return new ClaimData(0L, 0);
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    // Kept for compatibility if needed, but getClaimData is preferred
    public CompletableFuture<Long> getLastClaimTime(UUID uuid, String rankKey) {
        return getClaimData(uuid, rankKey).thenApply(ClaimData::lastClaimTime);
    }

    public void saveClaim(UUID uuid, String rankKey, long time, int streak) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO daily_reward_claims (player_uuid, rank_key, last_claim_time, current_streak) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(player_uuid, rank_key) DO UPDATE SET last_claim_time = ?, current_streak = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankKey);
                ps.setLong(3, time);
                ps.setInt(4, streak);
                // Update part
                ps.setLong(5, time);
                ps.setInt(6, streak);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not save claim for " + uuid, e);
            }
        });
    }

    // For backward compatibility with older code calling this
    public void setLastClaimTime(UUID uuid, long time, String rankKey) {
        // This method assumes we just want to update time, but we need to know the streak.
        // ideally this shouldn't be used anymore, but for safety:
        getClaimData(uuid, rankKey).thenAccept(data -> saveClaim(uuid, rankKey, time, data.streak()));
    }
}