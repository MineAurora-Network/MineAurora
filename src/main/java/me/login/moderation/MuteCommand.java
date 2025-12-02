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

public class MuteCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationDatabase database;
    private final ModerationLogger logger;

    public MuteCommand(Login plugin, ModerationDatabase database, ModerationLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // --- MUTE ---
        if (label.equalsIgnoreCase("mute")) {
            if (!sender.hasPermission("staff.mute")) return noPerm(sender);
            if (args.length < 3) return usage(sender, "/mute <player> <duration> <reason>");

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

            if (!Utils.canPunish(sender, target)) {
                sender.sendMessage(Utils.color("&cYou cannot mute this player (Higher rank/Equal rank/Self)."));
                return true;
            }

            long duration = Utils.parseDuration(args[1]);
            if (duration == 0) { sender.sendMessage(Utils.color("&cInvalid duration.")); return true; }

            String reason = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
            UUID staffUUID = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000");

            if (database.mutePlayer(target.getUniqueId(), target.getName(), staffUUID, sender.getName(), reason, duration)) {
                String durStr = Utils.formatDuration(duration);
                Utils.broadcastToStaff(String.format("&b%s &fwas muted by &b%s&f for &e%s &f(&e%s&f).", target.getName(), sender.getName(), reason, durStr));

                // Log MUTE
                logger.log(ModerationLogger.LogType.MUTE, "🔇 **MUTE** | " + target.getName() + " was muted by " + sender.getName() + " for: `" + reason + "` (" + durStr + ")");

                if (target.isOnline() && target.getPlayer() != null) {
                    String timeLeft = Utils.formatDurationShort(duration);
                    String msg = "&c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                            "&cYou are currently muted for &f" + reason + "&c.\n" +
                            "&7Expires in &f" + timeLeft + "\n" +
                            " &c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";
                    target.getPlayer().sendMessage(Utils.color(msg));
                }
            }
            return true;
        }

        // --- UNMUTE ---
        if (label.equalsIgnoreCase("unmute")) {
            if (!sender.hasPermission("staff.unmute")) return noPerm(sender);
            if (args.length < 1) return usage(sender, "/unmute <player>");
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (database.unmutePlayer(target.getUniqueId())) {
                Utils.broadcastToStaff(String.format("&b%s &fwas unmuted by &b%s&f.", target.getName(), sender.getName()));
                // Log UNMUTE
                logger.log(ModerationLogger.LogType.UNMUTE, "🔊 **UNMUTE** | " + target.getName() + " was unmuted by " + sender.getName());

                sender.sendMessage(Utils.color("&aUnmuted " + target.getName()));
                if(target.isOnline()) target.getPlayer().sendMessage(Utils.color("&aYou have been unmuted."));
            } else {
                sender.sendMessage(Utils.color("&cPlayer is not muted."));
            }
            return true;
        }

        // --- MUTE INFO ---
        if (label.equalsIgnoreCase("muteinfo")) {
            if (!sender.hasPermission("staff.muteinfo")) return noPerm(sender);
            if (args.length < 1) return usage(sender, "/muteinfo <player>");
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            Map<String, Object> info = database.getActiveMuteInfo(target.getUniqueId());
            Component prefix = Utils.getServerPrefix(plugin);

            if (info != null) {
                Utils.sendComponent(sender, prefix.append(Component.text("Mute Info for " + target.getName() + ":", NamedTextColor.YELLOW)));
                Utils.sendComponent(sender, Component.text("  Muted by: ", NamedTextColor.GRAY).append(Component.text((String) info.get("staff_name"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Reason: ", NamedTextColor.GRAY).append(Component.text((String) info.get("reason"), NamedTextColor.WHITE)));
                Utils.sendComponent(sender, Component.text("  Time Left: ", NamedTextColor.GRAY).append(Component.text(Utils.formatTimeLeft((long) info.get("end_time")), NamedTextColor.WHITE)));
            } else {
                Utils.sendComponent(sender, prefix.append(Component.text(target.getName() + " is not muted.", NamedTextColor.GREEN)));
            }
            return true;
        }
        return false;
    }
    private boolean noPerm(CommandSender s) { s.sendMessage(Utils.color("&cNo permission.")); return true; }
    private boolean usage(CommandSender s, String u) { s.sendMessage(Utils.color("&cUsage: " + u)); return true; }
}