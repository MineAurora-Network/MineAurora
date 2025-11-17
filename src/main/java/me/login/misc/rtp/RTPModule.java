package me.login.misc.rtp;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;

public class RTPModule {

    private final Login plugin;
    private final RTPLogger logger;
    private boolean worldGuardEnabled = false;
    private String serverPrefix;
    private String serverPrefix2;

    private RTPLocationFinder locationFinder;
    private RTPMenu rtpMenu;
    private RTPCooldownManager cooldownManager; // <-- ADDED

    public RTPModule(Login plugin, RTPLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    public void enable() {
        // Load configuration values
        this.serverPrefix = plugin.getConfig().getString("server_prefix", "<#2EFFF0>Mine<#2E75F0>Aurora <#F02E2E>»");
        this.serverPrefix2 = plugin.getConfig().getString("server_prefix_2", "&3&lMine&1&lAurora &c&l»");

        // Attempt to hook into WorldGuard
        setupWorldGuard();

        // Initialize components
        this.locationFinder = new RTPLocationFinder(this);
        this.rtpMenu = new RTPMenu(this);
        this.cooldownManager = new RTPCooldownManager(); // <-- ADDED

        // Register the command
        RTPCommand rtpCommand = new RTPCommand(this, locationFinder, rtpMenu);
        plugin.getCommand("rtp").setExecutor(rtpCommand);
        plugin.getCommand("rtp").setTabCompleter(rtpCommand);

        // Register the GUI listener
        Bukkit.getPluginManager().registerEvents(rtpMenu, plugin);

        logger.log("RTP module enabled successfully.");
        if (worldGuardEnabled) {
            logger.log("Successfully hooked into WorldGuard for RTP safety checks.");
        } else {
            logger.log("WorldGuard not found. RTP will only check for liquids and solid blocks.");
        }
    }

    private void setupWorldGuard() {
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") != null) {
            try {
                Class.forName("com.sk89q.worldguard.WorldGuard");
                worldGuardEnabled = true;
            } catch (Exception e) {
                logger.log("WorldGuard found, but an error occurred while hooking (likely version mismatch). Disabling WG checks for RTP.");
                worldGuardEnabled = false;
            }
        }
    }

    // Getters for other classes to use
    public Login getPlugin() {
        return plugin;
    }

    public RTPLogger getLogger() {
        return logger;
    }

    public RTPLocationFinder getLocationFinder() {
        return locationFinder;
    }

    public RTPCooldownManager getCooldownManager() { // <-- ADDED
        return cooldownManager;
    }

    public boolean isWorldGuardEnabled() {
        return worldGuardEnabled;
    }

    public String getServerPrefix() {
        return serverPrefix;
    }

    public String getServerPrefix2() {
        // Return raw string; command will parse it
        return this.serverPrefix2;
    }
}