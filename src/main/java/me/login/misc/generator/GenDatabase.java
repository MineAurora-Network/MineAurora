package me.login.misc.generator;

import me.login.Login;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.logging.Level;

public class GenDatabase {
    private final Login plugin;
    private Connection connection;
    private final String dbPath;

    public GenDatabase(Login plugin) {
        this.plugin = plugin;

        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs(); // This creates the directory if it doesn't exist
        }

        this.dbPath = databaseDir.getAbsolutePath() + File.separator + "generators.db";
        initializeDatabase();
    }

    private Connection getSQLConnection() {
        File dataFolder = new File(dbPath);
        if (!dataFolder.exists()) {
            try {
                if (!dataFolder.createNewFile()) {
                    plugin.getLogger().severe("Could not create generators.db file!");
                    return null;
                }
            } catch (IOException e) {
                plugin.getLogger().severe("IO Exception creating generators.db");
                return null;
            }
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            return connection;
        } catch (SQLException | ClassNotFoundException ex) {
            plugin.getLogger().log(Level.SEVERE, "SQLite exception", ex);
        }
        return null;
    }

    private void initializeDatabase() {
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();
            // Generators table
            s.executeUpdate("CREATE TABLE IF NOT EXISTS generators (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`owner_uuid` VARCHAR(36) NOT NULL," +
                    "`world` VARCHAR(50) NOT NULL," +
                    "`x` INTEGER NOT NULL," +
                    "`y` INTEGER NOT NULL," +
                    "`z` INTEGER NOT NULL," +
                    "`tier_id` VARCHAR(32) NOT NULL" +
                    ");");

            // Limits table
            s.executeUpdate("CREATE TABLE IF NOT EXISTS gen_limits (" +
                    "`player_uuid` VARCHAR(36) PRIMARY KEY," +
                    "`limit_amount` INTEGER NOT NULL" +
                    ");");

            s.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating generator tables", e);
        }
    }

    // --- Generator Methods ---
    public void addGenerator(String ownerUUID, String world, int x, int y, int z, String tierId) {
        String sql = "INSERT INTO generators(owner_uuid, world, x, y, z, tier_id) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ownerUUID);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.setString(6, tierId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error adding generator: " + ex.getMessage());
        }
    }

    public void removeGenerator(String world, int x, int y, int z) {
        String sql = "DELETE FROM generators WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, world);
            ps.setInt(2, x);
            ps.setInt(3, y);
            ps.setInt(4, z);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error removing generator: " + ex.getMessage());
        }
    }

    public void updateGeneratorTier(String world, int x, int y, int z, String newTierId) {
        String sql = "UPDATE generators SET tier_id = ? WHERE world = ? AND x = ? AND y = ? AND z = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, newTierId);
            ps.setString(2, world);
            ps.setInt(3, x);
            ps.setInt(4, y);
            ps.setInt(5, z);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error updating generator tier: " + ex.getMessage());
        }
    }

    public ResultSet getAllGenerators() {
        try {
            return getSQLConnection().createStatement().executeQuery("SELECT * FROM generators");
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error fetching generators: " + ex.getMessage());
            return null;
        }
    }

    // --- Limit Methods ---
    public void setPlayerLimit(String uuid, int limit) {
        String sql = "INSERT INTO gen_limits(player_uuid, limit_amount) VALUES(?,?) ON CONFLICT(player_uuid) DO UPDATE SET limit_amount = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ps.setInt(2, limit);
            ps.setInt(3, limit);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error setting player limit: " + ex.getMessage());
        }
    }

    public Integer getPlayerLimit(String uuid) {
        String sql = "SELECT limit_amount FROM gen_limits WHERE player_uuid = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, uuid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                return rs.getInt("limit_amount");
            }
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error fetching player limit: " + ex.getMessage());
        }
        return null; // Return null if no custom limit set
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}