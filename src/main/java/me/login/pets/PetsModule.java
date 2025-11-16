package me.login.pets;

import me.login.Login;
import me.login.pets.data.PetsDatabase;
import me.login.pets.gui.PetGuiListener;
import me.login.pets.listeners.*; // Import all listeners

/**
 * Main module class for the Pet system.
 * Initializes all managers, listeners, and commands.
 */
public class PetsModule {

    private final Login plugin;
    private PetsConfig petsConfig;
    private PetsDatabase petsDatabase;
    private PetManager petManager;
    private PetMessageHandler messageHandler;
    private PetsLogger petsLogger;
    private PetCommand petCommand;

    // --- NEW: Pet Item Manager ---
    private PetItemManager petItemManager;

    // Listeners
    private PetDataListener petDataListener;
    private PetGuiListener petGuiListener;

    // --- NEW Listeners ---
    private PetInteractListener petInteractListener;
    private PetCombatListener petCombatListener;
    private PetProtectionListener petProtectionListener;
    private PetInventoryListener petInventoryListener;
    private PetPlacementListener petPlacementListener;


    public PetsModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init(PetsLogger sharedLogger) {
        plugin.getLogger().info("Initializing Pets Module...");

        // 1. Config
        this.petsConfig = new PetsConfig(plugin);
        petsConfig.loadConfig();

        // 2. Messaging & Logging
        this.messageHandler = new PetMessageHandler(plugin, petsConfig);
        this.petsLogger = sharedLogger; // Use the shared logger from Login
        if (this.petsLogger == null) {
            plugin.getLogger().warning("PetsModule received a null logger! Creating a fallback.");
            this.petsLogger = new PetsLogger(plugin); // Fallback if something went wrong
        }
        petsLogger.loadConfig();

        // 3. Database
        this.petsDatabase = new PetsDatabase(plugin);
        if (!petsDatabase.connect()) {
            plugin.getLogger().severe("Failed to connect to Pets database!");
            return false;
        }

        // --- NEW: 4. Pet Item Manager (must be before PetManager) ---
        this.petItemManager = new PetItemManager(plugin);

        // 5. Core Manager
        this.petManager = new PetManager(plugin, petsDatabase, petsConfig, messageHandler, petsLogger);

        // 6. Listeners
        this.petDataListener = new PetDataListener(petManager, petsDatabase);
        this.petGuiListener = new PetGuiListener(petManager, messageHandler);

        // --- NEW Listeners Initialized ---
        this.petInventoryListener = new PetInventoryListener(plugin, petManager, petsConfig);
        this.petInteractListener = new PetInteractListener(petManager, messageHandler, petInventoryListener);
        this.petCombatListener = new PetCombatListener(petManager, petsConfig, messageHandler);
        this.petProtectionListener = new PetProtectionListener(petManager);
        this.petPlacementListener = new PetPlacementListener(plugin);


        // 7. Commands
        this.petCommand = new PetCommand(plugin, petManager, messageHandler, petsLogger, petsConfig, petItemManager, petInventoryListener);

        // 8. Register
        plugin.getServer().getPluginManager().registerEvents(petDataListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petGuiListener, plugin);

        // --- NEW Listeners Registered ---
        plugin.getServer().getPluginManager().registerEvents(petInteractListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petCombatListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petProtectionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petInventoryListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petPlacementListener, plugin);

        // Note: CaptureListener is removed, its logic is now in PetInteractListener

        plugin.getCommand("pet").setExecutor(petCommand);
        plugin.getCommand("pet").setTabCompleter(petCommand);

        plugin.getLogger().info("Pets Module initialized successfully.");
        return true;
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down Pets Module...");
        if (petManager != null) {
            petManager.despawnAllActivePets();
        }
        if (petsDatabase != null) {
            petsDatabase.disconnect();
        }
    }

    // Getters for other modules if needed
    public PetManager getPetManager() {
        return petManager;
    }

    public PetsConfig getPetsConfig() {
        return petsConfig;
    }

    public PetMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public PetsLogger getPetsLogger() {
        return petsLogger;
    }

    // --- NEW Getter ---
    public PetItemManager getPetItemManager() {
        return petItemManager;
    }
}