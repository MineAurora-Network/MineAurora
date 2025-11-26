package me.login.misc.tokens;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class TokenDatabase {

    private final String url;
    private Connection connection;
    private final Login plugin;

    public TokenDatabase(Login plugin) {
        this.plugin = plugin;
        // Requirement 6: Ensure DB is in plugins/Login/database
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "tokens.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connecting Token DB...");
            createTables();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect Token DB!", e);
            this.connection = null;
        }
    }

    public Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                connect();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error getting Token DB connection", e);
        }
        return connection;
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Token DB Disconnected.");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error disconnecting Token DB", e);
        }
    }

    public void createTables() {
        String tokensTable = """
            CREATE TABLE IF NOT EXISTS player_tokens (
                player_uuid VARCHAR(36) PRIMARY KEY NOT NULL,
                tokens BIGINT NOT NULL DEFAULT 0
            );""";

        try (Statement stmt = getConnection().createStatement()) {
            stmt.executeUpdate(tokensTable);

            // FIX: Attempt to add column if it's missing (solves SQLITE_ERROR if table existed but column didn't)
            try {
                stmt.executeUpdate("ALTER TABLE player_tokens ADD COLUMN tokens BIGINT NOT NULL DEFAULT 0;");
                plugin.getLogger().info("Added 'tokens' column to player_tokens table.");
            } catch (SQLException ignored) {
                // Column likely already exists, ignore
            }

            plugin.getLogger().info("Token table created or verified.");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create Token tables!", e);
        }
    }

    public CompletableFuture<Long> getTokenBalance(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT tokens FROM player_tokens WHERE player_uuid = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    return rs.getLong("tokens");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not get token balance for " + uuid, e);
            }
            return 0L;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    public void addTokens(UUID uuid, long amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_tokens (player_uuid, tokens) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET tokens = tokens + ?";
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

    public CompletableFuture<Boolean> removeTokens(UUID uuid, long amount) {
        return getTokenBalance(uuid).thenApply(balance -> {
            if (balance < amount) return false;

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                String sql = "UPDATE player_tokens SET tokens = tokens - ? WHERE player_uuid = ?";
                try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                    ps.setLong(1, amount);
                    ps.setString(2, uuid.toString());
                    ps.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.WARNING, "Could not remove tokens for " + uuid, e);
                }
            });
            return true;
        });
    }

    public void setTokens(UUID uuid, long amount) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "INSERT INTO player_tokens (player_uuid, tokens) VALUES (?, ?) " +
                    "ON CONFLICT(player_uuid) DO UPDATE SET tokens = ?";
            try (PreparedStatement ps = getConnection().prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, amount);
                ps.setLong(3, amount);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Could not set tokens for " + uuid, e);
            }
        });
    }
}