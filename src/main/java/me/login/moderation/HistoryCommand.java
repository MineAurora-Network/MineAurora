package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

public class HistoryCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationDatabase database;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final SimpleDateFormat dateFormat;

    public HistoryCommand(Login plugin, ModerationDatabase database) {
        this.plugin = plugin;
        this.database = database;

        // Set Timezone to India
        this.dateFormat = new SimpleDateFormat("dd/MM/yyyy HH:mm");
        this.dateFormat.setTimeZone(TimeZone.getTimeZone("Asia/Kolkata"));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean isBan = label.equalsIgnoreCase("banhistory");
        String perm = isBan ? "staff.banhistory" : "staff.mutehistory";

        if (!sender.hasPermission(perm)) {
            sender.sendMessage(Utils.color("&cYou do not have permission."));
            return true;
        }

        if (args.length < 1) {
            sender.sendMessage(Utils.color("&cUsage: /" + label + " <player>"));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        String name = target.getName() != null ? target.getName() : args[0];
        Component prefix = Utils.getServerPrefix(plugin);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Map<String, Object>> history = isBan ? database.getBanHistory(uuid) : database.getMuteHistory(uuid);

            if (history.isEmpty()) {
                Utils.sendComponent(sender, prefix.append(Component.text("No " + (isBan ? "ban" : "mute") + " history found for " + name + ".", NamedTextColor.GREEN)));
                return;
            }

            sender.sendMessage(prefix.append(mm.deserialize("<gold>" + (isBan ? "Ban" : "Mute") + " History for <yellow>" + name + "</yellow> (IST):</gold>")));

            for (Map<String, Object> entry : history) {
                String staff = (String) entry.get("staff_name");
                String reason = (String) entry.get("reason");
                long start = (long) entry.get("start_time");
                long end = (long) entry.get("end_time");
                boolean active = (boolean) entry.get("active");

                String dateStr = dateFormat.format(new Date(start));
                String durationStr = (end == -1) ? "Permanent" : Utils.formatDuration(end - start);
                String statusColor = active ? "<red>" : "<gray>";

                String line = "<dark_gray>•</dark_gray> " + statusColor + dateStr + "</" + (active?"red":"gray") + ">: " +
                        "<white>" + reason + "</white> " +
                        "<gray>- by <yellow>" + staff + "</yellow> (<aqua>" + durationStr + "</aqua>)</gray>";

                sender.sendMessage(mm.deserialize(line));
            }
        });

        return true;
    }
}