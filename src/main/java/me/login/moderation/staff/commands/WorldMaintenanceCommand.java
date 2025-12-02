package me.login.moderation.staff.commands;

import me.login.Login;
import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class WorldMaintenanceCommand implements CommandExecutor {

    private final Login plugin;
    private final StaffManager manager;
    private final StaffLogger logger;

    public WorldMaintenanceCommand(Login plugin, StaffManager manager, StaffLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("staff.command.worldmaintenance")) return noPerm(sender);

        if (args.length != 2 || (!args[1].equalsIgnoreCase("on") && !args[1].equalsIgnoreCase("off"))) {
            sender.sendMessage(Utils.color("&cUsage: /worldmaintenance <world> <on/off>"));
            return true;
        }

        World world = Bukkit.getWorld(args[0]);
        if (world == null) {
            sender.sendMessage(Utils.color("&cWorld not found."));
            return true;
        }

        boolean state = args[1].equalsIgnoreCase("on");

        // This method in StaffManager now handles the teleportation logic
        manager.setWorldMaintenance(world.getName(), state);

        String status = state ? "<green>LOCKED</green>" : "<red>UNLOCKED</red>";
        sender.sendMessage(Utils.getServerPrefix(plugin).append(MiniMessage.miniMessage().deserialize("<gold>World <yellow>" + world.getName() + "</yellow> is now " + status)));

        if (state) {
            sender.sendMessage(Utils.color("&7(Players have been evacuated to Hub/Login)"));
        }

        logger.log(StaffLogger.LogType.MAINTENANCE, "World Maintenance for **" + world.getName() + "** set to **" + state + "** by " + sender.getName());
        return true;
    }
    private boolean noPerm(CommandSender s) { s.sendMessage(Utils.color("&cNo permission.")); return true; }
}