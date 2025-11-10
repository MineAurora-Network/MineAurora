package me.login.coinflip;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player; // <-- IMPORT ADDED

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Manages all persistent data for the Coinflip system using an SQLite database.
 * This class handles player stats, cooldowns, and all active/pending games.
 */
public class CoinflipDatabase {

    private final Login plugin;
    private Connection connection;
    private final String dbUrl;

    public CoinflipDatabase(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.dbUrl = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "coinflip.db"; // Changed file name
    }

    public boolean connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection(dbUrl);
            plugin.getLogger().info("Coinflip SQLite database connected.");
            createTables();
            return true;
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to Coinflip SQLite database: " + e.getMessage());
            return false;
        } catch (ClassNotFoundException e) {
            plugin.getLogger().severe("SQLite JDBC driver not found!");
            return false;
        }
    }

    // FIX: Made public so module can access it
    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting Coinflip DB connection", e);
        }
        return connection;
    }

    private void createTables() {
        String gamesTable = """
            CREATE TABLE IF NOT EXISTS coinflips (
                game_id INTEGER PRIMARY KEY AUTOINCREMENT,
                creator_uuid VARCHAR(36) NOT NULL,
                creator_name VARCHAR(16) NOT NULL,
                chosen_side VARCHAR(5) NOT NULL,
                amount REAL NOT NULL,
                creation_time BIGINT NOT NULL,
                status VARCHAR(10) NOT NULL DEFAULT 'PENDING'
            )""";

        String statsTable = """
            CREATE TABLE IF NOT EXISTS coinflip_stats (
                player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                player_name VARCHAR(16) NOT NULL,
                wins INTEGER NOT NULL DEFAULT 0,
                losses INTEGER NOT NULL DEFAULT 0,
                cooldown BIGINT NOT NULL DEFAULT 0,
                message_toggle BOOLEAN NOT NULL DEFAULT 1
            )"""; // [Req 3] Added message_toggle column

        String statsIndex = "CREATE INDEX IF NOT EXISTS idx_coinflips_status ON coinflips (status)";
        String creatorIndex = "CREATE INDEX IF NOT EXISTS idx_coinflips_creator ON coinflips (creator_uuid, status)";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(gamesTable);
            stmt.execute(statsTable);
            stmt.execute(statsIndex);
            stmt.execute(creatorIndex);
        } catch (SQLException e) {
            plugin.getLogger().severe("Error creating Coinflip tables: " + e.getMessage());
        }
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                plugin.getLogger().info("Coinflip SQLite database disconnected.");
            } catch (SQLException e) {
                plugin.getLogger().severe("Error disconnecting from Coinflip SQLite database: " + e.getMessage());
            }
        }
    }

    // --- Game Management Methods (FIXED) ---

    public CompletableFuture<Long> createCoinflip(UUID creatorUUID, String creatorName, CoinflipGame.CoinSide side, double amount) {
        CompletableFuture<Long> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String query = "INSERT INTO coinflips (creator_uuid, creator_name, chosen_side, amount, creation_time, status) VALUES (?, ?, ?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(query, Statement.RETURN_GENERATED_KEYS)) {
                ps.setString(1, creatorUUID.toString());
                ps.setString(2, creatorName);
                ps.setString(3, side.name());
                ps.setDouble(4, amount);
                ps.setLong(5, System.currentTimeMillis());
                ps.setString(6, "PENDING");
                int affectedRows = ps.executeUpdate();
                if (affectedRows == 0) { throw new SQLException("Creating coinflip failed, no rows affected."); }
                try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
                    if (generatedKeys.next()) {
                        future.complete(generatedKeys.getLong(1));
                    } else {
                        throw new SQLException("Creating coinflip failed, no ID obtained.");
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<CoinflipGame>> loadAllGames() {
        CompletableFuture<List<CoinflipGame>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CoinflipGame> games = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.complete(games); return; }
            String query = "SELECT * FROM coinflips WHERE status = 'PENDING' OR status = 'ACTIVE' ORDER BY creation_time ASC";
            try (PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        games.add(parseGameFromResult(rs));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Failed parse coinflip game ID " + rs.getLong("game_id") + ": Invalid side - " + e.getMessage());
                    }
                }
                future.complete(games);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<CoinflipGame>> loadPendingCoinflips() {
        CompletableFuture<List<CoinflipGame>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CoinflipGame> games = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.complete(games); return; }
            String query = "SELECT * FROM coinflips WHERE status = 'PENDING' ORDER BY creation_time ASC";
            try (PreparedStatement ps = conn.prepareStatement(query);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    try {
                        games.add(parseGameFromResult(rs));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Failed parse coinflip game ID " + rs.getLong("game_id") + ": Invalid side - " + e.getMessage());
                    }
                }
                future.complete(games);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<List<CoinflipGame>> loadPlayerPendingCoinflips(UUID playerUUID) {
        CompletableFuture<List<CoinflipGame>> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<CoinflipGame> games = new ArrayList<>();
            Connection conn = getConnection();
            if (conn == null) { future.complete(games); return; }
            String query = "SELECT * FROM coinflips WHERE creator_uuid = ? AND status = 'PENDING' ORDER BY creation_time DESC";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        try {
                            games.add(parseGameFromResult(rs));
                        } catch (IllegalArgumentException e) {
                            plugin.getLogger().warning("Failed parse player coinflip game ID " + rs.getLong("game_id") + ": Invalid side - " + e.getMessage());
                        }
                    }
                }
                future.complete(games);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> activateCoinflip(long gameId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(false); return; }
            String query = "UPDATE coinflips SET status = 'ACTIVE' WHERE game_id = ? AND status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, gameId);
                int affectedRows = ps.executeUpdate();
                future.complete(affectedRows > 0);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> removeCoinflip(long gameId) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(false); return; }
            String query = "DELETE FROM coinflips WHERE game_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, gameId);
                future.complete(ps.executeUpdate() > 0);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    private CoinflipGame parseGameFromResult(ResultSet rs) throws SQLException {
        return new CoinflipGame(
                rs.getLong("game_id"),
                UUID.fromString(rs.getString("creator_uuid")),
                rs.getString("creator_name"),
                CoinflipGame.CoinSide.valueOf(rs.getString("chosen_side")),
                rs.getDouble("amount"),
                rs.getLong("creation_time"),
                rs.getString("status")
        );
    }

    // --- Stats Management Methods (FIXED) ---

    public CompletableFuture<CoinflipStats> loadPlayerStats(UUID playerUUID) {
        CompletableFuture<CoinflipStats> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(new CoinflipStats(playerUUID, 0, 0)); return; }
            String query = "SELECT wins, losses FROM coinflip_stats WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(new CoinflipStats(playerUUID, rs.getInt("wins"), rs.getInt("losses")));
                    } else {
                        future.complete(new CoinflipStats(playerUUID, 0, 0));
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Void> updatePlayerStats(UUID playerUUID, String playerName, boolean won) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String updateQuery = won ?
                    "INSERT INTO coinflip_stats (player_uuid, player_name, wins, losses) VALUES (?, ?, 1, 0) ON CONFLICT(player_uuid) DO UPDATE SET wins = wins + 1, player_name = excluded.player_name" :
                    "INSERT INTO coinflip_stats (player_uuid, player_name, wins, losses) VALUES (?, ?, 0, 1) ON CONFLICT(player_uuid) DO UPDATE SET losses = losses + 1, player_name = excluded.player_name";

            try (PreparedStatement ps = conn.prepareStatement(updateQuery)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, playerName);
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    // --- [Req 3] Message Toggle Methods ---
    public CompletableFuture<Void> saveMessageToggle(UUID uuid, boolean enabled) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.completeExceptionally(new SQLException("DB not connected")); return; }
            String query = "INSERT INTO coinflip_stats (player_uuid, player_name, message_toggle) VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET message_toggle = excluded.message_toggle";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                ps.setString(2, "Unknown"); // Name will be updated on next stat update
                ps.setBoolean(3, enabled);
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }

    public CompletableFuture<Boolean> loadMessageToggle(UUID uuid) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(true); return; } // Default to true
            String query = "SELECT message_toggle FROM coinflip_stats WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(rs.getBoolean("message_toggle"));
                    } else {
                        future.complete(true); // Default to true if no record
                    }
                }
            } catch (SQLException e) {
                future.completeExceptionally(e);
                future.complete(true); // Default to true on error
            }
        });
        return future;
    }

    // Cooldown methods (from your previous file, adapted)
    public void setCooldown(UUID uuid, long time) {
        String sql = "INSERT INTO coinflip_stats (player_uuid, player_name, cooldown) VALUES (?, ?, ?) ON CONFLICT(player_uuid) DO UPDATE SET cooldown = excluded.cooldown";
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, "Unknown"); // Name is required, but we might not have it here. It will be updated on next stat update.
                pstmt.setLong(3, time);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().severe("Error setting coinflip cooldown for " + uuid + ": " + e.getMessage());
            }
        });
    }

    public long getCooldown(UUID uuid) {
        String sql = "SELECT cooldown FROM coinflip_stats WHERE player_uuid = ?;";
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getLong("cooldown");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error getting coinflip cooldown for " + uuid + ": " + e.getMessage());
        }
        return 0;
    }

    // Stats methods (from your previous file, adapted)
    public CoinflipStats loadPlayerStats(Player player) {
        // --- FIX: Corrected typo getUniquelid -> getUniqueId ---
        return loadPlayerStats(player.getUniqueId()).join(); // Use the async one and block (since this is what the old code implies)
    }

    public void savePlayerStats(UUID uuid, CoinflipStats stats) {
        // This is now handled by updatePlayerStats
    }

    public Map<UUID, CoinflipStats> getAllStats() {
        Map<UUID, CoinflipStats> allStats = new HashMap<>();
        String sql = "SELECT player_uuid, wins, losses FROM coinflip_stats;";
        try (Statement stmt = getConnection().createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("player_uuid"));
                    CoinflipStats stats = new CoinflipStats(
                            uuid,
                            rs.getInt("wins"),
                            rs.getInt("losses")
                    );
                    allStats.put(uuid, stats);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Found invalid UUID in coinflip_stats database: " + rs.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading all coinflip stats: " + e.getMessage());
        }
        return allStats;
    }
}