package me.login.discordcommand;

import me.login.Login;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DiscordModConfig {

    private final Login plugin;
    private File configFile;
    private FileConfiguration config;

    public DiscordModConfig(Login plugin) {
        this.plugin = plugin;
        setup();
    }

    private void setup() {
        configFile = new File(plugin.getDataFolder(), "discord.yml");
        if (!configFile.exists()) {
            try {
                // Create the file and add defaults
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                config.options().setHeader(List.of(
                        "This file stores data for the Discord moderation commands.",
                        "Warning format is: \"Reason -by StaffName on Date\""
                ));
                config.set("max-warnings", 3);
                config.set("warnings", null); // Placeholder for the warnings section
                config.save(configFile);
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create discord.yml!");
                e.printStackTrace();
            }
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public void saveConfig() {
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save discord.yml!");
            e.printStackTrace();
        }
    }

    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    public int getMaxWarnings() {
        return config.getInt("max-warnings", 3);
    }

    /**
     * Gets all warnings for a specific Discord user ID.
     * @param userId The Discord user's ID.
     * @return A list of warning strings. Returns an empty list if none.
     */
    public List<String> getWarnings(long userId) {
        return config.getStringList("warnings." + userId);
    }

    /**
     * Adds a warning for a specific Discord user ID.
     * @param userId The Discord user's ID.
     * @param warningMessage The full warning message to add.
     */
    public void addWarning(long userId, String warningMessage) {
        List<String> warnings = getWarnings(userId);
        warnings.add(warningMessage);
        config.set("warnings." + userId, warnings);
        saveConfig();
    }

    /**
     * Removes the most recent warning for a specific Discord user ID.
     * @param userId The Discord user's ID.
     * @return The warning message that was removed, or null if none were found.
     */
    public String removeWarning(long userId) {
        List<String> warnings = getWarnings(userId);
        if (warnings.isEmpty()) {
            return null;
        }
        String removedWarning = warnings.remove(warnings.size() - 1); // Remove the last one
        config.set("warnings." + userId, warnings);
        saveConfig();
        return removedWarning;
    }

    /**
     * Clears all warnings for a specific Discord user ID.
     * @param userId The Discord user's ID.
     */
    public void clearWarnings(long userId) {
        config.set("warnings." + userId, null); // Remove the user's section
        saveConfig();
    }
}