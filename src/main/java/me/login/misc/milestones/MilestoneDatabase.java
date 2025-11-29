package me.login.misc.milestones;

import me.login.Login;

import java.io.File;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class MilestoneDatabase {

    private final Login plugin;
    private final String url;
    private Connection connection;

    public MilestoneDatabase(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.url = "jdbc:sqlite:" + dbFolder.getAbsolutePath() + File.separator + "milestones.db";
    }

    public void connect() {
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection(url);
            createTable();
            plugin.getLogger().info("Milestone Database connected.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to Milestone Database!", e);
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
        String sql = "CREATE TABLE IF NOT EXISTS player_milestones (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "current_streak INTEGER DEFAULT 0, " +
                "claimed_milestones TEXT" + // Stored as "1,2,3"
                ");";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create milestone table!", e);
        }
    }

    public void savePlayerData(UUID uuid, int streak, Set<Integer> claimed) {
        String claimedStr = claimed.isEmpty() ? "" : String.join(",", claimed.stream().map(String::valueOf).toArray(String[]::new));
        String sql = "INSERT INTO player_milestones (uuid, current_streak, claimed_milestones) VALUES (?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET current_streak = ?, claimed_milestones = ?";

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, streak);
                ps.setString(3, claimedStr);
                ps.setInt(4, streak);
                ps.setString(5, claimedStr);
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to save milestone data for " + uuid, e);
            }
        });
    }

    public void loadPlayerData(UUID uuid, MilestoneManager manager) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            String sql = "SELECT current_streak, claimed_milestones FROM player_milestones WHERE uuid = ?";
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) {
                    int streak = rs.getInt("current_streak");
                    String claimedStr = rs.getString("claimed_milestones");
                    Set<Integer> claimed = new HashSet<>();
                    if (claimedStr != null && !claimedStr.isEmpty()) {
                        for (String s : claimedStr.split(",")) {
                            try {
                                claimed.add(Integer.parseInt(s));
                            } catch (NumberFormatException ignored) {}
                        }
                    }
                    // Load back to main thread
                    plugin.getServer().getScheduler().runTask(plugin, () -> manager.loadDataCallback(uuid, streak, claimed));
                } else {
                    plugin.getServer().getScheduler().runTask(plugin, () -> manager.loadDataCallback(uuid, 0, new HashSet<>()));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load milestone data for " + uuid, e);
            }
        });
    }
}