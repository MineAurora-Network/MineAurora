//
// File: me/login/moderation/commands/MuteCommand.java
// (Updated with new mute message format)
//
package me.login.moderation;

import me.login.Login;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.Utils;
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

public class MuteCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationDatabase database;

    public MuteCommand(Login plugin, ModerationDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // /mute <player> <duration> <reason>
        if (label.equalsIgnoreCase("mute")) {
            if (!sender.hasPermission("staff.mute")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(Utils.color("&cUsage: /mute <player> <duration> <reason>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            // --- Self-punish check ---
            if (sender instanceof Player && ((Player) sender).getUniqueId().equals(target.getUniqueId())) {
                sender.sendMessage(Utils.color("&cYou cannot mute yourself."));
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
            } // Console (sender not instanceof Player) bypasses this check

            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) {
                sender.sendMessage(Utils.color("&cInvalid duration format. Use 'perm' or units like s, m, h, d."));
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"); // Console UUID
            String staffName = sender.getName();

            boolean success = database.mutePlayer(target.getUniqueId(), target.getName(), staffUUID, staffName, reason, duration);

            if (success) {
                String durationStr = Utils.formatDuration(duration);
                // Staff broadcast uses simple legacy prefix
                String msg = String.format("&b%s &fwas muted by &b%s&f for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durationStr);
                Utils.broadcastToStaff(msg);

                plugin.sendStaffLog(sender.getName() + " muted " + target.getName() + " for " + durationStr + ". Reason: " + reason);

                if (target.isOnline()) {
                    // --- UPDATED MESSAGE FORMAT ---
                    Player targetPlayer = target.getPlayer();
                    String timeLeft = Utils.formatDurationShort(duration); // Use new method
                    String discordLink = plugin.getConfig().getString("discord-server-link", "your-discord.gg");

                    String message = "&c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                            "&cYou are currently muted for &f" + reason + "&c on the server.\n" +
                            "&7Your mute will expire in &f" + timeLeft + "\n" +
                            "&7Appeal at: &f" + discordLink + "\n" +
                            " &c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

                    targetPlayer.sendMessage(Utils.color(message));
                    // --- END UPDATE ---
                }
            } else {
                sender.sendMessage(Utils.color("&cAn error occurred while muting the player."));
            }
            return true;
        }

        // /muteinfo <player>
        if (label.equalsIgnoreCase("muteinfo")) {
            if (!sender.hasPermission("staff.muteinfo")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Utils.color("&cUsage: /muteinfo <player>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            Map<String, Object> info = database.getActiveMuteInfo(target.getUniqueId());
            Component prefix = Utils.getServerPrefix(plugin); // Use server prefix for info commands

            if (info == null) {
                Utils.sendComponent(sender, prefix.append(Component.text("Player " + target.getName() + " is not currently muted.", NamedTextColor.GREEN)));
                return true;
            }

            Utils.sendComponent(sender, prefix.append(Component.text("Mute Info for " + target.getName() + ":", NamedTextColor.YELLOW)));
            Utils.sendComponent(sender, Component.text("  Muted by: ", NamedTextColor.GRAY).append(Component.text((String) info.get("staff_name"), NamedTextColor.WHITE)));
            Utils.sendComponent(sender, Component.text("  Reason: ", NamedTextColor.GRAY).append(Component.text((String) info.get("reason"), NamedTextColor.WHITE)));
            Utils.sendComponent(sender, Component.text("  Time Left: ", NamedTextColor.GRAY).append(Component.text(Utils.formatTimeLeft((long) info.get("end_time")), NamedTextColor.WHITE)));
            return true;
        }

        // /unmute <player>
        if (label.equalsIgnoreCase("unmute")) {
            if (!sender.hasPermission("staff.unmute")) {
                sender.sendMessage(Utils.color("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(Utils.color("&cUsage: /unmute <player>"));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(Utils.color("&cPlayer not found."));
                return true;
            }

            boolean wasMuted = database.unmutePlayer(target.getUniqueId());
            Component prefix = Utils.getServerPrefix(plugin);

            if (wasMuted) {
                // Staff broadcast
                String msg = String.format("&b%s &fwas unmuted by &b%s&f.", target.getName(), sender.getName());
                Utils.broadcastToStaff(msg);
                plugin.sendStaffLog(sender.getName() + " unmuted " + target.getName());

                if (target.isOnline()) {
                    // Player message
                    target.getPlayer().sendMessage(prefix.append(Component.text("You have been unmuted.", NamedTextColor.GREEN)));
                }
                Utils.sendComponent(sender, prefix.append(Component.text(target.getName() + " has been unmuted.", NamedTextColor.GREEN)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text("Player " + target.getName() + " is not muted.", NamedTextColor.RED)));
            }
            return true;
        }

        return false;
    }
}