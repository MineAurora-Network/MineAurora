package me.login.leaderboards;

import me.login.Login;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class KillLeaderboardCommand implements TabExecutor {

    private final Login plugin;
    private final LeaderboardDisplayManager manager;
    private final List<String> leaderboardTypes = Arrays.asList(
            "kills", "deaths", "playtime", "balance", "credits", "lifesteal", "token", "parkour", "all"
    );

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public KillLeaderboardCommand(Login plugin, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Audience audience = (Audience) sender;

        if (!sender.hasPermission("leaderboards.admin")) {
            audience.sendMessage(miniMessage.deserialize("<red>You do not have permission.</red>"));
            return true;
        }

        if (args.length != 1) {
            sendUsage(sender);
            return true;
        }

        String typeToRemove = args[0].toLowerCase();

        if (!leaderboardTypes.contains(typeToRemove)) {
            audience.sendMessage(miniMessage.deserialize("<red>Invalid leaderboard type specified.</red>"));
            sendUsage(sender);
            return true;
        }

        int removedCount = manager.removeLeaderboardsByType(typeToRemove);

        if (removedCount > 0) {
            audience.sendMessage(miniMessage.deserialize("<green>Successfully removed " + removedCount + " leaderboard display(s) of type '" + typeToRemove + "'.</green>"));
        } else {
            audience.sendMessage(miniMessage.deserialize("<yellow>No leaderboard displays found matching type '" + typeToRemove + "'.</yellow>"));
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("leaderboards.admin")) {
            List<String> completions = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0].toLowerCase(), leaderboardTypes, completions);
            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }
        return null;
    }

    private void sendUsage(CommandSender sender) {
        Audience audience = (Audience) sender;
        audience.sendMessage(miniMessage.deserialize("<red>Usage: /killleaderboard <type|all></red>"));
    }
}