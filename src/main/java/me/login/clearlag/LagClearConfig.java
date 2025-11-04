package me.login.clearlag;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages the lagclear.yml file and message formatting.
 */
public class LagClearConfig {

    private final Login plugin;
    private FileConfiguration config;
    private File configFile;

    // --- NEW: Kyori Adventure fields ---
    private final MiniMessage miniMessage;
    private final Component serverPrefix;
    private final String legacyPrefix; // Fallback

    public LagClearConfig(Login plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "lagclear.yml");
        saveDefaultConfig();
        reloadConfig();

        // --- NEW: Initialize prefixes ---
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("cleaner_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>CLEANER</gradient></b><white>:");
        String legacyPrefixString = plugin.getConfig().getString("cleaner_prefix_2", "&x&4&7&F&0&D&E&lC&x&4&2&A&C&F&1&lL&x&3&4&A&3&F&1&lE&x&2&6&9&9&F&0&lA&x&1&7&9&0&F&0&lN&x&0&9&8&6&E&F&lE&x&0&9&8&6&E&F&lR&f:");

        Component parsedPrefix;
        try {
            parsedPrefix = this.miniMessage.deserialize(prefixString + " ");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse 'cleaner_prefix' with MiniMessage. Using fallback 'cleaner_prefix_2'.");
            parsedPrefix = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyPrefixString + " ");
        }
        this.serverPrefix = parsedPrefix;
        this.legacyPrefix = legacyPrefixString;
        // --- END NEW ---
    }

    /**
     * Formats a MiniMessage string with the server prefix.
     * @param miniMessageString The MiniMessage string (e.g., "<red>Hello</red>")
     * @return A formatted Component
     */
    public Component formatMessage(String miniMessageString) {
        try {
            return this.serverPrefix.append(this.miniMessage.deserialize(miniMessageString));
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse MiniMessage: " + miniMessageString + ". Using legacy.");
            // Fallback to legacy
            String legacyMessage = LegacyComponentSerializer.legacyAmpersand().serialize(this.miniMessage.deserialize(miniMessageString));
            return LegacyComponentSerializer.legacyAmpersand().deserialize(this.legacyPrefix + " " + legacyMessage);
        }
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

    public boolean getPlayerToggle(UUID uuid) {
        return getConfig().getBoolean("players." + uuid.toString(), true);
    }

    public void setPlayerToggle(UUID uuid, boolean value) {
        getConfig().set("players." + uuid.toString(), value);
        saveConfig();
    }
}