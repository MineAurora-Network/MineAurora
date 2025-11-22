package me.login.premimumfeatures.credits;

import me.login.Login;
import org.bukkit.Bukkit;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.UUID;

public class CreditsDatabase {

    private final Login plugin;
    private Connection connection;

    public CreditsDatabase(Login plugin) {
        this.plugin = plugin;
        initializeDatabase();
    }

    private void initializeDatabase() {
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

            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE IF NOT EXISTS player_credits (" +
                        "uuid TEXT PRIMARY KEY, " +
                        "amount DOUBLE NOT NULL DEFAULT 0)");
            }

        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().severe("Could not initialize Credits SQLite database!");
            e.printStackTrace();
        }
    }

    public double getCredits(UUID uuid) {
        String query = "SELECT amount FROM player_credits WHERE uuid = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getDouble("amount");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0.0;
    }

    public void setCredits(UUID uuid, double amount) {
        String query = "INSERT INTO player_credits (uuid, amount) VALUES (?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET amount = ?";
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, uuid.toString());
            ps.setDouble(2, amount);
            ps.setDouble(3, amount);
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addCredits(UUID uuid, double amount) {
        double current = getCredits(uuid);
        setCredits(uuid, current + amount);
    }

    public void removeCredits(UUID uuid, double amount) {
        double current = getCredits(uuid);
        setCredits(uuid, Math.max(0, current - amount));
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