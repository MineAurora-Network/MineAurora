package me.login.coinflip;

import me.login.Login;
import org.bukkit.Bukkit;
import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CoinflipDatabase {

    private final Login plugin;
    private final String url;
    private Connection connection;

    public CoinflipDatabase(Login plugin) {
        this.plugin = plugin;

        // [Req 3] Create database folder and update path
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "coinflips.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting Coinflip DB...");
            try (Statement stmt = connection.createStatement()) {
                // Table for active coinflip games
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS coinflips (
                        game_id INTEGER PRIMARY KEY AUTOINCREMENT,
                        creator_uuid VARCHAR(36) NOT NULL,
                        creator_name VARCHAR(16) NOT NULL,
                        chosen_side VARCHAR(5) NOT NULL, -- HEADS or TAILS
                        amount REAL NOT NULL,
                        creation_time BIGINT NOT NULL,
                        status VARCHAR(10) NOT NULL DEFAULT 'PENDING' -- PENDING, ACTIVE, FINISHED
                    )""");

                // Table for player stats
                stmt.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS coinflip_stats (
                        player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                        player_name VARCHAR(16) NOT NULL, -- Store name for convenience, update on change
                        wins INTEGER NOT NULL DEFAULT 0,
                        losses INTEGER NOT NULL DEFAULT 0
                    )""");
                // Index for faster lookups
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_coinflips_status ON coinflips (status)");
                stmt.executeUpdate("CREATE INDEX IF NOT EXISTS idx_coinflips_creator ON coinflips (creator_uuid, status)");
            }
            plugin.getLogger().info("Coinflip DB Connected.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed connect Coinflip DB!", e);
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) connect();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting Coinflip DB connection", e);
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Coinflip DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting Coinflip DB", e);
        }
    }

    // --- Game Management ---

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

    /**
     * Loads all PENDING or ACTIVE coinflips.
     * Used by Admin menu.
     * @return A list of all ongoing games.
     */
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
            if (conn == null) { future.complete(games); return; } // Return empty list if no connection
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
            // Only update if it's currently PENDING
            String query = "UPDATE coinflips SET status = 'ACTIVE' WHERE game_id = ? AND status = 'PENDING'";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setLong(1, gameId);
                int affectedRows = ps.executeUpdate();
                future.complete(affectedRows > 0); // True if the update was successful (it was PENDING)
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
        long id = rs.getLong("game_id");
        UUID creatorUUID = UUID.fromString(rs.getString("creator_uuid"));
        String creatorName = rs.getString("creator_name");
        CoinflipGame.CoinSide side = CoinflipGame.CoinSide.valueOf(rs.getString("chosen_side"));
        double amount = rs.getDouble("amount");
        long creationTime = rs.getLong("creation_time");
        // [Req 8] Add status to CoinflipGame object
        String status = rs.getString("status");

        return new CoinflipGame(id, creatorUUID, creatorName, side, amount, creationTime, status);
    }


    // --- Stats Management ---

    public CompletableFuture<CoinflipStats> loadPlayerStats(UUID playerUUID) {
        CompletableFuture<CoinflipStats> future = new CompletableFuture<>();
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            Connection conn = getConnection();
            if (conn == null) { future.complete(new CoinflipStats(playerUUID, 0, 0)); return; } // Return default if no connection
            String query = "SELECT wins, losses FROM coinflip_stats WHERE player_uuid = ?";
            try (PreparedStatement ps = conn.prepareStatement(query)) {
                ps.setString(1, playerUUID.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        future.complete(new CoinflipStats(playerUUID, rs.getInt("wins"), rs.getInt("losses")));
                    } else {
                        future.complete(new CoinflipStats(playerUUID, 0, 0)); // Player not found, return default
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

            String updateQuery;
            if (won) {
                // Atomically increment wins, or insert new record
                updateQuery = """
                     INSERT INTO coinflip_stats (player_uuid, player_name, wins, losses) VALUES (?, ?, 1, 0)
                     ON CONFLICT(player_uuid) DO UPDATE SET wins = wins + 1, player_name = excluded.player_name
                     """;
            } else {
                // Atomically increment losses, or insert new record
                updateQuery = """
                    INSERT INTO coinflip_stats (player_uuid, player_name, wins, losses) VALUES (?, ?, 0, 1)
                    ON CONFLICT(player_uuid) DO UPDATE SET losses = losses + 1, player_name = excluded.player_name
                    """;
            }

            try (PreparedStatement ps = conn.prepareStatement(updateQuery)) {
                ps.setString(1, playerUUID.toString());
                ps.setString(2, playerName); // Update name on insert/update
                ps.executeUpdate();
                future.complete(null);
            } catch (SQLException e) {
                future.completeExceptionally(e);
            }
        });
        return future;
    }
}