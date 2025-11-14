package me.login.pets;

import me.login.Login;
import me.login.pets.data.PetsDatabase;
import me.login.pets.gui.PetGuiListener;
import me.login.pets.listeners.CaptureListener;
import me.login.pets.listeners.PetDataListener;
import me.login.pets.listeners.PetProtectionListener;
import org.bukkit.Bukkit;

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

    // Listeners
    private PetDataListener petDataListener;
    private CaptureListener captureListener;
    private PetProtectionListener petProtectionListener;
    private PetGuiListener petGuiListener;

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

        // 4. Core Manager
        this.petManager = new PetManager(plugin, petsDatabase, petsConfig, messageHandler, petsLogger);

        // 5. Listeners
        this.petDataListener = new PetDataListener(petManager, petsDatabase);
        this.captureListener = new CaptureListener(petManager, petsConfig, messageHandler, petsLogger);

        // --- THIS IS THE FIX ---
        // Added messageHandler to the constructor so it can send messages
        this.petProtectionListener = new PetProtectionListener(petManager, petsConfig, messageHandler);
        // --- END FIX ---

        this.petGuiListener = new PetGuiListener(petManager, messageHandler);

        // 6. Commands
        this.petCommand = new PetCommand(plugin, petManager, messageHandler, petsLogger, petsConfig);

        // 7. Register
        plugin.getServer().getPluginManager().registerEvents(petDataListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(captureListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petProtectionListener, plugin);
        plugin.getServer().getPluginManager().registerEvents(petGuiListener, plugin);

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
}