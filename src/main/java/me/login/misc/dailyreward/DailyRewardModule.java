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
        this.plugin = plugin;
        this.database = database;
        this.logger = new DailyRewardLogger(plugin);
        this.manager = new DailyRewardManager(plugin, database, logger, economy, tokenManager);

        // Initialize GUI once
        this.gui = new DailyRewardGUI(plugin, manager);

        // Pass singleton GUI to command
        this.command = new DailyRewardCommand(plugin, manager, gui);

        // Pass singleton GUI to NPC listener
        this.npcListener = new DailyRewardNPCListener(plugin, manager, gui);
    }

    public boolean init() {
        try {
            // Register GUI Events ONCE
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

    public DailyRewardManager getManager() {
        return manager;
    }

    public DailyRewardDatabase getDatabase() {
        return database;
    }
}