package me.login.lifesteal;

import me.login.Login;
import me.login.lifesteal.prestige.HeartPrestigeGUI;
import me.login.lifesteal.prestige.HeartPrestigeLogger;
import me.login.lifesteal.prestige.HeartPrestigeManager;
import me.login.lifesteal.prestige.HeartPrestigeNPCListener;
import net.dv8tion.jda.api.JDA;
import net.luckperms.api.LuckPerms;
import org.bukkit.command.PluginCommand;

public class LifestealModule {

    private final Login plugin;
    private final LuckPerms luckPermsApi;
    private final LifestealLogger logger;

    // Components
    private DatabaseManager databaseManager;
    private ItemManager itemManager;
    private LifestealManager lifestealManager;
    private DeadPlayerManager deadPlayerManager;
    private ReviveMenu reviveMenu;
    private CombatLogManager combatLogManager;
    private LifestealCommands lifestealCommands;
    private LifestealListener lifestealListener;

    // Prestige Components
    private HeartPrestigeManager prestigeManager;
    private HeartPrestigeGUI prestigeGUI;
    private HeartPrestigeLogger prestigeLogger;
    private HeartPrestigeNPCListener prestigeNPCListener;

    public LifestealModule(Login plugin, LuckPerms luckPermsApi, LifestealLogger logger) {
        this.plugin = plugin;
        this.luckPermsApi = luckPermsApi;
        this.logger = logger;
    }

    public boolean init() {
        plugin.getLogger().info("Initializing Lifesteal Module...");

        // 1. Database
        this.databaseManager = new DatabaseManager(plugin);
        if (!this.databaseManager.initializeDatabase()) {
            plugin.getLogger().severe("Failed to initialize Lifesteal database!");
            return false;
        }
        this.databaseManager.createTables();

        // 2. Items
        this.itemManager = new ItemManager(plugin);

        // 3. Manager
        this.lifestealManager = new LifestealManager(plugin, itemManager, databaseManager, logger);

        // 4. Dead Players
        this.deadPlayerManager = new DeadPlayerManager(plugin, databaseManager);

        // 5. Menus & Listeners
        this.reviveMenu = new ReviveMenu(plugin, itemManager, deadPlayerManager, logger);
        this.combatLogManager = new CombatLogManager(plugin, itemManager, lifestealManager, deadPlayerManager, logger);
        this.lifestealCommands = new LifestealCommands(plugin, itemManager, lifestealManager, deadPlayerManager, luckPermsApi, logger);
        this.lifestealListener = new LifestealListener(plugin, itemManager, lifestealManager, deadPlayerManager, reviveMenu, logger);

        // --- PRESTIGE INIT ---
        JDA jda = (logger != null) ? plugin.getJda() : null; // Get JDA safely
        this.prestigeLogger = new HeartPrestigeLogger(plugin, jda);
        this.prestigeManager = new HeartPrestigeManager(plugin, lifestealManager, itemManager, prestigeLogger);
        this.prestigeGUI = new HeartPrestigeGUI(plugin, prestigeManager, itemManager);
        this.prestigeNPCListener = new HeartPrestigeNPCListener(plugin, prestigeGUI);

        plugin.getServer().getPluginManager().registerEvents(prestigeGUI, plugin);
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            plugin.getServer().getPluginManager().registerEvents(prestigeNPCListener, plugin);
        } else {
            plugin.getLogger().warning("Citizens not found - Prestige NPC will not work.");
        }
        // ---------------------

        registerListeners();
        registerCommands();

        plugin.getLogger().info("Lifesteal Module enabled successfully.");
        return true;
    }

    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(lifestealListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(combatLogManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(reviveMenu, plugin);
    }

    private void registerCommands() {
        PluginCommand withdrawCmd = plugin.getCommand("withdrawhearts");
        if (withdrawCmd != null) {
            withdrawCmd.setExecutor(lifestealCommands);
        }
        PluginCommand lifestealCmd = plugin.getCommand("lifesteal");
        if (lifestealCmd != null) {
            lifestealCmd.setExecutor(lifestealCommands);
            lifestealCmd.setTabCompleter(lifestealCommands);
        }
    }

    public void shutdown() {
        if (lifestealManager != null) {
            lifestealManager.saveAllOnlinePlayerData();
        }
        if (combatLogManager != null) {
            combatLogManager.shutdown();
        }
        if (databaseManager != null) {
            databaseManager.closeConnection();
        }
    }

    // Getters
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ItemManager getItemManager() { return itemManager; }
    public LifestealManager getLifestealManager() { return lifestealManager; }
    public DeadPlayerManager getDeadPlayerManager() { return deadPlayerManager; }
    public CombatLogManager getCombatLogManager() { return combatLogManager; }
}