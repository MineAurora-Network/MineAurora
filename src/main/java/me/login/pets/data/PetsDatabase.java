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

/**
 * Handles all SQLite database interactions for the pet system.
 */
public class PetsDatabase {

    private final Login plugin;
    private Connection connection;

    public PetsDatabase(Login plugin) {
        this.plugin = plugin;
    }

    // --- FIXED: Added connect() method ---
    public boolean connect() {
        File dataFolder = new File(plugin.getDataFolder(), "pets.db");
        if (!dataFolder.exists()) {
            try {
                dataFolder.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create pets database file!", e);
                return false;
            }
        }

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dataFolder);
            createTables();
            return true;
        } catch (SQLException | ClassNotFoundException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to connect to pets database!", e);
            return false;
        }
    }

    // --- FIXED: Added disconnect() method ---
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
                + "UNIQUE(player_uuid, pet_type)"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not create pets table!", e);
        }
    }

    /**
     * Retrieves all pets owned by a specific player.
     * @param playerUuid The player's UUID.
     * @return A list of Pet objects.
     */
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

                Pet pet = new Pet(petType, displayName);
                pet.setCooldownEndTime(cooldownEndTime);
                pets.add(pet);
            }
        } catch (SQLException | IllegalArgumentException e) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching player pets for " + playerUuid, e);
        }
        return pets;
    }

    /**
     * Adds a new pet to a player.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @return true if the pet was added, false if they already own it.
     */
    public boolean addPlayerPet(UUID playerUuid, EntityType petType) {
        String sql = "INSERT INTO player_pets(player_uuid, pet_type) VALUES(?,?)";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, petType.name());
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            // Check for unique constraint violation
            if (e.getErrorCode() == 19) { // SQLITE_CONSTRAINT
                return false;
            }
            plugin.getLogger().log(Level.SEVERE, "Error adding player pet " + petType + " for " + playerUuid, e);
            return false;
        }
    }

    /**
     * Removes a pet from a player.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @return true if the pet was removed, false if they didn't own it.
     */
    public boolean removePlayerPet(UUID playerUuid, EntityType petType) {
        String sql = "DELETE FROM player_pets WHERE player_uuid = ? AND pet_type = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, playerUuid.toString());
            pstmt.setString(2, petType.name());
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error removing player pet " + petType + " for " + playerUuid, e);
            return false;
        }
    }

    /**
     * Updates the display name of a specific pet.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @param newName The new display name.
     */
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

    /**
     * Sets the cooldown end time for a specific pet.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @param cooldownEndTime The timestamp when the cooldown ends (0 for no cooldown).
     */
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

    public Login getPlugin() {
        return plugin;
    }
}