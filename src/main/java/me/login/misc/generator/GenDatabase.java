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
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "generators.db";
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
            // Store location as world,x,y,z string or separate columns. Separate is better for queries.
            s.executeUpdate("CREATE TABLE IF NOT EXISTS generators (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`owner_uuid` VARCHAR(36) NOT NULL," +
                    "`world` VARCHAR(50) NOT NULL," +
                    "`x` INTEGER NOT NULL," +
                    "`y` INTEGER NOT NULL," +
                    "`z` INTEGER NOT NULL," +
                    "`tier_id` VARCHAR(32) NOT NULL" +
                    ");");
            s.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating generator table", e);
        }
    }

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

    public void removeAllByPlayer(String ownerUUID) {
        String sql = "DELETE FROM generators WHERE owner_uuid = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ownerUUID);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().severe("Error removing player generators: " + ex.getMessage());
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

    public ResultSet getPlayerGenerators(String uuid) {
        try {
            PreparedStatement ps = getSQLConnection().prepareStatement("SELECT * FROM generators WHERE owner_uuid = ?");
            ps.setString(1, uuid);
            return ps.executeQuery();
        } catch (SQLException ex) {
            return null;
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) connection.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}