package me.login.lifesteal;

import me.login.Login;
import net.luckperms.api.LuckPerms;
import org.bukkit.command.PluginCommand;

/**
 * Main handler class for the Lifesteal module.
 * This class initializes, registers, and shuts down all components
 * related to the Lifesteal system to keep the main plugin class clean.
 */
public class LifestealModule {

    private final Login plugin;
    private final LuckPerms luckPermsApi;
    private final LifestealLogger logger; // <-- ADDED

    // All Lifesteal components are now stored here
    private DatabaseManager databaseManager;
    private ItemManager itemManager;
    private LifestealManager lifestealManager;
    private DeadPlayerManager deadPlayerManager;
    private ReviveMenu reviveMenu;
    private CombatLogManager combatLogManager;
    private LifestealCommands lifestealCommands;
    private LifestealListener lifestealListener; // <-- ADDED FIELD

    // --- CONSTRUCTOR UPDATED ---
    public LifestealModule(Login plugin, LuckPerms luckPermsApi, LifestealLogger logger) {
        this.plugin = plugin;
        this.luckPermsApi = luckPermsApi;
        this.logger = logger; // <-- STORE LOGGER
    }

    /**
     * Initializes all Lifesteal components.
     * @return true if initialization was successful, false otherwise.
     */
    public boolean init() {
        plugin.getLogger().info("Initializing Lifesteal Module...");

        // 1. Initialize DatabaseManager
        this.databaseManager = new DatabaseManager(plugin);
        if (!this.databaseManager.initializeDatabase()) {
            plugin.getLogger().severe("Failed to initialize Lifesteal database!");
            return false;
        }
        this.databaseManager.createTables();
        plugin.getLogger().info("Lifesteal DatabaseManager initialized.");

        // 2. Initialize ItemManager (requires main config)
        this.itemManager = new ItemManager(plugin);
        plugin.getLogger().info("Lifesteal ItemManager initialized.");

        // 3. Initialize LifestealManager (now passes logger)
        this.lifestealManager = new LifestealManager(plugin, itemManager, databaseManager, logger);
        plugin.getLogger().info("Lifesteal LifestealManager initialized.");

        // 4. Initialize DeadPlayerManager
        this.deadPlayerManager = new DeadPlayerManager(plugin, databaseManager);
        plugin.getLogger().info("Lifesteal DeadPlayerManager initialized.");

        // 5. Initialize ReviveMenu (now passes logger)
        this.reviveMenu = new ReviveMenu(plugin, itemManager, deadPlayerManager, logger);
        plugin.getLogger().info("Lifesteal ReviveMenu initialized.");

        // 6. Initialize CombatLogManager (now passes logger)
        this.combatLogManager = new CombatLogManager(plugin, itemManager, lifestealManager, deadPlayerManager, logger);
        plugin.getLogger().info("Lifesteal CombatLogManager initialized.");

        // 7. Initialize Commands (now passes logger)
        this.lifestealCommands = new LifestealCommands(plugin, itemManager, lifestealManager, deadPlayerManager, luckPermsApi, logger);
        plugin.getLogger().info("Lifesteal LifestealCommands initialized.");

        // 8. Initialize Listener (now passes logger)
        this.lifestealListener = new LifestealListener(plugin, itemManager, lifestealManager, deadPlayerManager, reviveMenu, logger);
        plugin.getLogger().info("Lifesteal LifestealListener initialized.");

        // After all components are created, register listeners and commands
        registerListeners();
        registerCommands();

        plugin.getLogger().info("Lifesteal Module enabled successfully.");
        return true;
    }

    /**
     * Registers all event listeners for the Lifesteal module.
     */
    private void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(lifestealListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(combatLogManager, plugin);
        plugin.getServer().getPluginManager().registerEvents(reviveMenu, plugin); // <-- ADDED THIS
        plugin.getLogger().info("Lifesteal listeners registered.");
    }

    /**
     * Registers all commands for the Lifesteal module.
     */
    private void registerCommands() {
        PluginCommand withdrawCmd = plugin.getCommand("withdrawhearts");
        if (withdrawCmd != null) {
            withdrawCmd.setExecutor(lifestealCommands);
        } else {
            plugin.getLogger().warning("Could not find 'withdrawhearts' command in plugin.yml!");
        }

        PluginCommand lifestealCmd = plugin.getCommand("lifesteal");
        if (lifestealCmd != null) {
            lifestealCmd.setExecutor(lifestealCommands);
            lifestealCmd.setTabCompleter(lifestealCommands);
        } else {
            plugin.getLogger().warning("Could not find 'lifesteal' command in plugin.yml!");
        }
        plugin.getLogger().info("Lifesteal commands registered.");
    }

    /**
     * Shuts down all Lifesteal components cleanly.
     */
    public void shutdown() {
        if (lifestealManager != null) {
            lifestealManager.saveAllOnlinePlayerData();
            plugin.getLogger().info("Saved all Lifesteal player data.");
        }
        if (combatLogManager != null) {
            combatLogManager.shutdown();
            plugin.getLogger().info("Lifesteal CombatLogManager shut down.");
        }
        // --- REMOVED itemManager.closeWebhook() ---
        if (databaseManager != null) {
            databaseManager.closeConnection();
            plugin.getLogger().info("Lifesteal DatabaseManager connection closed.");
        }
        plugin.getLogger().info("Lifesteal Module shut down.");
    }

    // --- Public Getters ---
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }
    public ItemManager getItemManager() {
        return itemManager;
    }
    public LifestealManager getLifestealManager() {
        return lifestealManager;
    }
    public DeadPlayerManager getDeadPlayerManager() {
        return deadPlayerManager;
    }
    public CombatLogManager getCombatLogManager() {
        return combatLogManager;
    }
}