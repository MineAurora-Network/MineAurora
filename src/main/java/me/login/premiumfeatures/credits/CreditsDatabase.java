package me.login.premiumfeatures.credits;

import me.login.Login;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class CreditsDatabase {

    private final Login plugin;
    private Connection connection;

    public CreditsDatabase(Login plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
        // Ensure database folder exists: plugins/Login/database/
        File databaseFolder = new File(plugin.getDataFolder(), "database");
        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }

        File dbFile = new File(databaseFolder, "credits.db");
        if (!dbFile.exists()) {
            try {
                dbFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create credits.db file!");
                e.printStackTrace();
            }
        }

        try {
            if (connection != null && !connection.isClosed()) {
                return;
            }

            // Load SQLite driver and connect
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                // 1. Credits Table (Changed DOUBLE to INTEGER)
                statement.execute("CREATE TABLE IF NOT EXISTS player_credits (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "amount INTEGER NOT NULL DEFAULT 0)");

                // 2. Creator Codes Table
                statement.execute("CREATE TABLE IF NOT EXISTS creator_codes (" +
                        "code TEXT PRIMARY KEY)");

                // 3. Player Usage Table
                statement.execute("CREATE TABLE IF NOT EXISTS player_creator_usage (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "code TEXT NOT NULL)");
            }

        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Could not initialize Credits SQLite database!");
            e.printStackTrace();
        }
    }

    // --- Credits Methods (Integers) ---

    public int getCredits(UUID uuid) {
        String query = "SELECT amount FROM player_credits WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("amount");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public void setCredits(UUID uuid, int amount) {
        String query = "INSERT INTO player_credits (uuid, amount) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET amount = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setInt(2, amount);
            ps.setInt(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addCredits(UUID uuid, int amount) {
        int current = getCredits(uuid);
        setCredits(uuid, current + amount);
    }

    public void removeCredits(UUID uuid, int amount) {
        int current = getCredits(uuid);
        setCredits(uuid, Math.max(0, current - amount));
    }

    // --- Creator Code Methods ---

    public Set<String> getCreatorCodes() {
        Set<String> codes = new HashSet<>();
        String query = "SELECT code FROM creator_codes";
        try (PreparedStatement ps = connection.prepareStatement(query);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                codes.add(rs.getString("code"));
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load creator codes", e);
        }
        return codes;
    }

    public void addCreatorCode(String code) {
        String query = "INSERT OR IGNORE INTO creator_codes (code) VALUES (?)";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, code.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void removeCreatorCode(String code) {
        String query = "DELETE FROM creator_codes WHERE code = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, code.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public String getPlayerCreatorCode(UUID uuid) {
        String query = "SELECT code FROM player_creator_usage WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("code");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void setPlayerCreatorCode(UUID uuid, String code) {
        String query = "INSERT INTO player_creator_usage (uuid, code) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET code = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, code.toLowerCase());
            ps.setString(3, code.toLowerCase());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}