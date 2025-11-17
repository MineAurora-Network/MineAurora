package me.login.misc.hologram;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.stream.Collectors;

public class HologramCommand implements CommandExecutor, TabCompleter {

    private final HologramModule module;
    private final HologramManager manager;
    private final String prefix;

    public HologramCommand(HologramModule module) {
        this.module = module;
        this.manager = module.getHologramManager();
        this.prefix = module.getPlugin().getConfig().getString("server_prefix", "<#2EFFF0>Mine<#2E75F0>Aurora <#F02E2E>»");
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mineaurora.admin.hologram")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <red>You do not have permission."));
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String holoName = args[1];

        if (!manager.getHologramNames().contains(holoName)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <red>Hologram '" + holoName + "' not found in config.yml."));
            return true;
        }

        switch (subCommand) {
            case "spawn":
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <red>This command can only be run by a player."));
                    return true;
                }
                manager.spawnHologram(holoName, player.getLocation());
                sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <green>Spawned hologram '" + holoName + "' at your location."));
                break;

            case "reload":
                manager.reloadHologram(holoName);
                sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <green>Reloaded hologram '" + holoName + "'."));
                break;

            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + " <gray>Usage: /hologram <spawn|reload> <hologram_name>"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("mineaurora.admin.hologram")) {
            return List.of();
        }

        if (args.length == 1) {
            return List.of("spawn", "reload").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2) {
            return manager.getHologramNames().stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        return List.of();
    }
}