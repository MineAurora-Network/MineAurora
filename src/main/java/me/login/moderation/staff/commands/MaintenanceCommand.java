package me.login.moderation.staff.commands;

import me.login.Login;
import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class MaintenanceCommand implements CommandExecutor {

    private final Login plugin;
    private final StaffManager manager;
    private final StaffLogger logger;

    public MaintenanceCommand(Login plugin, StaffManager manager, StaffLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.command.maintenance")) return noPerm(sender);

        if (args.length != 1 || (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off"))) {
            sender.sendMessage(Utils.color("&cUsage: /maintenance <on/off>"));
            return true;
        }

        boolean state = args[0].equalsIgnoreCase("on");
        manager.setGlobalMaintenance(state);

        String status = state ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
        Bukkit.broadcast(Utils.getServerPrefix(plugin).append(MiniMessage.miniMessage().deserialize("<gold>Global Maintenance has been " + status + "<gold> by <yellow>" + sender.getName())));

        logger.log(StaffLogger.LogType.MAINTENANCE, "Global Maintenance set to **" + state + "** by " + sender.getName());
        return true;
    }

    private boolean noPerm(CommandSender s) { s.sendMessage(Utils.color("&cNo permission.")); return true; }
}