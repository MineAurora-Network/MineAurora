package me.login.misc.holograms;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main class to initialize the Hologram module.
 * This class should be called from your main plugin's onEnable() and onDisable().
 */
public class HologramModule {

    private HologramConfig hologramConfig;
    private HologramManager hologramManager;
    private HologramStorage hologramStorage; // --- ADDED ---
    private final JavaPlugin plugin;

    public HologramModule(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // 1. Initialize and load config
        this.hologramConfig = new HologramConfig();
        this.hologramConfig.load(plugin);

        // 2. Initialize storage --- ADDED ---
        this.hologramStorage = new HologramStorage(plugin);

        // 3. Initialize manager --- MODIFIED ---
        this.hologramManager = new HologramManager(plugin, hologramConfig, hologramStorage);

        // 4. Register command --- MODIFIED ---
        HologramCommands hologramCommands = new HologramCommands(hologramManager, hologramConfig);
        PluginCommand pluginCommand = plugin.getCommand("commandhologram");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(hologramCommands);
            pluginCommand.setTabCompleter(hologramCommands);
        } else {
            plugin.getLogger().severe("Could not register /commandhologram command! Is it in plugin.yml?");
        }

        // 5. Register listeners
        plugin.getServer().getPluginManager().registerEvents(new HologramListener(hologramManager), plugin);

        // 6. Spawn holograms from storage --- ADDED ---
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            hologramManager.spawnHologramsFromStorage();
        }, 20L); // Delay slightly to ensure worlds are loaded

        plugin.getLogger().info("HologramModule enabled.");
    }

    public void disable() {
        // Clean up all hologram entities from the world
        if (this.hologramManager != null) {
            this.hologramManager.removeAllHolograms();
        }
        plugin.getLogger().info("HologramModule disabled.");
    }

    /**
     * Reloads only the hologram text configuration.
     * Does not respawn or move existing holograms.
     * Called by /scoreboard reload
     */
    public void reloadConfigOnly() {
        this.hologramConfig.load(plugin);
        plugin.getLogger().info("Hologram config reloaded.");
    }

    /**
     * Reloads config and respawns all holograms from storage.
     * Called by /commandhologram reload
     */
    public void reloadAllHolograms() {
        if (this.hologramManager != null) {
            this.hologramManager.reloadAllHolograms();
        }
        plugin.getLogger().info("All holograms reloaded.");
    }
}