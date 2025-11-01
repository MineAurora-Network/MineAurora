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

public class KillLeaderboardCommand implements TabExecutor {

    private final Login plugin;
    private final LeaderboardDisplayManager manager;
    private final List<String> leaderboardTypes = Arrays.asList(
            "kills", "deaths", "playtime", "balance", "credits", "lifesteal", "all"
    );

    public KillLeaderboardCommand(Login plugin, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("leaderboards.admin")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        String typeToRemove = args[0].toLowerCase();

        if (!leaderboardTypes.contains(typeToRemove)) {
            sender.sendMessage(ChatColor.RED + "Invalid leaderboard type specified.");
            sendUsage(sender);
            return true;
        }

        int removedCount = manager.removeLeaderboardsByType(typeToRemove);

        if (removedCount > 0) {
            sender.sendMessage(ChatColor.GREEN + "Successfully removed " + removedCount + " leaderboard display(s) of type '" + typeToRemove + "'.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "No leaderboard displays found matching type '" + typeToRemove + "'.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("leaderboards.admin")) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();
            StringUtil.copyPartialMatches(partial, leaderboardTypes, completions);
            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }
        return null;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ChatColor.RED + "Usage: /killleaderboard <type|all>");
        sender.sendMessage(ChatColor.RED + "Types: kills, deaths, playtime, balance, credits, lifesteal, all");
    }
}