package me.login.discord.moderation.discord;

import me.login.Login;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.List;

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
                configFile.createNewFile();
                config = YamlConfiguration.loadConfiguration(configFile);
                config.options().setHeader(List.of("Discord Moderation Configuration"));
                config.set("max-warnings", 3);
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

    public int getMaxWarnings() {
        return config.getInt("max-warnings", 3);
    }
}