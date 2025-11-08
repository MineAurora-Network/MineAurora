package me.login.misc.playtimerewards;

import me.login.Login;
import me.login.misc.dailyreward.DailyRewardDatabase; // This import is now used
import net.milkbowl.vault.economy.Economy;
import org.bukkit.command.TabCompleter;

/**
 * Initializes all components for the Playtime Rewards feature.
 */
public class PlaytimeRewardModule {

    private final Login plugin;
    private final Economy economy;
    private PlaytimeRewardDatabase database;
    private PlaytimeRewardLogger logger;
    private PlaytimeRewardManager manager;
    private PlaytimeRewardGUI gui;
    private PlaytimeRewardCommand command;
    private PlaytimeRewardNPCListener npcListener;

    // --- ADDED FIELD ---
    private final DailyRewardDatabase dailyRewardDatabase;

    public PlaytimeRewardModule(Login plugin, Economy economy, DailyRewardDatabase dailyRewardDatabase) {
        this.plugin = plugin;
        this.economy = economy;
        this.dailyRewardDatabase = dailyRewardDatabase; // Store this
    }

    public boolean init() {
        try {
            // 1. Initialize Database
            this.database = new PlaytimeRewardDatabase(plugin);
            this.database.connect();
            this.database.createTables();

            // 2. Initialize Logger (shares JDA)
            if (plugin.getLagClearLogger() == null || plugin.getLagClearLogger().getJDA() == null) {
                plugin.getLogger().warning("LagClearLogger JDA not ready. PlaytimeReward logging will be disabled.");
                this.logger = new PlaytimeRewardLogger(plugin, null); // Run without logging
            } else {
                this.logger = new PlaytimeRewardLogger(plugin, plugin.getLagClearLogger().getJDA());
            }

            // 3. Get DailyRewardDatabase for token management
            // This is now passed in the constructor
            if (dailyRewardDatabase == null) { // This will now correctly check the field
                plugin.getLogger().severe("DailyRewardDatabase is not initialized! PlaytimeReward token rewards will fail.");
                return false;
            }

            // 4. Initialize Manager (Core Logic)
            this.manager = new PlaytimeRewardManager(plugin, database, logger, economy, dailyRewardDatabase); // Pass it here
            plugin.getServer().getPluginManager().registerEvents(manager, plugin); // Registers Join/Quit for playtime tracking

            // 5. Initialize GUI
            this.gui = new PlaytimeRewardGUI(plugin, manager);
            plugin.getServer().getPluginManager().registerEvents(gui, plugin);

            // 6. Initialize Command
            this.command = new PlaytimeRewardCommand(gui, manager);
            plugin.getCommand("playtimerewards").setExecutor(command);
            plugin.getCommand("playtimerewards").setAliases(java.util.Arrays.asList("ptrewards"));

            // 7. Initialize NPC Listener
            if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
                this.npcListener = new PlaytimeRewardNPCListener(plugin, gui, manager);
                plugin.getServer().getPluginManager().registerEvents(npcListener, plugin);
                plugin.getLogger().info("PlaytimeRewards: Citizens NPC Listener registered.");
            } else {
                plugin.getLogger().warning("PlaytimeRewards: Citizens not found, NPC trigger will not work.");
            }

            plugin.getLogger().info("PlaytimeRewardModule has been enabled successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize PlaytimeRewardModule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void shutdown() {
        if (manager != null) {
            manager.saveAllPlayerData();
        }
        if (database != null) {
            database.disconnect();
        }
        plugin.getLogger().info("PlaytimeRewardModule has been disabled.");
    }
}