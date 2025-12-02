package me.login.moderation.staff.commands;

import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VanishCommand implements CommandExecutor {

    private final StaffManager manager;
    private final StaffLogger logger;

    public VanishCommand(StaffManager manager, StaffLogger logger) {
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) { sender.sendMessage("Players only"); return true; }
        if (!sender.hasPermission("staff.command.vanish")) { sender.sendMessage(Utils.color("&cNo permission.")); return true; }

        Player player = (Player) sender;

        // Supports /vanish, /vanish on, /vanish off
        if (args.length > 0) {
            boolean shouldVanish = args[0].equalsIgnoreCase("on");
            boolean currentlyVanished = manager.isVanished(player.getUniqueId());

            if (shouldVanish == currentlyVanished) {
                player.sendMessage(Component.text("You are already " + (currentlyVanished ? "vanished" : "visible"), NamedTextColor.RED));
                return true;
            }
        }

        // Toggle
        manager.toggleVanish(player);

        boolean newState = manager.isVanished(player.getUniqueId());
        logger.log(StaffLogger.LogType.VANISH, player.getName() + " set vanish to **" + (newState ? "ENABLED" : "DISABLED") + "**");

        return true;
    }
}