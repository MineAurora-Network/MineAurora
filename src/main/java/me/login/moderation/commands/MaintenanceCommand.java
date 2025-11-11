package me.login.moderation.commands;

import me.login.Login;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MaintenanceCommand implements CommandExecutor {

    private final MaintenanceManager maintenanceManager;
    private final Login plugin;

    public MaintenanceCommand(MaintenanceManager maintenanceManager) {
        this.maintenanceManager = maintenanceManager;
        this.plugin = maintenanceManager.getPlugin(); // Get plugin from manager
    }

    /**
     * Helper to send a prefixed message
     */
    private void sendPrefixedMessage(CommandSender sender, String message) {
        sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("login.admin.maintenance")) {
            sendPrefixedMessage(sender, "<red>You do not have permission to use this command.");
            return true;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("off")) {
            if (!maintenanceManager.isMaintenanceActive()) {
                sendPrefixedMessage(sender, "<yellow>Maintenance mode is not currently active.");
                maintenanceManager.cancelScheduledMaintenance(); // Also cancel any pending task
                return true;
            }
            maintenanceManager.setMaintenanceMode(false);
            sendPrefixedMessage(sender, "<green>Maintenance mode disabled.");
        } else {
            sendPrefixedMessage(sender, "<red>Usage: /maintenance <off>");
        }
        return true;
    }
}