package me.login.leaderboards;

import me.login.Login;
import org.bukkit.scheduler.BukkitTask;

public class LeaderboardModule {

    private final Login plugin;
    private LeaderboardDisplayManager leaderboardManager;
    private LeaderboardGUI leaderboardGUI; // Store GUI instance
    private BukkitTask leaderboardUpdateTask;
    private static boolean showOps = true;

    public LeaderboardModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        plugin.getLogger().info("Initializing LeaderboardModule...");

        this.leaderboardManager = new LeaderboardDisplayManager(plugin);
        this.leaderboardGUI = new LeaderboardGUI(plugin); // Initialize GUI here

        registerCommands();
        registerListeners();
        startUpdateTask();

        plugin.getLogger().info("LeaderboardModule enabled successfully.");
        return true;
    }

    public void shutdown() {
        if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
            this.leaderboardUpdateTask.cancel();
        }
        if (leaderboardManager != null) {
            leaderboardManager.removeAll();
        }
        plugin.getLogger().info("LeaderboardModule disabled.");
    }

    public void reload() {
        plugin.reloadConfig();

        if (leaderboardManager != null) {
            leaderboardManager.reloadConfigAndUpdateAll();
        }

        if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
            this.leaderboardUpdateTask.cancel();
        }
        startUpdateTask();
    }

    private void registerCommands() {
        // Pass the module instance for reload/toggleop functionality
        LeaderboardCommand leaderboardCmd = new LeaderboardCommand(plugin, this, this.leaderboardManager);
        plugin.getCommand("leaderboard").setExecutor(leaderboardCmd);
        plugin.getCommand("leaderboard").setTabCompleter(leaderboardCmd);

        KillLeaderboardCommand killLeaderboardCmd = new KillLeaderboardCommand(plugin, this.leaderboardManager);
        plugin.getCommand("killleaderboard").setExecutor(killLeaderboardCmd);
        plugin.getCommand("killleaderboard").setTabCompleter(killLeaderboardCmd);
    }

    private void registerListeners() {
        // Register Protection Listener
        plugin.getServer().getPluginManager().registerEvents(new LeaderboardProtectionListener(this.leaderboardManager), plugin);

        // Register GUI Listener (The GUI class itself implements Listener)
        plugin.getServer().getPluginManager().registerEvents(this.leaderboardGUI, plugin);

        // Register NPC Listener (Check if Citizens is enabled first to be safe, or just register if hard dependency)
        if (plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getServer().getPluginManager().registerEvents(new LeaderboardNPCListener(plugin, this.leaderboardGUI), plugin);
        } else {
            plugin.getLogger().warning("Citizens not found! Leaderboard NPC will not work.");
        }
    }

    private void startUpdateTask() {
        long delay = 20L * 10;
        long refreshTicks = 20L * plugin.getConfig().getLong("leaderboards.refresh-seconds", 60);
        this.leaderboardUpdateTask = new LeaderboardUpdateTask(this.leaderboardManager).runTaskTimer(plugin, delay, refreshTicks);
    }

    public LeaderboardDisplayManager getLeaderboardManager() {
        return leaderboardManager;
    }

    public static boolean isShowOps() {
        return showOps;
    }

    public static void setShowOps(boolean show) {
        showOps = show;
    }
}