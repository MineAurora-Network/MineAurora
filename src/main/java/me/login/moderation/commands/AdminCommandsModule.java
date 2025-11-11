package me.login.moderation.commands;

import me.login.Login;
import me.login.moderation.commands.util.TimeUtil;
import org.bukkit.command.PluginCommand;

public class AdminCommandsModule {

    private final Login plugin;
    private final MaintenanceManager maintenanceManager;
    private final WorldManager worldManager;
    private final RestartManager restartManager;
    private final AdminCommandListener listener;

    public AdminCommandsModule(Login plugin) {
        this.plugin = plugin;
        this.maintenanceManager = new MaintenanceManager(plugin);
        this.worldManager = new WorldManager(plugin);
        this.restartManager = new RestartManager(plugin);
        this.listener = new AdminCommandListener(this.maintenanceManager, this.worldManager);
    }

    /**
     * Call this in your main plugin's onEnable()
     */
    public void enable() {
        // Register Listeners
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);

        // Register Commands
        registerCommand("schedule", new ScheduleCommand(this));
        registerCommand("maintenance", new MaintenanceCommand(maintenanceManager));
        registerCommand("worldmanager", new WorldManagerCommand(worldManager));

        plugin.getLogger().info("AdminCommandsModule enabled.");
    }

    /**
     * Call this in your main plugin's onDisable()
     */
    public void disable() {
        // Cancel any pending tasks
        if (maintenanceManager != null) {
            maintenanceManager.cancelScheduledMaintenance();
        }
        if (restartManager != null) {
            restartManager.cancelScheduledRestart();
        }
        plugin.getLogger().info("AdminCommandsModule disabled, tasks cancelled.");
    }

    private void registerCommand(String name, org.bukkit.command.CommandExecutor executor) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(executor);

            // Set TabCompleter if the executor implements it (ScheduleCommand does)
            if (executor instanceof org.bukkit.command.TabCompleter) {
                command.setTabCompleter((org.bukkit.command.TabCompleter) executor);
            }
        } else {
            plugin.getLogger().warning("Failed to register command '" + name + "'. Is it in your plugin.yml?");
        }
    }

    // Getters for commands
    public MaintenanceManager getMaintenanceManager() {
        return maintenanceManager;
    }

    public WorldManager getWorldManager() {
        return worldManager;
    }

    public RestartManager getRestartManager() {
        return restartManager;
    }

    public Login getPlugin() {
        return plugin;
    }
}