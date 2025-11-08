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
        String dailyRewardsTable = """
            CREATE TABLE IF NOT EXISTS daily_rewards (
                player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                last_claim_time BIGINT NOT NULL,
                claimed_rank_key VARCHAR(32)
            );""";

        String tokensTable = """
            CREATE TABLE IF NOT EXISTS player_tokens (
                player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                token_balance BIGINT NOT NULL DEFAULT 0
            );""";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(dailyRewardsTable);
            stmt.executeUpdate(tokensTable);
            plugin.getLogger().info("Daily Reward & Token tables created or verified.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create DailyReward tables!", e);
        }
    }

    // --- Daily Reward Methods ---

    public CompletableFuture<Long> getLastClaimTime(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT last_claim_time FROM daily_rewards WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong("last_claim_time");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get last claim time for " + uuid, e);
            }
            return 0L; // No previous claim
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public CompletableFuture<String> getClaimedRankToday(UUID uuid, long startOfDay) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT claimed_rank_key FROM daily_rewards WHERE player_uuid = ? AND last_claim_time >= ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, startOfDay);
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getString("claimed_rank_key");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get claimed rank for " + uuid, e);
            }
            return null; // Not claimed today
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void setLastClaimTime(UUID uuid, long time, String rankKey) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO daily_rewards (player_uuid, last_claim_time, claimed_rank_key) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET last_claim_time = ?, claimed_rank_key = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, time);
                ps.setString(3, rankKey);
                ps.setLong(4, time);
                ps.setString(5, rankKey);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not set last claim time for " + uuid, e);
            }
        });
    }

    // --- Token Methods ---

    public CompletableFuture<Long> getTokenBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT token_balance FROM player_tokens WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong("token_balance");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get token balance for " + uuid, e);
            }
            return 0L; // Default balance
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void addTokens(UUID uuid, int amount) {
        if (amount <= 0) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_tokens (player_uuid, token_balance) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET token_balance = token_balance + ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not add tokens for " + uuid, e);
            }
        });
    }

    // --- NEW METHODS FOR TOKEN MODULE ---

    /**
     * Removes tokens from a player's balance.
     * @param uuid The player's UUID.
     * @param amount The positive amount to remove.
     * @return CompletableFuture that completes with true if removal was successful, false if not (e.g., insufficient funds).
     */
    public CompletableFuture<Boolean> removeTokens(UUID uuid, long amount) {
        if (amount <= 0) {
            return CompletableFuture.completedFuture(false);
        }

        return CompletableFuture.supplyAsync(() -> {
            // First, check balance
            long currentBalance = getTokenBalance(uuid).join(); // .join() is safe inside supplyAsync
            if (currentBalance < amount) {
                return false; // Insufficient funds
            }

            // If sufficient, remove
            String sql = "UPDATE player_tokens SET token_balance = token_balance - ? WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setLong(1, amount);
                ps.setString(2, uuid.toString());
                ps.executeUpdate();
                return true; // Success
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not remove tokens for " + uuid, e);
                return false; // SQL error
            }
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /**
     * Sets a player's token balance to a specific amount.
     * @param uuid The player's UUID.
     * @param amount The new total balance (must be >= 0).
     */
    public void setTokens(UUID uuid, long amount) {
        if (amount < 0) amount = 0; // Cannot set negative balance

        long finalAmount = amount; // Final variable for async task
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_tokens (player_uuid, token_balance) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET token_balance = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, finalAmount);
                ps.setLong(3, finalAmount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not set tokens for " + uuid, e);
            }
        });
    }
}