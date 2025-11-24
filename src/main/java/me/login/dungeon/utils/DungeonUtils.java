package me.login.dungeon.utils;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;

public class DungeonUtils {

    private static final MiniMessage mm = MiniMessage.miniMessage();

    public static void msg(CommandSender sender, String message) {
        sender.sendMessage(mm.deserialize("<gradient:#00aaff:#00ffaa><b>Dungeon</b></gradient> <dark_gray>»</dark_gray> <gray>" + message));
    }

    public static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text("Dungeon » " + message, NamedTextColor.RED));
    }

    public static Location parseLocation(CommandSender sender, String[] args, int startIndex) {
        try {
            double x = Double.parseDouble(args[startIndex]);
            double y = Double.parseDouble(args[startIndex + 1]);
            double z = Double.parseDouble(args[startIndex + 2]);
            if (sender instanceof org.bukkit.entity.Player) {
                return new Location(((org.bukkit.entity.Player) sender).getWorld(), x, y, z);
            }
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    public static TextColor bukkitToTextColor(org.bukkit.Color color) {
        return TextColor.color(color.asRGB());
    }
}