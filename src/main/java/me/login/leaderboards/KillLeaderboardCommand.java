package me.login.leaderboards;

import me.login.Login;
import net.kyori.adventure.audience.Audience; // IMPORT
import net.kyori.adventure.text.minimessage.MiniMessage; // IMPORT
// import org.bukkit.ChatColor; // REMOVED
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
// import org.bukkit.entity.Player; // No longer needed
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

    // --- KYORI CHANGE ---
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public KillLeaderboardCommand(Login plugin, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // --- KYORI CHANGE ---
        final Audience audience = (Audience) sender;

        if (!sender.hasPermission("leaderboards.admin")) {
            // --- KYORI CHANGE ---
            audience.sendMessage(miniMessage.deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        String typeToRemove = args[0].toLowerCase();

        if (!leaderboardTypes.contains(typeToRemove)) {
            // --- KYORI CHANGE ---
            audience.sendMessage(miniMessage.deserialize("<red>Invalid leaderboard type specified.</red>"));
            sendUsage(sender);
            return true;
        }

        int removedCount = manager.removeLeaderboardsByType(typeToRemove);

        if (removedCount > 0) {
            // --- KYORI CHANGE ---
            audience.sendMessage(miniMessage.deserialize("<green>Successfully removed " + removedCount + " leaderboard display(s) of type '" + typeToRemove + "'.</green>"));
        } else {
            // --- KYORI CHANGE ---
            audience.sendMessage(miniMessage.deserialize("<yellow>No leaderboard displays found matching type '" + typeToRemove + "'.</yellow>"));
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
        // --- KYORI CHANGE ---
        final Audience audience = (Audience) sender;
        audience.sendMessage(miniMessage.deserialize("<red>Usage: /killleaderboard <type|all></red>"));
        audience.sendMessage(miniMessage.deserialize("<red>Types: kills, deaths, playtime, balance, credits, lifesteal, all</red>"));
    }
}