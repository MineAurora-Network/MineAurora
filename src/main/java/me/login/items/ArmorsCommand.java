package me.login.items;

import me.login.Login;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ArmorsCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final ArmorManager armorManager;
    private final ArmorLogger armorLogger;

    public ArmorsCommand(Login plugin, ArmorManager armorManager, ArmorLogger armorLogger) {
        this.plugin = plugin;
        this.armorManager = armorManager;
        this.armorLogger = armorLogger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mineaurora.armor.give")) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>You do not have permission."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /armourgive <piece_name> [player]"));
            return true;
        }

        String pieceName = args[0];
        Player target;

        if (args.length >= 2) {
            target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Player not found."));
                return true;
            }
        } else {
            if (!(sender instanceof Player)) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Console must specify a player."));
                return true;
            }
            target = (Player) sender;
        }

        if (!armorManager.exists(pieceName)) {
            sender.sendMessage(MiniMessage.miniMessage().deserialize("<red>Armor piece '" + pieceName + "' does not exist in items.yml."));
            return true;
        }

        ItemStack armorPiece = armorManager.getArmorPiece(pieceName);
        if (armorPiece != null) {
            target.getInventory().addItem(armorPiece);
            String prefix = plugin.getConfig().getString("armor-system.prefix", "<gradient:#FF5555:#AA0000><b>ARMOR</b></gradient> <dark_gray>» <gray>");
            target.sendMessage(MiniMessage.miniMessage().deserialize(prefix + "Received <green>" + pieceName + "<gray>."));
            if (!target.equals(sender)) {
                sender.sendMessage(MiniMessage.miniMessage().deserialize(prefix + "Gave <green>" + pieceName + " <gray>to <yellow>" + target.getName() + "<gray>."));
            }

            String logMsg = "**Admin Action:** Armor Give\n**Admin:** " + sender.getName() + "\n**Target:** " + target.getName() + "\n**Piece:** " + pieceName;
            armorLogger.log(logMsg);
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mineaurora.armor.give")) return List.of();

        if (args.length == 1) {
            return armorManager.getArmorNames().stream()
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return null; // Return null to suggest online players
        }
        return List.of();
    }
}