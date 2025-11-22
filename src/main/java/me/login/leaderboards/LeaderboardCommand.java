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
    private final LeaderboardModule module;
    private final LeaderboardDisplayManager manager;
    private final LeaderboardGUI gui;

    // Admin spawn types
    private final List<String> spawnTypes = Arrays.asList("kills", "deaths", "playtime", "balance", "credits", "lifesteal", "token", "parkour", "tokens");
    // User subcommands
    private final List<String> subCommands = Arrays.asList("reload", "toggleop", "menu");
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public LeaderboardCommand(Login plugin, LeaderboardModule module, LeaderboardDisplayManager manager) {
        this.plugin = plugin;
        this.module = module;
        this.manager = manager;
        this.gui = new LeaderboardGUI(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        final Audience audience = (Audience) sender;

        if (!(sender instanceof Player)) {
            // Console logic
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                module.reload();
                audience.sendMessage(miniMessage.deserialize("<green>Leaderboards reloaded.</green>"));
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("toggleop")) {
                boolean newState = !LeaderboardModule.isShowOps();
                LeaderboardModule.setShowOps(newState);
                audience.sendMessage(miniMessage.deserialize("<green>Leaderboard OP visibility set to: " + newState + "</green>"));
                return true;
            }
            audience.sendMessage(miniMessage.deserialize("<red>Command not supported from console.</red>"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            gui.openMenu(player);
            return true;
        }

        String arg = args[0].toLowerCase();

        // Hidden command for chat pagination functionality
        if (arg.equals("_navigate")) {
            if (args.length < 3) return true; // Silently fail if args missing
            String type = args[1];
            int page = 1;
            try {
                page = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {}
            gui.sendLeaderboardChat(player, type, page);
            return true;
        }

        // Admin Logic for Spawning/Reloading
        if (!player.hasPermission("leaderboards.admin")) {
            audience.sendMessage(miniMessage.deserialize("<red>No permission.</red>"));
            return true;
        }

        switch (arg) {
            case "reload":
                module.reload();
                audience.sendMessage(miniMessage.deserialize("<green>Leaderboards configuration reloaded.</green>"));
                return true;

            case "toggleop":
                boolean newState = !LeaderboardModule.isShowOps();
                LeaderboardModule.setShowOps(newState);
                audience.sendMessage(miniMessage.deserialize("<green>Leaderboard OP visibility set to: " + newState + "</green>"));
                return true;

            default:
                if (spawnTypes.contains(arg)) {
                    manager.createLeaderboard(player.getLocation(), arg, player);
                    return true;
                }
                sendUsage(player);
                return true;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String partial = args[0].toLowerCase();

            if (sender.hasPermission("leaderboards.admin")) {
                StringUtil.copyPartialMatches(partial, spawnTypes, completions);
                StringUtil.copyPartialMatches(partial, subCommands, completions);
            } else {
                // Regular players only see "menu"
                StringUtil.copyPartialMatches(partial, Arrays.asList("menu"), completions);
            }

            completions.sort(String.CASE_INSENSITIVE_ORDER);
            return completions;
        }
        return null;
    }

    private void sendUsage(Player player) {
        Audience audience = (Audience) player;
        audience.sendMessage(miniMessage.deserialize("<red>Usage: /leaderboard <menu|type|reload|toggleop></red>"));
    }
}