package me.login.misc.holograms;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Manages saving and loading hologram locations to a YAML file.
 * File is located at plugins/Login/database/hologramlocations.yml
 */
public class HologramStorage {

    private final JavaPlugin plugin;
    private final File storageFile;
    private FileConfiguration storageConfig;

    public HologramStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        // Create database folder if it doesn't exist
        File databaseFolder = new File(plugin.getDataFolder(), "database");
        if (!databaseFolder.exists()) {
            databaseFolder.mkdirs();
        }
        this.storageFile = new File(databaseFolder, "hologramlocations.yml");
        loadConfig();
    }

    private void loadConfig() {
        if (!storageFile.exists()) {
            try {
                storageFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create hologramlocations.yml! " + e.getMessage());
            }
        }
        this.storageConfig = YamlConfiguration.loadConfiguration(storageFile);
    }

    private void saveConfig() {
        try {
            storageConfig.save(storageFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save hologramlocations.yml! " + e.getMessage());
        }
    }

    /**
     * Saves a hologram's name and location to the YML file.
     *
     * @param name The name of the hologram.
     * @param loc  The location of the hologram.
     */
    public void saveHologram(String name, Location loc) {
        String path = "holograms." + name.toLowerCase();
        storageConfig.set(path + ".world", loc.getWorld().getName());
        storageConfig.set(path + ".x", loc.getX());
        storageConfig.set(path + ".y", loc.getY());
        storageConfig.set(path + ".z", loc.getZ());
        storageConfig.set(path + ".yaw", loc.getYaw());
        storageConfig.set(path + ".pitch", loc.getPitch());
        saveConfig();
    }

    /**
     * Removes a hologram from the YML file.
     *
     * @param name The name of the hologram.
     */
    public void removeHologram(String name) {
        storageConfig.set("holograms." + name.toLowerCase(), null);
        saveConfig();
    }

    /**
     * Removes all holograms from the YML file.
     */
    public void removeAllHolograms() {
        storageConfig.set("holograms", null);
        saveConfig();
    }

    /**
     * Loads all saved hologram names and their locations.
     *
     * @return A map where the key is the hologram name and the value is its Location.
     */
    public Map<String, Location> loadHolograms() {
        Map<String, Location> holograms = new HashMap<>();
        ConfigurationSection section = storageConfig.getConfigurationSection("holograms");
        if (section == null) {
            return holograms;
        }

        Set<String> keys = section.getKeys(false);
        for (String name : keys) {
            String path = "holograms." + name;
            World world = Bukkit.getWorld(storageConfig.getString(path + ".world", "world"));
            if (world == null) {
                plugin.getLogger().warning("World '" + storageConfig.getString(path + ".world") + "' for hologram '" + name + "' not found. Skipping.");
                continue;
            }
            double x = storageConfig.getDouble(path + ".x");
            double y = storageConfig.getDouble(path + ".y");
            double z = storageConfig.getDouble(path + ".z");
            float yaw = (float) storageConfig.getDouble(path + ".yaw");
            float pitch = (float) storageConfig.getDouble(path + ".pitch");

            holograms.put(name, new Location(world, x, y, z, yaw, pitch));
        }
        return holograms;
    }
}