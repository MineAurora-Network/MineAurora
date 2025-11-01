//
// File: me/login/moderation/BanCommand.java
// (Updated for MiniMessage, Hierarchy, & Self-Punish)
//
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

    public BanCommand(Login plugin, ModerationDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        Component prefix = Utils.getServerPrefix(plugin); // Get prefix for later use

        // /ban <player> <duration> <reason>
        if (label.equalsIgnoreCase("ban")) {
            if (!sender.hasPermission("staff.ban")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUsage: /ban <player> <duration> <reason>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            // --- Self-punish check ---
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(Utils.color("&cYou cannot ban yourself."));
                return true;
            }

            // --- Hierarchy check ---
            if (sender instanceof Player) {
                int senderWeight = Utils.getLuckPermsRankWeight((Player) sender);
                int targetWeight = Utils.getLuckPermsRankWeight(target);

                if (senderWeight > 0 && senderWeight <= targetWeight) {
                    sender.sendMessage(Utils.color("&cYou cannot punish a player with an equal or higher rank."));
                    return true;
                }
            } // Console bypasses

            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) {
                sender.sendMessage(Utils.color("&cInvalid duration format. Use 'perm' or units like s, m, h, d."));
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"); // Console UUID
            String staffName = sender.getName();

            boolean success = database.banPlayer(target.getUniqueId(), target.getName(), staffUUID, staffName, reason, duration);

            if (success) {
                String durationStr = Utils.formatDuration(duration);
                // Staff broadcast
                String msg = String.format("&4%s &fwas banned by &4%s&f for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durationStr);
                Utils.broadcastToStaff(msg);
                plugin.sendStaffLog(sender.getName() + " banned " + target.getName() + " for " + durationStr + ". Reason: " + reason);

                if (target.isOnline()) {
                    Player targetPlayer = target.getPlayer();
                    // Player kick message uses MiniMessage prefix
                    Component kickMessage = prefix.append(Component.text("\nYou have been banned from this server.", NamedTextColor.RED))
                            .append(Component.newline())
                            .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)))
                            .append(Component.newline())
                            .append(Component.text("Expires in: ", NamedTextColor.RED).append(Component.text(durationStr, NamedTextColor.WHITE)));
                    targetPlayer.kick(kickMessage);
                }
            } else {
                sender.sendMessage(Utils.color("&cAn error occurred while banning the player."));
            }
            return true;
        }

        // /ipban <player> <duration> <reason>
        if (label.equalsIgnoreCase("ipban")) {
            if (!sender.hasPermission("staff.ipban")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUsage: /ipban <player> <duration> <reason>"));
                return true;
            }

            Player target = Bukkit.getPlayer(args[0]);
            if (target == null || !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer must be online to IP ban."));
                return true;
            }

            // --- Self-punish check ---
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(Utils.color("&cYou cannot IP ban yourself."));
                return true;
            }

            // --- Hierarchy check ---
            if (sender instanceof Player) {
                int senderWeight = Utils.getLuckPermsRankWeight((Player) sender);
                int targetWeight = Utils.getLuckPermsRankWeight(target);

                if (senderWeight > 0 && senderWeight <= targetWeight) {
                    sender.sendMessage(Utils.color("&cYou cannot punish a player with an equal or higher rank."));
                    return true;
                }
            } // Console bypasses

            String ipAddress = target.getAddress().getAddress().getHostAddress();
            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) {
                sender.sendMessage(Utils.color("&cInvalid duration format. Use 'perm' or units like s, m, h, d."));
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");
            String staffName = sender.getName();

            boolean success = database.ipBanPlayer(ipAddress, staffUUID, staffName, reason, duration, target.getUniqueId(), target.getName());

            if (success) {
                String durationStr = Utils.formatDuration(duration);
                // Staff broadcast
                String msg = String.format("&4%s &f(IP) was banned by &4%s&f for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durationStr);
                Utils.broadcastToStaff(msg);
                plugin.sendStaffLog(sender.getName() + " IP-banned " + target.getName() + " (" + ipAddress + ") for " + durationStr + ". Reason: " + reason);

                // Player kick message
                Component kickMessage = prefix.append(Component.text("\nYou have been IP-banned from this server.", NamedTextColor.RED))
                        .append(Component.newline())
                        .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)))
                        .append(Component.newline())
                        .append(Component.text("Expires in: ", NamedTextColor.RED).append(Component.text(durationStr, NamedTextColor.WHITE)));
                target.kick(kickMessage);
            } else {
                sender.sendMessage(Utils.color("&cAn error occurred while IP banning the player."));
            }
            return true;
        }

        // /baninfo <player>
        if (label.equalsIgnoreCase("baninfo")) {
            if (!sender.hasPermission("staff.baninfo")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Utils.color("&cUsage: /baninfo <player>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            // Check for UUID ban
            Map<String, Object> info = database.getActiveBanInfo(target.getUniqueId());
            if (info != null) {
                Utils.sendComponent(sender, prefix.append(Component.text("Ban Info for " + target.getName() + ":", NamedTextColor.YELLOW)));
                Utils.sendComponent(sender, Component.text("  Type: ", NamedTextColor.GRAY).append(Component.text("Player Ban", NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Banned by: ", NamedTextColor.GRAY).append(Component.text((String) info.get("staff_name"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Reason: ", NamedTextColor.GRAY).append(Component.text((String) info.get("reason"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Time Left: ", NamedTextColor.GRAY).append(Component.text(Utils.formatTimeLeft((long) info.get("end_time")), NamedTextColor.WHITE)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text("Player " + target.getName() + " is not currently banned by UUID.", NamedTextColor.GREEN)));
            }

            // Check for IP ban (if they were online)
            if (target.isOnline()) {
                String ip = target.getPlayer().getAddress().getAddress().getHostAddress();
                Map<String, Object> ipInfo = database.getActiveIpBanInfo(ip);
                if (ipInfo != null) {
                    Utils.sendComponent(sender, prefix.append(Component.text("IP Ban Info for " + ip + " (Player: " + target.getName() + "):", NamedTextColor.YELLOW)));
                    Utils.sendComponent(sender, Component.text("  Type: ", NamedTextColor.GRAY).append(Component.text("IP Ban", NamedTextColor.WHITE)));
                    Utils.sendComponent(sender, Component.text("  Banned by: ", NamedTextColor.GRAY).append(Component.text((String) ipInfo.get("staff_name"), NamedTextColor.WHITE)));
                    Utils.sendComponent(sender, Component.text("  Reason: ", NamedTextColor.GRAY).append(Component.text((String) ipInfo.get("reason"), NamedTextColor.WHITE)));
                    Utils.sendComponent(sender, Component.text("  Time Left: ", NamedTextColor.GRAY).append(Component.text(Utils.formatTimeLeft((long) ipInfo.get("end_time")), NamedTextColor.WHITE)));
                }
            }
            return true;
        }

        // /unban <player>
        if (label.equalsIgnoreCase("unban")) {
            if (!sender.hasPermission("staff.unban")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Utils.color("&cUsage: /unban <player>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            boolean wasBanned = database.unbanPlayer(target.getUniqueId());

            if (wasBanned) {
                // Staff broadcast
                String msg = String.format("&a%s &fwas unbanned by &a%s&f.", target.getName(), sender.getName());
                Utils.broadcastToStaff(msg);
                plugin.sendStaffLog(sender.getName() + " unbanned " + target.getName());
                Utils.sendComponent(sender, prefix.append(Component.text(target.getName() + " has been unbanned.", NamedTextColor.GREEN)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text("Player " + target.getName() + " is not banned by UUID.", NamedTextColor.RED)));
            }
            return true;
        }

        // /unbanip <ip>
        if (label.equalsIgnoreCase("unbanip")) {
            if (!sender.hasPermission("staff.unbanip")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Utils.color("&cUsage: /unbanip <ip_address>"));
                return true;
            }

            String ipAddress = args[0];
            boolean wasBanned = database.unbanIp(ipAddress);

            if (wasBanned) {
                // Staff broadcast
                String msg = String.format("&aIP &b%s &fwas unbanned by &a%s&f.", ipAddress, sender.getName());
                Utils.broadcastToStaff(msg);
                plugin.sendStaffLog(sender.getName() + " unbanned IP " + ipAddress);
                Utils.sendComponent(sender, prefix.append(Component.text("IP " + ipAddress + " has been unbanned.", NamedTextColor.GREEN)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text("IP " + ipAddress + " is not banned.", NamedTextColor.RED)));
            }
            return true;
        }

        return false;
    }
}