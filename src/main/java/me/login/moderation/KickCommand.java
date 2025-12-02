package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;

public class KickCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationLogger logger;

    public KickCommand(Login plugin, ModerationLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.kick")) {
            sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
            return true;
        }

        if (args.length < 2) {
            sender.sendMessage(Utils.color("&cUsage: /kick <player> <reason>"));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null) {
            sender.sendMessage(Utils.color("&cPlayer not found or not online."));
            return true;
        }

        if (!Utils.canPunish(sender, target)) {
            sender.sendMessage(Utils.color("&cYou cannot kick this player (Higher rank/Equal rank/Self)."));
            return true;
        }

        String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Component prefix = Utils.getServerPrefix(plugin);

        Component kickMessage = prefix.append(Component.text("\nYou have been kicked from the server.", NamedTextColor.RED))
                .append(Component.newline())
                .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)));

        target.kick(kickMessage);

        String staffName = sender.getName();
        String logMsg = String.format("&e%s &fwas kicked by &e%s&f for &e%s", target.getName(), staffName, reason);
        Utils.broadcastToStaff(logMsg);

        // Log KICK
        logger.log(ModerationLogger.LogType.KICK, "👢 **KICK** | " + target.getName() + " was kicked by " + staffName + " for: `" + reason + "`");

        sender.sendMessage(Utils.color("&aYou kicked " + target.getName() + "."));
        return true;
    }
}