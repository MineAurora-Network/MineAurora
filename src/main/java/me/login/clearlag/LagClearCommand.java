package me.login.clearlag;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class LagClearCommand implements CommandExecutor, TabCompleter {

    private final Plugin plugin;
    private final LagClearConfig lagClearConfig; // <-- ADDED

    // --- CONSTRUCTOR UPDATED ---
    public LagClearCommand(Plugin plugin, LagClearConfig lagClearConfig) {
        this.plugin = plugin;
        this.lagClearConfig = lagClearConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // --- NEW: msgtoggle command for all players ---
        if (args[0].equalsIgnoreCase("msgtoggle")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(ChatColor.RED + "This command can only be run by a player.");
                return true;
            }

            Player player = (Player) sender;
            boolean currentSetting = lagClearConfig.getPlayerToggle(player.getUniqueId());
            boolean newSetting = !currentSetting; // Toggle the value
            lagClearConfig.setPlayerToggle(player.getUniqueId(), newSetting);

            String status = newSetting ? (ChatColor.GREEN + "ENABLED") : (ChatColor.RED + "DISABLED");
            player.sendMessage(ChatColor.RED + "Cleaner" + ChatColor.WHITE + ": Your automatic lag clear messages are now " + status + ChatColor.WHITE + ".");
            return true;
        }
        // --- END: msgtoggle ---

        // --- Admin 'clean' command ---
        if (!sender.hasPermission("lagclear.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args[0].equalsIgnoreCase("clean")) {
            sender.sendMessage(ChatColor.RED + "Cleaner§7: Running manual cleanup...");

            // Run the cleanup and get the count
            int removed = EntityCleanup.performCleanup(plugin);
            sender.sendMessage(ChatColor.RED + "Cleaner§7: §aSuccessfully removed " + removed + " items and entities.");
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ChatColor.GOLD + "--- ClearLag Help ---");
        // Check permission for admin help
        if (sender.hasPermission("lagclear.admin")) {
            sender.sendMessage(ChatColor.AQUA + "/lagclear clean" + ChatColor.WHITE + " - Manually cleans all ground items, projectiles and entities.");
        }
        sender.sendMessage(ChatColor.AQUA + "/lagclear msgtoggle" + ChatColor.WHITE + " - Toggles automatic cleanup messages for you.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            // Add player command
            completions.add("msgtoggle");
            // Add admin command if they have permission
            if (sender.hasPermission("lagclear.admin")) {
                completions.add("clean");
            }
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}