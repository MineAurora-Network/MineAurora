package me.login.misc.tokens;

import me.login.Login;
import net.luckperms.api.LuckPerms;

public class TokenModule {
    private final Login plugin;
    private final LuckPerms luckPerms;
    private TokenDatabase tokenDatabase; // Created internally
    private TokenLogger tokenLogger;
    private ItemManager itemManager;
    private TokenManager tokenManager;
    private TokenShopGUI tokenShopGUI;
    private TokenShopNPCListener npcListener;
    private TokenCommands tokenCommands;
    private TokenShopCommand tokenShopCommand;

    // CHANGED: No longer accepts DailyRewardDatabase in constructor
    public TokenModule(Login plugin, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
    }

    public boolean init() {
        try {
            // 1. Initialize Database
            this.tokenDatabase = new TokenDatabase(plugin);
            this.tokenDatabase.connect();
            this.tokenDatabase.createTables();

            // 2. Initialize Logger (shares JDA from LagClearLogger)
            if (plugin.getLagClearLogger() == null || plugin.getLagClearLogger().getJDA() == null) {
                plugin.getLogger().warning("TokenModule: LagClearLogger JDA not ready. Token logging will be disabled.");
                this.tokenLogger = new TokenLogger(plugin, null);
            } else {
                this.tokenLogger = new TokenLogger(plugin, plugin.getLagClearLogger().getJDA());
            }

            // 3. Initialize ItemManager
            this.itemManager = new ItemManager(plugin);

            // 4. Initialize TokenManager (Passes TokenDatabase now)
            this.tokenManager = new TokenManager(plugin, tokenDatabase, tokenLogger, luckPerms, itemManager);

            // 5. Initialize TokenShopGUI
            this.tokenShopGUI = new TokenShopGUI(plugin, tokenManager);
            plugin.getServer().getPluginManager().registerEvents(tokenShopGUI, plugin);

            // 6. Initialize Commands
            this.tokenCommands = new TokenCommands(plugin, tokenManager, tokenShopGUI);
            plugin.getCommand("token").setExecutor(tokenCommands);
            plugin.getCommand("token").setTabCompleter(tokenCommands);

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
        if (tokenDatabase != null) {
            tokenDatabase.disconnect();
        }
        plugin.getLogger().info("TokenModule has been disabled.");
    }

    public TokenManager getTokenManager() {
        return tokenManager;
    }

    // Helper if you need the DB elsewhere, though TokenManager is preferred
    public TokenDatabase getTokenDatabase() {
        return tokenDatabase;
    }
}