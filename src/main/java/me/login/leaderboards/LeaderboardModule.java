package me.login.leaderboards;

import me.login.Login;
import me.login.misc.tokens.TokenModule;
import me.login.premimumfeatures.credits.CreditsModule;
import org.bukkit.scheduler.BukkitTask;

public class LeaderboardModule {

    private final Login plugin;
    private LeaderboardDisplayManager leaderboardManager;
    private LeaderboardGUI leaderboardGUI;
    private StatsFetcher statsFetcher;
    private BukkitTask leaderboardUpdateTask;
    private static boolean showOps = true;

    public LeaderboardModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        plugin.getLogger().info("Initializing LeaderboardModule...");

        // 1. Get Database Dependencies
        TokenModule tokenModule = plugin.getTokenModule();
        CreditsModule creditsModule = plugin.getCreditsModule();

        // 2. Initialize StatsFetcher with dependencies
        this.statsFetcher = new StatsFetcher(plugin, tokenModule, creditsModule);

        // 3. Initialize Managers
        this.leaderboardManager = new LeaderboardDisplayManager(plugin, statsFetcher);
        this.leaderboardGUI = new LeaderboardGUI(plugin, statsFetcher, leaderboardManager);

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
        LeaderboardCommand leaderboardCmd = new LeaderboardCommand(plugin, this, this.leaderboardManager, this.leaderboardGUI);
        plugin.getCommand("leaderboard").setExecutor(leaderboardCmd);
        plugin.getCommand("leaderboard").setTabCompleter(leaderboardCmd);

        KillLeaderboardCommand killLeaderboardCmd = new KillLeaderboardCommand(plugin, this.leaderboardManager);
        plugin.getCommand("killleaderboard").setExecutor(killLeaderboardCmd);
        plugin.getCommand("killleaderboard").setTabCompleter(killLeaderboardCmd);
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new LeaderboardProtectionListener(this.leaderboardManager), plugin);
        plugin.getServer().getPluginManager().registerEvents(this.leaderboardGUI, plugin);

        if (plugin.getServer().getPluginManager().isPluginEnabled("Citizens")) {
            plugin.getServer().getPluginManager().registerEvents(new LeaderboardNPCListener(plugin, this.leaderboardGUI), plugin);
        }
    }

    private void startUpdateTask() {
        long delay = 20L * 10;
        long refreshTicks = 20L * plugin.getConfig().getLong("leaderboards.refresh-seconds", 60);
        this.leaderboardUpdateTask = new LeaderboardUpdateTask(this.leaderboardManager).runTaskTimer(plugin, delay, refreshTicks);
    }

    public static boolean isShowOps() { return showOps; }
    public static void setShowOps(boolean show) { showOps = show; }
}