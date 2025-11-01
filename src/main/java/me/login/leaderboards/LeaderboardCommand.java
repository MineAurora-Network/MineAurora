package me.login.leaderboards;

import me.login.Login;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LeaderboardCommand implements TabExecutor {

    private final Login plugin;
    private final LeaderboardDisplayManager manager;
    // --- ADDED NEW TYPES ---
    private final List<String> spawnTypes = Arrays.asList("kills", "deaths", "playtime", "balance", "credits", "lifesteal");
    private final List<String> subCommands = Arrays.asList("reload");

    public LeaderboardCommand(Login plugin, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                plugin.reloadLeaderboards();
                sender.sendMessage(ChatColor.GREEN + "Leaderboards configuration reloaded and all displays updated.");
                return true;
            }
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("leaderboards.admin")) {
            player.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("reload")) {
            plugin.reloadLeaderboards();
            player.sendMessage(ChatColor.GREEN + "Leaderboards configuration reloaded and all displays updated.");
            return true;

        } else if (spawnTypes.contains(arg)) {
            manager.createLeaderboard(player.getLocation(), arg, player);
            return true;

        } else {
            sendUsage(player);
            return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("leaderboards.admin")) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();

            StringUtil.copyPartialMatches(partial, spawnTypes, completions);
            StringUtil.copyPartialMatches(partial, subCommands, completions);

            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }
        return null;
    }

    private void sendUsage(Player player) {
        player.sendMessage(ChatColor.RED + "Usage: /leaderboard <type|reload>");
        // --- UPDATED USAGE ---
        player.sendMessage(ChatColor.RED + "Types: kills, deaths, playtime, balance, credits, lifesteal");
    }
}