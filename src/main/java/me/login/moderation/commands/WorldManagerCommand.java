package me.login.moderation.commands;

import me.login.Login;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class WorldManagerCommand implements CommandExecutor, TabCompleter {

    private final WorldManager worldManager;
    private final Login plugin;

    public WorldManagerCommand(WorldManager worldManager) {
        this.worldManager = worldManager;
        this.plugin = worldManager.getPlugin(); // Get plugin from manager
    }

    /**
     * Helper to send a prefixed message
     */
    private void sendPrefixedMessage(CommandSender sender, String message) {
        sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message));
    }

    /**
     * Helper to get the plugin's prefix and serializer
     */
    private Component getPrefixedComponent(String message) {
        return plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("login.admin.worldmanager")) {
            sendPrefixedMessage(sender, "<red>You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String worldName = args[1];

        if (Bukkit.getWorld(worldName) == null) {
            sendPrefixedMessage(sender, "<red>World '" + worldName + "' does not exist.");
            return true;
        }

        switch (subCommand) {
            case "lock":
                if (worldManager.lockWorld(worldName)) {
                    sendPrefixedMessage(sender, "<green>World '" + worldName + "' has been <red>locked</red>.");
                    Bukkit.broadcast(getPrefixedComponent("<gold>World " + worldName + " has been locked by an admin."), "login.admin.worldmanager.notify");
                } else {
                    sendPrefixedMessage(sender, "<red>Failed to lock world (does it exist?).");
                }
                break;
            case "unlock":
                if (worldManager.unlockWorld(worldName)) {
                    sendPrefixedMessage(sender, "<green>World '" + worldName + "' has been unlocked.");
                    Bukkit.broadcast(getPrefixedComponent("<gold>World " + worldName + " has been unlocked."), "login.admin.worldmanager.notify");
                } else {
                    sendPrefixedMessage(sender, "<yellow>World '" + worldName + "' was not locked.");
                }
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sendPrefixedMessage(sender, "<red>Usage: /worldmanager <lock|unlock> <worldname>");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return List.of("lock", "unlock").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Bukkit.getWorlds().stream()
                    .map(World::getName)
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}