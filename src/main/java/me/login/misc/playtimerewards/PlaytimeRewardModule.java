package me.login.misc.playtimerewards;

import me.login.Login;
import me.login.misc.tokens.TokenManager;
import net.milkbowl.vault.economy.Economy;

/**
 * Initializes all components for the Playtime Rewards feature.
 */
public class PlaytimeRewardModule {

    private final Login plugin;
    private final Economy economy;
    private final TokenManager tokenManager;
    private PlaytimeRewardDatabase database;
    private PlaytimeRewardLogger logger;
    private PlaytimeRewardManager manager;
    private PlaytimeRewardGUI gui;
    private PlaytimeRewardCommand command;
    private PlaytimeRewardNPCListener npcListener;

    public PlaytimeRewardModule(Login plugin, Economy economy, TokenManager tokenManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.tokenManager = tokenManager;
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

            // 3. Verify TokenManager (was previously DailyRewardDatabase check)
            if (tokenManager == null) {
                plugin.getLogger().severe("TokenManager is not initialized! PlaytimeReward token rewards will fail.");
                return false;
            }

            // 4. Initialize Manager (Core Logic) - passing tokenManager now
            this.manager = new PlaytimeRewardManager(plugin, database, logger, economy, tokenManager);
            plugin.getServer().getPluginManager().registerEvents(manager, plugin);

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
            // FIX: Use the synchronous save method to prevent "Plugin disabled" scheduler error
            manager.saveAllPlayerDataSync();
        }
        if (database != null) {
            database.disconnect();
        }
        plugin.getLogger().info("PlaytimeRewardModule has been disabled.");
    }
}