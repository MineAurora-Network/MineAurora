package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

public class BanCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationDatabase database;
    private final ModerationLogger logger;

    public BanCommand(Login plugin, ModerationDatabase database, ModerationLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Component prefix = Utils.getServerPrefix(plugin);

        // --- BAN ---
        if (label.equalsIgnoreCase("ban")) {
            if (!sender.hasPermission("staff.ban")) return noPerm(sender);
            if (args.length < 3) return usage(sender, "/ban <player> <duration> <reason>");

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (!Utils.canPunish(sender, target)) {
                sender.sendMessage(Utils.color("&cYou cannot ban this player (Higher rank/Equal rank/Self)."));
                return true;
            }

            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) { sender.sendMessage(Utils.color("&cInvalid duration.")); return true; }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
            String staffName = sender.getName();

            if (database.banPlayer(target.getUniqueId(), target.getName(), staffUUID, staffName, reason, duration)) {
                String durStr = Utils.formatDuration(duration);
                Utils.broadcastToStaff(String.format("&4%s &fwas banned by &4%s&f for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durStr));

                // Log BAN
                logger.log(ModerationLogger.LogType.BAN, "🔨 **BAN** | " + target.getName() + " was banned by " + staffName + " for: `" + reason + "` (" + durStr + ")");

                if (target.isOnline() && target.getPlayer() != null) {
                    Component kickMsg = prefix.append(Component.text("\nYou have been banned.", NamedTextColor.RED))
                            .append(Component.newline())
                            .append(Component.text("Reason: " + reason, NamedTextColor.WHITE))
                            .append(Component.newline())
                            .append(Component.text("Expires: " + durStr, NamedTextColor.WHITE));
                    target.getPlayer().kick(kickMsg);
                }
            }
            return true;
        }

        // --- IP BAN ---
        if (label.equalsIgnoreCase("ipban")) {
            if (!sender.hasPermission("staff.ipban")) return noPerm(sender);
            if (args.length < 3) return usage(sender, "/ipban <player> <duration> <reason>");

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) { sender.sendMessage(Utils.color("&cPlayer must be online to IP Ban.")); return true; }

            if (!Utils.canPunish(sender, target)) {
                sender.sendMessage(Utils.color("&cYou cannot IP-ban this player (Higher rank/Equal rank/Self)."));
                return true;
            }

            String ip = target.getAddress().getAddress().getHostAddress();
            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) { sender.sendMessage(Utils.color("&cInvalid duration.")); return true; }
            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
            String staffName = sender.getName();

            if (database.ipBanPlayer(ip, staffUUID, staffName, reason, duration, target.getUniqueId(), target.getName())) {
                String durStr = Utils.formatDuration(duration);
                Utils.broadcastToStaff(String.format("&4%s &f(IP) was banned by &4%s&f for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durStr));

                // Log IP BAN
                logger.log(ModerationLogger.LogType.BAN, "🔨 **IP-BAN** | " + target.getName() + " was banned by " + staffName + " for: `" + reason + "`");

                Component kickMsg = prefix.append(Component.text("\nYou have been IP-banned.", NamedTextColor.RED));
                target.kick(kickMsg);
            }
            return true;
        }

        // --- UNBAN ---
        if (label.equalsIgnoreCase("unban")) {
            if (!sender.hasPermission("staff.unban")) return noPerm(sender);
            if (args.length < 1) return usage(sender, "/unban <player>");

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (database.unbanPlayer(target.getUniqueId())) {
                Utils.broadcastToStaff(String.format("&a%s &fwas unbanned by &a%s&f.", target.getName(), sender.getName()));
                // Log UNBAN
                logger.log(ModerationLogger.LogType.UNBAN, "🔓 **UNBAN** | " + target.getName() + " was unbanned by " + sender.getName());

                sender.sendMessage(Utils.color("&aUnbanned " + target.getName()));
            } else {
                sender.sendMessage(Utils.color("&cPlayer is not banned."));
            }
            return true;
        }

        // --- UNBAN IP ---
        if (label.equalsIgnoreCase("unbanip")) {
            if (!sender.hasPermission("staff.unbanip")) return noPerm(sender);
            if (args.length < 1) return usage(sender, "/unbanip <ip>");
            if (database.unbanIp(args[0])) {
                Utils.broadcastToStaff(String.format("&aIP &b%s &fwas unbanned by &a%s&f.", args[0], sender.getName()));
                // Log UNBAN IP
                logger.log(ModerationLogger.LogType.UNBAN, "🔓 **UNBAN-IP** | " + args[0] + " was unbanned by " + sender.getName());

                sender.sendMessage(Utils.color("&aUnbanned IP " + args[0]));
            } else {
                sender.sendMessage(Utils.color("&cIP is not banned."));
            }
            return true;
        }

        // --- BAN INFO ---
        if (label.equalsIgnoreCase("baninfo")) {
            if (!sender.hasPermission("staff.baninfo")) return noPerm(sender);
            if (args.length < 1) return usage(sender, "/baninfo <player>");
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            Map<String, Object> info = database.getActiveBanInfo(target.getUniqueId());

            if (info != null) {
                Utils.sendComponent(sender, prefix.append(Component.text("Ban Info for " + target.getName() + ":", NamedTextColor.YELLOW)));
                Utils.sendComponent(sender, Component.text("  Banned by: ", NamedTextColor.GRAY).append(Component.text((String) info.get("staff_name"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Reason: ", NamedTextColor.GRAY).append(Component.text((String) info.get("reason"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Time Left: ", NamedTextColor.GRAY).append(Component.text(Utils.formatTimeLeft((long) info.get("end_time")), NamedTextColor.WHITE)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text(target.getName() + " is not banned.", NamedTextColor.GREEN)));
            }
            return true;
        }

        return false;
    }

    private boolean noPerm(CommandSender s) { s.sendMessage(Utils.color("&cNo permission.")); return true; }
    private boolean usage(CommandSender s, String u) { s.sendMessage(Utils.color("&cUsage: " + u)); return true; }
}