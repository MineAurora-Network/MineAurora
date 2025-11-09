package me.login.clearlag;

import me.login.Login;

/**
 * Module to initialize and manage all components of the ClearLag system.
 * This class handles the config, commands, listeners, and repeating tasks.
 * The LagClearLogger is handled separately in Login.java due to its
 * asynchronous JDA dependency.
 */
public class LagClearModule {

    private final Login plugin;
    private LagClearConfig lagClearConfig;

    public LagClearModule(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the ClearLag module.
     * Loads config, registers listeners, commands, and starts tasks.
     * @return true if initialization was successful.
     */
    public boolean init() {
        plugin.getLogger().info("Initializing ClearLag components...");

        // 1. Load the configuration
        // This now loads from /database/lagclear.yml
        this.lagClearConfig = new LagClearConfig(plugin);

        // 2. Register listeners
        plugin.getServer().getPluginManager().registerEvents(new PlacementLimitListener(plugin, this.lagClearConfig), plugin);

        // 3. Register commands
        LagClearCommand lagClearCmd = new LagClearCommand(plugin, this.lagClearConfig);
        plugin.getCommand("lagclear").setExecutor(lagClearCmd);
        plugin.getCommand("lagclear").setTabCompleter(lagClearCmd);

        // 4. Start repeating tasks
        long countdownInterval = 20L; // 1 second
        new CleanupTask(plugin, this.lagClearConfig).runTaskTimer(plugin, countdownInterval, countdownInterval);

        long tpsCheckInterval = 200L; // 10 seconds
        new TPSWatcher(plugin).runTaskTimer(plugin, tpsCheckInterval, tpsCheckInterval);

        plugin.getLogger().info("ClearLag components enabled.");
        return true;
    }

    /**
     * Shuts down the LagClear module.
     * (Tasks are automatically cancelled by Bukkit on plugin disable)
     */
    public void shutdown() {
        // All tasks are auto-cancelled.
        // The LagClearLogger has its own shutdown method in Login.java
        plugin.getLogger().info("ClearLagModule disabled.");
    }

    /**
     * Gets the loaded LagClear configuration.
     * @return The LagClearConfig instance.
     */
    public LagClearConfig getLagClearConfig() {
        return this.lagClearConfig;
    }
}