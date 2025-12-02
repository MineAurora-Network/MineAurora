package me.login.moderation.staff.commands;

import me.login.Login;
import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class StaffChatCommand implements CommandExecutor {

    private final Login plugin;
    private final StaffManager manager;
    private final StaffLogger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public StaffChatCommand(Login plugin, StaffManager manager, StaffLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.chat")) {
            sender.sendMessage(Utils.color("&cNo permission."));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Utils.color("&cUsage: /sc <message>"));
            return true;
        }

        String message = String.join(" ", args);
        String senderName = sender.getName();

        // Format: [SC] Player: Message
        Component format = mm.deserialize("<dark_gray>[<aqua>SC</aqua>]</dark_gray> <yellow>" + senderName + "</yellow><gray>:</gray> <white>" + message + "</white>");

        manager.broadcastToStaff(format, "staff.chat");

        // Log
        logger.log(StaffLogger.LogType.CHAT, "**[SC]** " + senderName + ": " + message);

        // Also send to console if sender is player
        if (sender instanceof Player) {
            plugin.getLogger().info("[StaffChat] " + senderName + ": " + message);
        } else {
            sender.sendMessage(format); // Send back to console
        }

        return true;
    }
}