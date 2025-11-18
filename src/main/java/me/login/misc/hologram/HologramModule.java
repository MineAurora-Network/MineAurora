package me.login.misc.hologram;

import me.login.Login;
import me.login.misc.rtp.RTPModule;
import org.bukkit.Bukkit;

public class HologramModule {

    private final Login plugin;
    private final RTPModule rtpModule;
    private HologramManager hologramManager;
    private HologramDatabase hologramDatabase;
    private RTPHologramInteraction rtpInteraction; // Added field to store instance

    public HologramModule(Login plugin, RTPModule rtpModule) {
        this.plugin = plugin;
        this.rtpModule = rtpModule;
    }

    public void enable() {
        // Init database
        this.hologramDatabase = new HologramDatabase(plugin);

        // Init manager
        this.hologramManager = new HologramManager(this);

        // Init RTP interaction handler
        this.rtpInteraction = new RTPHologramInteraction(this, rtpModule);

        // Register listeners
        Bukkit.getPluginManager().registerEvents(new HologramListener(this, rtpInteraction), plugin);
        // Register the new NetherPortalListener
        Bukkit.getPluginManager().registerEvents(new NetherPortalListener(rtpModule, this.rtpInteraction), plugin);


        // Register command
        HologramCommand hologramCommand = new HologramCommand(this);
        plugin.getCommand("hologram").setExecutor(hologramCommand);
        plugin.getCommand("hologram").setTabCompleter(hologramCommand);

        // Load and spawn all holograms from the database
        hologramManager.loadHolograms();

        plugin.getLogger().info("Hologram module enabled successfully.");
    }

    public void disable() {
        // Despawn all holograms
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

    // Added getter for other listeners to access
    public RTPHologramInteraction getRtpInteraction() {
        return rtpInteraction;
    }
}