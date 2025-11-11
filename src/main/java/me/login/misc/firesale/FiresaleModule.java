package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.command.FiresaleCommand;
import me.login.misc.firesale.database.FiresaleDatabase;
import me.login.misc.firesale.gui.FiresaleGUI;
import me.login.misc.firesale.item.FiresaleItemManager;

/**
 * Main class to initialize the Firesale system module.
 * This class sets up the database, manager, commands, and listeners.
 */
public class FiresaleModule {

    private final Login plugin;
    private FiresaleDatabase database;
    private FiresaleItemManager itemManager;
    private FiresaleLogger logger;
    private FiresaleManager manager;
    private FiresaleGUI gui;
    private FiresaleListener listener;

    public FiresaleModule(Login plugin) {
        this.plugin = plugin;
    }

    public void init() {
        // Load Config values
        plugin.saveDefaultConfig(); // Ensure config is saved
        int npcId = plugin.getConfig().getInt("firesale.fire-npc-id", -1);
        String logChannelId = plugin.getConfig().getString("firesale.log-channel-id", "");

        if (npcId == -1) {
            plugin.getLogger().warning("Firesale NPC ID ('firesale.fire-npc-id') is not set in config.yml! NPC interaction will not work.");
        }
        if (logChannelId.isEmpty()) {
            plugin.getLogger().warning("Firesale Log Channel ID ('firesale.log-channel-id') is not set in config.yml! Discord logging will be disabled.");
        }

        // Initialize components
        this.database = new FiresaleDatabase(plugin);
        this.database.init();

        this.itemManager = new FiresaleItemManager(plugin);
        this.itemManager.loadItems();

        this.logger = new FiresaleLogger(plugin.getJda(), logChannelId);

        this.manager = new FiresaleManager(plugin, database, logger, itemManager);
        this.manager.loadSales(); // Load active sales from DB
        this.manager.startScheduler(); // Start the main timer task

        this.gui = new FiresaleGUI(plugin, manager, database);

        // Register Command
        FiresaleCommand firesaleCommand = new FiresaleCommand(plugin, manager, itemManager);
        plugin.getCommand("firesale").setExecutor(firesaleCommand);
        plugin.getCommand("firesale").setTabCompleter(firesaleCommand);

        // Register Listeners
        this.listener = new FiresaleListener(plugin, manager, gui, npcId);
        plugin.getServer().getPluginManager().registerEvents(this.listener, plugin);

        plugin.getLogger().info("Firesale module has been enabled.");
    }

    public void disable() {
        if (manager != null) {
            manager.shutdown(); // Stop scheduler
        }
        if (database != null) {
            database.close(); // Close DB connection
        }
        plugin.getLogger().info("Firesale module has been disabled.");
    }
}