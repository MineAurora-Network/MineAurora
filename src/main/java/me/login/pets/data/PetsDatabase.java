package me.login.pets.data;

import me.login.Login;
import org.bukkit.entity.EntityType;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class PetsDatabase {
    private final Login plugin;
    private Connection connection;
    public PetsDatabase(Login plugin) {
        this.plugin = plugin;
    }
    public Login getPlugin() {
        return plugin;
    }

    public boolean connect() {
        // Create the /plugins/Login/database directory
        File databaseDir = new File(plugin.getDataFolder(), "database");
        if (!databaseDir.exists()) {
            databaseDir.mkdirs();
        }
        File dataFile = new File(databaseDir, "pets.db");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create pets database file!", e);
                return false;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFile);
            createTables();
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to pets database!", e);
            return false;
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error while disconnecting from pets database!", e);
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS player_pets ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "player_uuid VARCHAR(36) NOT NULL,"
                + "pet_type VARCHAR(50) NOT NULL,"
                + "display_name TEXT,"
                + "cooldown_end_time BIGINT DEFAULT 0,"
                + "level INTEGER DEFAULT 1,"
                + "xp DOUBLE DEFAULT 0,"
                + "UNIQUE(player_uuid, pet_type)"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);

            // Add new columns (safe to run multiple times)
            try { stmt.execute("ALTER TABLE player_pets ADD COLUMN level INTEGER DEFAULT 1;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_pets ADD COLUMN xp DOUBLE DEFAULT 0;"); } catch (SQLException ignored) {}

            // --- NEW: Add inventory columns ---
            try { stmt.execute("ALTER TABLE player_pets ADD COLUMN armor_contents TEXT;"); } catch (SQLException ignored) {}
            try { stmt.execute("ALTER TABLE player_pets ADD COLUMN weapon_content TEXT;"); } catch (SQLException ignored) {}

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create pets table!", e);
        }
    }

    public List<Pet> getPlayerPets(UUID playerUuid) {
        List<Pet> pets = new ArrayList<>();
        String sql = "SELECT * FROM player_pets WHERE player_uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                EntityType petType = EntityType.valueOf(rs.getString("pet_type"));
                String displayName = rs.getString("display_name");
                long cooldownEndTime = rs.getLong("cooldown_end_time");
                int level = rs.getInt("level");
                double xp = rs.getDouble("xp");

                // --- NEW: Load inventory ---
                String armor = rs.getString("armor_contents");
                String weapon = rs.getString("weapon_content");

                Pet pet = new Pet(playerUuid, petType, displayName, cooldownEndTime, level, xp, armor, weapon);
                pets.add(pet);
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching player pets for " + playerUuid, e);
        }
        return pets;
    }

    public boolean addPet(UUID playerUuid, EntityType petType) {
        String sql = "INSERT INTO player_pets(player_uuid, pet_type, level, xp) VALUES(?,?, 1, 0)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, petType.name());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            if (e.getErrorCode() == 19) return false;
            plugin.getLogger().log(Level.SEVERE, "Error adding player pet " + petType + " for " + playerUuid, e);
            return false;
        }
    }

    public boolean removePet(UUID playerUuid, EntityType petType) {
        String sql = "DELETE FROM player_pets WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, petType.name());
            return pstmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing player pet " + petType + " for " + playerUuid, e);
            return false;
        }
    }

    public void updatePetName(UUID playerUuid, EntityType petType, String newName) {
        String sql = "UPDATE player_pets SET display_name = ? WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, newName);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setString(3, petType.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating pet name for " + playerUuid, e);
        }
    }

    public void setPetCooldown(UUID playerUuid, EntityType petType, long cooldownEndTime) {
        String sql = "UPDATE player_pets SET cooldown_end_time = ? WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, cooldownEndTime);
            pstmt.setString(2, playerUuid.toString());
            pstmt.setString(3, petType.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error setting pet cooldown for " + playerUuid, e);
        }
    }

    public void updatePetStats(UUID playerUuid, EntityType petType, int level, double xp) {
        String sql = "UPDATE player_pets SET level = ?, xp = ? WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, level);
            pstmt.setDouble(2, xp);
            pstmt.setString(3, playerUuid.toString());
            pstmt.setString(4, petType.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating pet stats for " + playerUuid, e);
        }
    }
    public void updatePetInventory(UUID playerUuid, EntityType petType, String armor, String weapon) {
        String sql = "UPDATE player_pets SET armor_contents = ?, weapon_content = ? WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, armor);
            pstmt.setString(2, weapon);
            pstmt.setString(3, playerUuid.toString());
            pstmt.setString(4, petType.name());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error updating pet inventory for " + playerUuid, e);
        }
    }
}