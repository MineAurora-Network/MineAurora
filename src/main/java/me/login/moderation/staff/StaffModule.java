package me.login.moderation.staff;

import me.login.Login;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.staff.commands.*;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;

public class StaffModule {

    private final Login plugin;
    private final ModerationDatabase database;
    private StaffManager manager;
    private StaffLogger logger;

    public StaffModule(Login plugin, ModerationDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    public void enable() {
        plugin.getLogger().info("Initializing StaffModule...");

        this.logger = new StaffLogger(plugin);
        this.manager = new StaffManager(plugin);

        // Register events and start the vanish task
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
        manager.init(); // <--- CRITICAL: Starts the task to keep players hidden

        registerCommand("staff", new StaffCommand(manager));
        registerCommand("staffchat", new StaffChatCommand(plugin, manager, logger));
        registerCommand("maintenance", new MaintenanceCommand(plugin, manager, logger));
        registerCommand("worldmaintenance", new WorldMaintenanceCommand(plugin, manager, logger));
        registerCommand("schedulerestart", new ScheduleRestartCommand(plugin, logger));
        registerCommand("vanish", new VanishCommand(manager, logger));

        ReportCommand reportCmd = new ReportCommand(plugin, database, manager, logger);
        registerCommand("report", reportCmd);
        registerCommand("reportlist", reportCmd);
        registerCommand("reportclear", reportCmd);
    }

    public void disable() {
        if (manager != null) {
            manager.shutdown(); // Stop the task
        }
    }

    private void registerCommand(String name, CommandExecutor executor) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
        } else {
            plugin.getLogger().warning("Failed to register staff command: " + name);
        }
    }
}