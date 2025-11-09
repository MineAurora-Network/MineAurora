package me.login.misc.creatorcode;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CreatorCodeManager {

    private final Login plugin;
    private final File configFile;
    private FileConfiguration config;

    // Use a thread-safe Set for creator codes, stored in lowercase
    private final Set<String> creatorCodes = ConcurrentHashMap.newKeySet();

    public CreatorCodeManager(Login plugin) {
        this.plugin = plugin;
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }
        this.configFile = new File(dbFolder, "creatorcodes.yml");
    }

    /**
     * Loads the creator codes from creatorcodes.yml into memory.
     */
    public void loadCodes() {
        if (!configFile.exists()) {
            plugin.saveResource("creatorcodes.yml", false);
            plugin.getLogger().info("Created default creatorcodes.yml in database folder.");
        }

        config = YamlConfiguration.loadConfiguration(configFile);
        creatorCodes.clear();

        for (String code : config.getStringList("creators")) {
            creatorCodes.add(code.toLowerCase());
        }
        plugin.getLogger().info("Loaded " + creatorCodes.size() + " creator codes.");
    }

    /**
     * Saves the current list of codes back to the YML file asynchronously.
     */
    private void saveCodesAsync() {
        // Create a snapshot of the list for async saving
        final Set<String> codesToSave = new HashSet<>(creatorCodes);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                config.set("creators", new ArrayList<>(codesToSave));
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save creatorcodes.yml!", e);
            }
        });
    }

    /**
     * Adds a new creator code to the list and saves.
     * @param code The code to add.
     * @return true if the code was added, false if it already existed.
     */
    public boolean addCode(String code) {
        boolean added = creatorCodes.add(code.toLowerCase());
        if (added) {
            saveCodesAsync();
        }
        return added;
    }

    /**
     * Removes a creator code from the list and saves.
     * @param code The code to remove.
     * @return true if the code was removed, false if it didn't exist.
     */
    public boolean removeCode(String code) {
        boolean removed = creatorCodes.remove(code.toLowerCase());
        if (removed) {
            saveCodesAsync();
        }
        return removed;
    }

    /**
     * Checks if a given code is valid (exists in the list).
     * @param code The code to check.
     * @return true if the code is valid, false otherwise.
     */
    public boolean isCodeValid(String code) {
        return creatorCodes.contains(code.toLowerCase());
    }

    /**
     * Gets an unmodifiable copy of the current creator codes.
     * @return A Set of creator codes.
     */
    public Set<String> getCodes() {
        return Collections.unmodifiableSet(creatorCodes);
    }
}