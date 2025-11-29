package me.login.level;

import me.login.Login;
import org.bukkit.Bukkit;

import java.io.File;
import java.sql.*;
import java.util.UUID;
import java.util.logging.Level;

public class LevelDatabase {

    private final Login plugin;
    private final String url;
    private Connection connection;

    public LevelDatabase(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "levels.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTable();
            plugin.getLogger().info("Level Database connected.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to Level Database!", e);
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void createTable() {
        String sql = "CREATE TABLE IF NOT EXISTS player_levels (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "level INTEGER DEFAULT 0, " +
                "current_xp INTEGER DEFAULT 0" +
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create level table!", e);
        }
    }

    public void savePlayerData(UUID uuid, int level, int xp) {
        String sql = "INSERT INTO player_levels (uuid, level, current_xp) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET level = ?, current_xp = ?";

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, level);
                ps.setInt(3, xp);
                ps.setInt(4, level);
                ps.setInt(5, xp);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save level data for " + uuid, e);
            }
        });
    }

    public void loadPlayerData(UUID uuid, java.util.function.BiConsumer<Integer, Integer> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT level, current_xp FROM player_levels WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int level = rs.getInt("level");
                    int xp = rs.getInt("current_xp");
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(level, xp));
                } else {
                    Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0, 0));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load level data for " + uuid, e);
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(0, 0));
            }
        });
    }
}