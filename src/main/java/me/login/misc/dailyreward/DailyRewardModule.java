package me.login.misc.dailyreward;

import me.login.Login;
import net.milkbowl.vault.economy.Economy;
import me.login.misc.tokens.TokenManager;
import org.bukkit.Bukkit;

public class DailyRewardModule {

    private final Login plugin;
    private final DailyRewardDatabase database;
    private final DailyRewardLogger logger;
    private final DailyRewardManager manager;
    private final DailyRewardGUI gui;
    private final DailyRewardCommand command;
    private final DailyRewardNPCListener npcListener;

    public DailyRewardModule(Login plugin, Economy economy, DailyRewardDatabase database, TokenManager tokenManager) {
        // ...
        this.plugin = plugin;
        this.database = database; // Use the one from Login.java
        this.logger = new DailyRewardLogger(plugin);
        this.manager = new DailyRewardManager(plugin, database, logger, economy, tokenManager);
        this.gui = new DailyRewardGUI(plugin, manager);
        this.command = new DailyRewardCommand(plugin, manager);
        this.npcListener = new DailyRewardNPCListener(plugin, manager);
    }

    public boolean init() {
        try {
            // --- REMOVED DB CONNECTION/TABLES (Done in Login.java) ---
            // this.database.connect();
            // this.database.createTables();

            // Register Events
            plugin.getServer().getPluginManager().registerEvents(gui, plugin);
            if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                plugin.getServer().getPluginManager().registerEvents(npcListener, plugin);
                plugin.getLogger().info("DailyReward NPC Listener registered.");
            } else {
                plugin.getLogger().warning("Citizens not found, DailyReward NPC click will not work.");
            }

            // Register Command
            plugin.getCommand("dailyreward").setExecutor(command);
            plugin.getCommand("dailyreward").setTabCompleter(command);

            plugin.getLogger().info("DailyRewardModule enabled successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize DailyRewardModule!");
            e.printStackTrace();
            return false;
        }
    }

    public void shutdown() {
        if (database != null) {
            database.disconnect();
        }
        if (logger != null) {
            logger.shutdown();
        }
        plugin.getLogger().info("DailyRewardModule disabled.");
    }

    // Getters if other modules need to interact
    public DailyRewardManager getManager() {
        return manager;
    }

    public DailyRewardDatabase getDatabase() {
        return database;
    }
}