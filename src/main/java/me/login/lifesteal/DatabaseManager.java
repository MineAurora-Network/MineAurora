package me.login.lifesteal;

import me.login.Login;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class DatabaseManager {

    private final Login plugin;
    private Connection connection;
    private final File dbFile;

    private final int DEFAULT_HEARTS = 10;

    // --- MODIFICATION: Updated Constructor ---
    public DatabaseManager(Login plugin) {
        this.plugin = plugin;

        // Create database subfolder
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }

        // Set the file path to be inside the database folder
        this.dbFile = new File(dbFolder, "lifesteal.db");
    }
    // --- END MODIFICATION ---

    // --- Connection Management ---

    public synchronized boolean initializeDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                return true;
            }

            if (!dbFile.exists()) {
                dbFile.createNewFile();
            }

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getPath());
            // --- MODIFICATION: Updated log message to show correct path ---
            plugin.getLogger().info("Lifesteal SQLite database connected successfully (at " + dbFile.getAbsolutePath() + ").");
            // --- END MODIFICATION ---

            return true;

        } catch (SQLException | ClassNotFoundException | IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize SQLite database connection!", e);
            return false;
        }
    }

    public synchronized void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("SQLite database connection closed.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing SQLite connection!", e);
        }
    }

    private Connection getConnection() throws SQLException {
        if (connection == null || connection.isClosed()) {
            if (!initializeDatabase()) {
                throw new SQLException("Failed to re-initialize database connection.");
            }
        }
        return connection;
    }

    public void createTables() {
        String createHeartsTable = "CREATE TABLE IF NOT EXISTS player_hearts (" +
                "uuid TEXT PRIMARY KEY," +
                "hearts INTEGER NOT NULL" +
                ");";

        String createDeadPlayersTable = "CREATE TABLE IF NOT EXISTS dead_players (" +
                "uuid TEXT PRIMARY KEY," +
                "username TEXT NOT NULL" +
                ");";

        // --- FIX: Connection is no longer in try-with-resources ---
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(createHeartsTable);
            stmt.execute(createDeadPlayersTable);
            plugin.getLogger().info("Lifesteal tables created or verified.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create database tables!", e);
        }
    }

    // --- Player Hearts Data (LifestealManager) ---

    public void getHearts(UUID uuid, java.util.function.Consumer<Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT hearts FROM player_hearts WHERE uuid = ?;";

            // --- FIX: Connection is no longer in try-with-resources ---
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                ResultSet rs = pstmt.executeQuery();

                int hearts = DEFAULT_HEARTS;
                if (rs.next()) {
                    hearts = rs.getInt("hearts");
                } else {
                    setHearts(uuid, DEFAULT_HEARTS); // This is async
                }

                final int finalHeartsToCallback = hearts;

                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalHeartsToCallback));

            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get hearts for " + uuid, e);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(DEFAULT_HEARTS));
            }
        });
    }

    public int getHeartsSync(UUID uuid) {
        String sql = "SELECT hearts FROM player_hearts WHERE uuid = ?;";

        // --- FIX: Connection is no longer in try-with-resources ---
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return rs.getInt("hearts");
            } else {
                setHeartsSync(uuid, DEFAULT_HEARTS);
                return DEFAULT_HEARTS;
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not get hearts sync for " + uuid, e);
            return DEFAULT_HEARTS;
        }
    }

    public void setHearts(UUID uuid, int hearts) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            setHeartsSync(uuid, hearts);
        });
    }

    // This was line 155
    private void setHeartsSync(UUID uuid, int hearts) {
        String sql = "INSERT OR REPLACE INTO player_hearts (uuid, hearts) VALUES (?, ?);";

        // --- FIX: Connection is no longer in try-with-resources ---
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            pstmt.setInt(2, hearts);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Could not set hearts for " + uuid, e);
        }
    }

    // --- Dead Players Data (DeadPlayerManager) ---

    // This was line 171
    public Map<UUID, String> getDeadPlayersMapSync() {
        Map<UUID, String> deadPlayers = new HashMap<>();
        String sql = "SELECT uuid, username FROM dead_players;";

        // --- FIX: Connection is no longer in try-with-resources ---
        try (PreparedStatement pstmt = getConnection().prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String username = rs.getString("username");
                    deadPlayers.put(uuid, username);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Invalid UUID in dead_players table: " + rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load dead players map!", e);
        }
        return deadPlayers;
    }

    public void addDeadPlayer(UUID uuid, String username) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT OR IGNORE INTO dead_players (uuid, username) VALUES (?, ?);";

            // --- FIX: Connection is no longer in try-with-resources ---
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, username);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not add dead player " + uuid, e);
            }
        });
    }

    public void removeDeadPlayer(UUID uuid) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "DELETE FROM dead_players WHERE uuid = ?;";

            // --- FIX: Connection is no longer in try-with-resources ---
            try (PreparedStatement pstmt = getConnection().prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not remove dead player " + uuid, e);
            }
        });
    }
}