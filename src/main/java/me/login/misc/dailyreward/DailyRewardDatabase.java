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
        // --- MODIFIED TABLE SCHEMA ---
        // This new table stores one row per player *per rank*, allowing separate cooldowns.
        String dailyRewardsTable = """
            CREATE TABLE IF NOT EXISTS daily_reward_claims (
                player_uuid VARCHAR(36) NOT NULL,
                rank_key VARCHAR(32) NOT NULL,
                last_claim_time BIGINT NOT NULL,
                PRIMARY KEY (player_uuid, rank_key)
            );""";
        // --- END MODIFICATION ---

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

    /**
     * Gets the last claim time for a specific rank.
     * @param uuid Player's UUID
     * @param rankKey The key for the rank (e.g., "elite", "default")
     * @return CompletableFuture with the timestamp, or 0L if never claimed.
     */
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
            return 0L; // No previous claim
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    /**
     * Gets a list of all rank keys a player has claimed since the startOfDay.
     * @param uuid Player's UUID
     * @param startOfDay The timestamp marking the start of the 24h period
     * @return CompletableFuture with a List of claimed rank keys.
     */
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
            return claimedRanks; // Return list (might be empty)
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void setLastClaimTime(UUID uuid, long time, String rankKey) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // --- MODIFIED SQL ---
            String sql = "INSERT INTO daily_reward_claims (player_uuid, rank_key, last_claim_time) VALUES (?, ?, ?) " +
                    "ON CONFLICT(player_uuid, rank_key) DO UPDATE SET last_claim_time = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, rankKey);
                ps.setLong(3, time);
                // On conflict
                ps.setLong(4, time);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not set last claim time for " + uuid + " and rank " + rankKey, e);
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