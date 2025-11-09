package me.login.leaderboards;

import me.login.Login;
import org.bukkit.scheduler.BukkitTask;

public class LeaderboardModule {

    private final Login plugin;
    private LeaderboardDisplayManager leaderboardManager;
    private BukkitTask leaderboardUpdateTask;

    public LeaderboardModule(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes the leaderboard module, manager, commands, listeners, and tasks.
     */
    public boolean init() {
        plugin.getLogger().info("Initializing LeaderboardModule...");

        // 1. Initialize the manager (which loads/creates leaderboards.yml)
        this.leaderboardManager = new LeaderboardDisplayManager(plugin);

        // 2. Register commands
        registerCommands();

        // 3. Register listeners
        registerListeners();

        // 4. Start the update task
        startUpdateTask();

        plugin.getLogger().info("LeaderboardModule enabled successfully.");
        return true;
    }

    /**
     * Shuts down the leaderboard module and cancels tasks.
     */
    public void shutdown() {
        if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
            this.leaderboardUpdateTask.cancel();
        }
        plugin.getLogger().info("LeaderboardModule disabled.");
    }

    /**
     * Handles the logic for reloading the leaderboards.
     */
    public void reload() {
        // Reload main config (for formats)
        plugin.reloadConfig();

        // Reload leaderboards.yml and update all displays
        if (leaderboardManager != null) {
            leaderboardManager.reloadConfigAndUpdateAll();
        }

        // Cancel and restart the task to apply new refresh-seconds
        if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
            this.leaderboardUpdateTask.cancel();
        }
        startUpdateTask();
    }

    /**
     * Registers the /leaderboard and /killleaderboard commands.
     */
    private void registerCommands() {
        // Note: We pass 'this' (the module) to the command for reloading
        LeaderboardCommand leaderboardCmd = new LeaderboardCommand(plugin, this, this.leaderboardManager);
        plugin.getCommand("leaderboard").setExecutor(leaderboardCmd);
        plugin.getCommand("leaderboard").setTabCompleter(leaderboardCmd);

        KillLeaderboardCommand killLeaderboardCmd = new KillLeaderboardCommand(plugin, this.leaderboardManager);
        plugin.getCommand("killleaderboard").setExecutor(killLeaderboardCmd);
        plugin.getCommand("killleaderboard").setTabCompleter(killLeaderboardCmd);
    }

    /**
     * Registers the entity protection listener.
     */
    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new LeaderboardProtectionListener(this.leaderboardManager), plugin);
    }

    /**
     * Starts the repeating task to update leaderboard displays.
     */
    private void startUpdateTask() {
        long delay = 20L * 10; // 10 seconds
        long refreshTicks = 20L * plugin.getConfig().getLong("leaderboards.refresh-seconds", 60);
        this.leaderboardUpdateTask = new LeaderboardUpdateTask(this.leaderboardManager).runTaskTimer(plugin, delay, refreshTicks);
    }

    // --- Public Getter ---

    public LeaderboardDisplayManager getLeaderboardManager() {
        return leaderboardManager;
    }
}