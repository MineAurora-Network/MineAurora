package me.login.misc.hologram;

import me.login.Login;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class HologramDatabase {

    private final Login plugin;
    private FileConfiguration databaseConfig = null;
    private final File databaseFile;

    public HologramDatabase(Login plugin) {
        this.plugin = plugin;
        this.databaseFile = new File(plugin.getDataFolder(), "database/holograms.yml");
        if (!databaseFile.exists()) {
            try {
                databaseFile.getParentFile().mkdirs();
                databaseFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create holograms.yml! " + e.getMessage());
            }
        }
        reloadConfig();
    }

    public FileConfiguration getConfig() {
        if (databaseConfig == null) {
            reloadConfig();
        }
        return databaseConfig;
    }

    public void reloadConfig() {
        databaseConfig = YamlConfiguration.loadConfiguration(databaseFile);
    }

    public void saveConfig() {
        try {
            databaseConfig.save(databaseFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save holograms.yml! " + e.getMessage());
        }
    }

    public void saveHologram(Hologram hologram) {
        String path = "holograms." + hologram.getName();
        Location loc = hologram.getBaseLocation();

        getConfig().set(path + ".location.world", loc.getWorld().getName());
        getConfig().set(path + ".location.x", loc.getX());
        getConfig().set(path + ".location.y", loc.getY());
        getConfig().set(path + ".location.z", loc.getZ());

        saveConfig();
    }
}