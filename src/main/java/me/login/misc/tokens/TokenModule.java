package me.login.misc.tokens;

import me.login.Login;
import me.login.misc.dailyreward.DailyRewardDatabase;
import net.luckperms.api.LuckPerms;

/**
 * Initializes all components for the Token system (shop and commands).
 */
public class TokenModule {

    private final Login plugin;
    private final LuckPerms luckPerms;
    private final DailyRewardDatabase tokenDatabase; // Re-use the DB from DailyRewards
    private TokenLogger tokenLogger;
    private ItemManager itemManager;
    private TokenManager tokenManager;
    private TokenShopGUI tokenShopGUI;
    private TokenShopNPCListener npcListener;
    private TokenCommands tokenCommands;
    private TokenShopCommand tokenShopCommand;

    public TokenModule(Login plugin, LuckPerms luckPerms, DailyRewardDatabase tokenDatabase) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.tokenDatabase = tokenDatabase;
    }

    public boolean init() {
        try {
            // 1. Initialize Logger (shares JDA)
            if (plugin.getLagClearLogger() == null || plugin.getLagClearLogger().getJDA() == null) {
                plugin.getLogger().warning("TokenModule: LagClearLogger JDA not ready. Token logging will be disabled.");
                this.tokenLogger = new TokenLogger(plugin, null); // Run without logging
            } else {
                this.tokenLogger = new TokenLogger(plugin, plugin.getLagClearLogger().getJDA());
            }

            // 2. Initialize ItemManager (loads from items.yml)
            this.itemManager = new ItemManager(plugin);

            // 3. Initialize TokenManager (Core Logic)
            this.tokenManager = new TokenManager(plugin, tokenDatabase, tokenLogger, luckPerms, itemManager);

            // 4. Initialize TokenShopGUI
            this.tokenShopGUI = new TokenShopGUI(plugin, tokenManager);
            plugin.getServer().getPluginManager().registerEvents(tokenShopGUI, plugin);

            // 5. Initialize TokenCommands (/token)
            this.tokenCommands = new TokenCommands(plugin, tokenManager, tokenShopGUI);
            plugin.getCommand("token").setExecutor(tokenCommands);
            plugin.getCommand("token").setTabCompleter(tokenCommands);

            // 6. Initialize TokenShopCommand (/tokenshop)
            this.tokenShopCommand = new TokenShopCommand(tokenShopGUI);
            plugin.getCommand("tokenshop").setExecutor(tokenShopCommand);

            // 7. Initialize NPC Listener
            if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
                this.npcListener = new TokenShopNPCListener(plugin, tokenShopGUI);
                plugin.getServer().getPluginManager().registerEvents(npcListener, plugin);
                plugin.getLogger().info("TokenShop: Citizens NPC Listener registered.");
            } else {
                plugin.getLogger().warning("TokenShop: Citizens not found, NPC trigger will not work.");
            }

            plugin.getLogger().info("TokenModule has been enabled successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialize TokenModule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    public void shutdown() {
        // No database to disconnect, as it's managed by DailyRewardModule
        plugin.getLogger().info("TokenModule has been disabled.");
    }
}