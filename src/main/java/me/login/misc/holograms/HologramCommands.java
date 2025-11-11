package me.login.misc.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the /commandhologram command to create, kill, and reload holograms.
 */
public class HologramCommands implements CommandExecutor, TabCompleter { // --- MODIFIED ---

    private final HologramManager hologramManager;
    private final HologramConfig hologramConfig;

    public HologramCommands(HologramManager hologramManager, HologramConfig hologramConfig) {
        this.hologramManager = hologramManager;
        this.hologramConfig = hologramConfig;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                if (!sender.hasPermission("mineaurora.command.hologram.create")) {
                    sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command can only be run by a player.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /commandhologram create <name>", NamedTextColor.RED));
                    return true;
                }
                handleCreate(player, args[1]);
                break;

            case "killall":
                if (!sender.hasPermission("mineaurora.command.hologram.killall")) {
                    sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                handleKillAll(sender);
                break;

            case "reload":
                if (!sender.hasPermission("mineaurora.command.hologram.reload")) {
                    sender.sendMessage(Component.text("You do not have permission to use this command.", NamedTextColor.RED));
                    return true;
                }
                handleReload(sender);
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void handleCreate(Player player, String hologramName) {
        hologramName = hologramName.toLowerCase();
        if (!hologramConfig.hasHologram(hologramName)) {
            player.sendMessage(Component.text("A hologram with that name doesn't exist in config.yml.", NamedTextColor.RED));
            return;
        }

        // Create the hologram at the player's location
        hologramManager.createHologram(hologramName, player.getLocation());
        player.sendMessage(Component.text("Hologram '" + hologramName + "' created.", NamedTextColor.GREEN));
    }

    private void handleKillAll(CommandSender sender) {
        hologramManager.killAllHolograms();
        sender.sendMessage(Component.text("All command holograms have been killed and removed from storage.", NamedTextColor.GREEN));
    }

    private void handleReload(CommandSender sender) {
        hologramManager.reloadAllHolograms();
        sender.sendMessage(Component.text("Hologram config reloaded and all holograms respawned.", NamedTextColor.GREEN));
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("--- Command Hologram Help ---", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("/commandhologram create <name>", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/commandhologram killall", NamedTextColor.GRAY));
        sender.sendMessage(Component.text("/commandhologram reload", NamedTextColor.GRAY));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(List.of("create", "killall", "reload"));
            return subCommands.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            // Suggest hologram names from config
            return hologramConfig.getHologramNames().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}