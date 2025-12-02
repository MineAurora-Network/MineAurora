package me.login.moderation.staff.commands;

import me.login.moderation.Utils;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffCommand implements CommandExecutor {

    private final StaffManager manager;

    public StaffCommand(StaffManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!sender.hasPermission("staff.staff")) {
            sender.sendMessage(Utils.color("&cNo permission."));
            return true;
        }

        Player player = (Player) sender;
        manager.toggleNotifications(player.getUniqueId());

        boolean enabled = manager.isNotificationEnabled(player.getUniqueId());
        if (enabled) {
            player.sendMessage(Component.text("Staff notifications enabled.", NamedTextColor.GREEN));
        } else {
            player.sendMessage(Component.text("Staff notifications disabled.", NamedTextColor.RED));
        }
        return true;
    }
}