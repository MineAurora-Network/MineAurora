package me.login.misc.hologram;

import me.login.Login;
import me.login.misc.rtp.RTPModule;
import org.bukkit.Bukkit;

public class HologramModule {

    private final Login plugin;
    private final RTPModule rtpModule;
    private HologramManager hologramManager;
    private HologramDatabase hologramDatabase;

    public HologramModule(Login plugin, RTPModule rtpModule) {
        this.plugin = plugin;
        this.rtpModule = rtpModule;
    }

    public void enable() {
        this.hologramDatabase = new HologramDatabase(plugin);
        this.hologramManager = new HologramManager(this);

        // Register listeners
        // Note: HologramListener no longer needs interaction logic for RTP
        Bukkit.getPluginManager().registerEvents(new HologramListener(this), plugin);

        // Register the NetherPortalListener (Modified to not use interaction)
        Bukkit.getPluginManager().registerEvents(new NetherPortalListener(rtpModule), plugin);

        // Register command
        HologramCommand hologramCommand = new HologramCommand(this);
        plugin.getCommand("hologram").setExecutor(hologramCommand);
        plugin.getCommand("hologram").setTabCompleter(hologramCommand);

        // Load and spawn all holograms from the database
        hologramManager.loadHolograms();

        plugin.getLogger().info("Hologram module enabled successfully.");
    }

    public void disable() {
        if (hologramManager != null) {
            hologramManager.despawnAllHolograms();
        }
        plugin.getLogger().info("Hologram module disabled.");
    }

    public Login getPlugin() {
        return plugin;
    }

    public HologramManager getHologramManager() {
        return hologramManager;
    }

    public HologramDatabase getDatabase() {
        return hologramDatabase;
    }
}