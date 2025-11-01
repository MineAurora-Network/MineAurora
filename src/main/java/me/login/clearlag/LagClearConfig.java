package me.login.clearlag;

import me.login.Login;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the lagclear.yml file for player message toggle preferences.
 */
public class LagClearConfig {

    private final Login plugin;
    private FileConfiguration config;
    private File configFile;

    public LagClearConfig(Login plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lagclear.yml");
        saveDefaultConfig();
        reloadConfig();
    }

    public FileConfiguration getConfig() {
        if (this.config == null) {
            reloadConfig();
        }
        return this.config;
    }

    public void reloadConfig() {
        if (this.configFile == null) {
            this.configFile = new File(plugin.getDataFolder(), "lagclear.yml");
        }
        this.config = YamlConfiguration.loadConfiguration(this.configFile);

        // Look for defaults in the jar
        if (plugin.getResource("lagclear.yml") != null) {
            YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(plugin.getResource("lagclear.yml")));
            this.config.setDefaults(defaultConfig);
        }
    }

    public void saveConfig() {
        if (this.config == null || this.configFile == null) {
            return;
        }
        try {
            getConfig().save(this.configFile);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + this.configFile, ex);
        }
    }

    public void saveDefaultConfig() {
        if (this.configFile == null) {
            this.configFile = new File(plugin.getDataFolder(), "lagclear.yml");
        }
        if (!this.configFile.exists()) {
            plugin.saveResource("lagclear.yml", false);
        }
    }

    // --- Player Toggle Methods ---

    /**
     * Gets a player's message toggle setting.
     * @param uuid The player's UUID.
     * @return true if messages are enabled, false otherwise. Defaults to true.
     */
    public boolean getPlayerToggle(UUID uuid) {
        // Defaults to true if not set
        return getConfig().getBoolean("players." + uuid.toString(), true);
    }

    /**
     * Sets a player's message toggle setting and saves the config.
     * @param uuid The player's UUID.
     * @param value The new setting.
     */
    public void setPlayerToggle(UUID uuid, boolean value) {
        getConfig().set("players." + uuid.toString(), value);
        saveConfig();
    }
}