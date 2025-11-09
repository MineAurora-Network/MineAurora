package me.login.leaderboards;

import me.login.Login;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private final LeaderboardModule module; // <-- ADDED
    private final LeaderboardDisplayManager manager;
    private final List<String> spawnTypes = Arrays.asList("kills", "deaths", "playtime", "balance", "credits", "lifesteal");
    private final List<String> subCommands = Arrays.asList("reload");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    // --- CONSTRUCTOR UPDATED ---
    public LeaderboardCommand(Login plugin, LeaderboardModule module, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.module = module; // <-- ADDED
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Audience audience = (Audience) sender;

        if (!(sender instanceof Player)) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                // --- CHANGED ---
                module.reload();
                audience.sendMessage(miniMessage.deserialize("<green>Leaderboards configuration reloaded and all displays updated.</green>"));
                return true;
            }
            audience.sendMessage(miniMessage.deserialize("<red>This command can only be run by a player.</red>"));
            return true;
        }

        Player player = (Player) sender;
        if (!player.hasPermission("leaderboards.admin")) {
            audience.sendMessage(miniMessage.deserialize("<red>You do not have permission to use this command.</red>"));
            return true;
        }

        if (args.length != 1) {
            sendUsage(player);
            return true;
        }

        String arg = args[0].toLowerCase();

        if (arg.equals("reload")) {
            // --- CHANGED ---
            module.reload();
            audience.sendMessage(miniMessage.deserialize("<green>Leaderboards configuration reloaded and all displays updated.</green>"));
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
        final Audience audience = (Audience) player;
        audience.sendMessage(miniMessage.deserialize("<red>Usage: /leaderboard <type|reload></red>"));
        audience.sendMessage(miniMessage.deserialize("<red>Types: kills, deaths, playtime, balance, credits, lifesteal</red>"));
    }
}