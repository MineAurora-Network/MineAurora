package me.login.moderation.staff.commands;

import me.login.Login;
import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.scheduler.BukkitRunnable;

public class ScheduleRestartCommand implements CommandExecutor {

    private final Login plugin;
    private final StaffLogger logger;

    public ScheduleRestartCommand(Login plugin, StaffLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.command.schedulerestart")) {
            sender.sendMessage(Utils.color("&cNo permission."));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(Utils.color("&cUsage: /schedulerestart <time> (e.g. 10s, 1m)"));
            return true;
        }

        long millis = Utils.parseDuration(args[0]);
        if (millis <= 0) {
            sender.sendMessage(Utils.color("&cInvalid time."));
            return true;
        }

        long ticks = millis / 50; // Convert millis to ticks
        long seconds = millis / 1000;

        sender.sendMessage(Utils.getServerPrefix(plugin).append(Component.text("Restart scheduled in " + args[0], NamedTextColor.GREEN)));
        logger.log(StaffLogger.LogType.RESTART, "Server restart scheduled in **" + args[0] + "** by " + sender.getName());

        new BukkitRunnable() {
            long remaining = seconds;

            @Override
            public void run() {
                if (remaining <= 0) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
                    this.cancel();
                    return;
                }

                // Broadcast specific times
                if (remaining == 60 || remaining == 10) {
                    Component msg = Utils.getServerPrefix(plugin).append(Component.text("Server restarting in " + remaining + " seconds!", NamedTextColor.RED));
                    Bukkit.broadcast(msg);
                }

                remaining--;
            }
        }.runTaskTimer(plugin, 0L, 20L); // Run every second

        return true;
    }
}