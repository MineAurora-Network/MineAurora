package me.login.moderation.commands;

import me.login.Login;
import me.login.moderation.commands.util.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ScheduleCommand implements CommandExecutor, TabCompleter {

    private final AdminCommandsModule module;
    private final Login plugin;

    public ScheduleCommand(AdminCommandsModule module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    /**
     * Helper to send a prefixed message
     */
    private void sendPrefixedMessage(CommandSender sender, String message) {
        sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("login.admin.schedule")) {
            sendPrefixedMessage(sender, "<red>You do not have permission to use this command.");
            return true;
        }

        if (args.length < 2) {
            sendUsage(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String timeString = args[1];
        long ticks;

        try {
            ticks = TimeUtil.parseTime(timeString);
            if (ticks <= 0) {
                sendPrefixedMessage(sender, "<red>Invalid time format. Use 1h, 30m, 10s, or a combination.");
                return true;
            }
        } catch (IllegalArgumentException e) {
            sendPrefixedMessage(sender, "<red>Invalid time: " + e.getMessage());
            return true;
        }

        String formattedTime = TimeUtil.formatTime(ticks / 20);

        switch (subCommand) {
            case "maintenance":
                module.getMaintenanceManager().scheduleMaintenance(ticks, formattedTime);
                sendPrefixedMessage(sender, "<green>Maintenance scheduled to begin in " + formattedTime + ".");
                break;
            case "restart":
                module.getRestartManager().scheduleRestart(ticks, formattedTime);
                sendPrefixedMessage(sender, "<green>Server restart scheduled in " + formattedTime + ".");
                break;
            default:
                sendUsage(sender);
                break;
        }
        return true;
    }

    private void sendUsage(CommandSender sender) {
        sendPrefixedMessage(sender, "<red>Usage: /schedule <maintenance|restart> <time>");
        sendPrefixedMessage(sender, "<gray>Example: /schedule maintenance 1h30m");
        sendPrefixedMessage(sender, "<gray>Example: /schedule restart 10m");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("maintenance", "restart").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 2) {
            return Collections.singletonList("1h");
        }
        return Collections.emptyList();
    }
}