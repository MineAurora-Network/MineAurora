package me.login.pets;

import me.login.Login;
import me.login.pets.data.PetsDatabase;
import me.login.pets.gui.PetGuiListener;
import me.login.pets.listeners.*;

public class PetsModule {

    private final Login plugin;
    private PetsConfig petsConfig;
    private PetsDatabase petsDatabase;
    private PetManager petManager;
    private PetMessageHandler messageHandler;
    private PetsLogger petsLogger;
    private PetCommand petCommand;
    private PetItemManager petItemManager;

    // Listeners
    private PetDataListener petDataListener;
    private PetGuiListener petGuiListener;
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

        this.petsConfig = new PetsConfig(plugin);
        petsConfig.loadConfig();

        this.messageHandler = new PetMessageHandler(plugin, petsConfig);
        this.petsLogger = sharedLogger;
        if (this.petsLogger == null) {
            this.petsLogger = new PetsLogger(plugin);
        }
        petsLogger.loadConfig();

        this.petsDatabase = new PetsDatabase(plugin);
        if (!petsDatabase.connect()) {
            plugin.getLogger().severe("Failed to connect to Pets database!");
            return false;
        }

        this.petItemManager = new PetItemManager(plugin);
        this.petManager = new PetManager(plugin, petsDatabase, petsConfig, messageHandler, petsLogger);

        this.petDataListener = new PetDataListener(petManager, petsDatabase);
        this.petGuiListener = new PetGuiListener(petManager, messageHandler);
        this.petInventoryListener = new PetInventoryListener(plugin, petManager, petsConfig);
        this.petInteractListener = new PetInteractListener(petManager, messageHandler, petInventoryListener);
        this.petCombatListener = new PetCombatListener(petManager, petsConfig, messageHandler);
        this.petProtectionListener = new PetProtectionListener(petManager);
        this.petPlacementListener = new PetPlacementListener(plugin);

        this.petCommand = new PetCommand(plugin, petManager, messageHandler, petsLogger, petsConfig, petItemManager, petInventoryListener);

        plugin.getServer().getPluginManager().registerEvents(petDataListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petGuiListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petInteractListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petCombatListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petProtectionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petInventoryListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petPlacementListener, plugin);

        // --- REMOVED: petHelmetGuiListener ---

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

    public PetManager getPetManager() { return petManager; }
    public PetsConfig getPetsConfig() { return petsConfig; }
    public PetMessageHandler getMessageHandler() { return messageHandler; }
    public PetsLogger getPetsLogger() { return petsLogger; }
    public PetItemManager getPetItemManager() { return petItemManager; }
}