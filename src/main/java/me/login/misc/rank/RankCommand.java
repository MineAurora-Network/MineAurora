package me.login.misc.rank;

import me.login.misc.rank.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankManager manager;
    private final LuckPerms luckPerms;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Ranks to hide from non-op tab completion
    private final List<String> hiddenRanks = Arrays.asList("owner", "manager");

    public RankCommand(RankManager manager, LuckPerms luckPerms, Component serverPrefix) {
        this.manager = manager;
        this.luckPerms = luckPerms;
        this.serverPrefix = serverPrefix;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("login.rank.admin")) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You do not have permission to use this command.</red>")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "set":
                // /rank set <player> <rank> <time>
                if (args.length < 4) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /rank set <player> <rank> <time|permanent></red>")));
                    return true;
                }
                handleSetRank(sender, args[1], args[2], args[3]);
                break;
            case "remove":
                // /rank remove <player>
                if (args.length != 2) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /rank remove <player></red>")));
                    return true;
                }
                handleRemoveRank(sender, args[1]);
                break;
            case "info":
                // /rank info <player>
                if (args.length != 2) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /rank info <player></red>")));
                    return true;
                }
                handleRankInfo(sender, args[1]);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleSetRank(CommandSender sender, String playerName, String rankName, String timeString) {
        // Parse time
        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(timeString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Invalid time format: " + e.getMessage() + "</red>")));
            return;
        }

        // Get LP Group
        Group group = luckPerms.getGroupManager().getGroup(rankName);
        if (group == null) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>The rank '<white>" + rankName + "</white>' does not exist.</red>")));
            return;
        }

        // Get target User (async)
        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace(); // Log the error
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            // Check hierarchy
            if (!manager.canModify(sender, targetUser)) {
                return; // canModify sends its own messages
            }

            // All checks passed, set the rank
            manager.setRank(sender, targetUser, group, durationMillis);
        });
    }

    private void handleRemoveRank(CommandSender sender, String playerName) {
        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace(); // Log the error
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            // Check hierarchy
            if (!manager.canModify(sender, targetUser)) {
                return;
            }

            // All checks passed, remove the rank
            manager.removeRank(sender, targetUser);
        });
    }

    private void handleRankInfo(CommandSender sender, String playerName) {
        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace(); // Log the error
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            // Get info and send it (replacing %nl% with newlines)
            String infoMessage = mm.serialize(manager.getRankInfo(targetUser));
            for (String line : infoMessage.split("%nl%")) {
                sender.sendMessage(mm.deserialize(line));
            }
        });
    }

    /**
     * Loads a LuckPerms user asynchronously.
     */
    private CompletableFuture<User> loadUser(String playerName) {
        // --- THIS IS THE FIX ---
        // Use Bukkit's method to find the player, even if offline
        @SuppressWarnings("deprecation")
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);

        if (player.hasPlayedBefore() || player.isOnline()) {
            // Player has a local profile, load by UUID
            return luckPerms.getUserManager().loadUser(player.getUniqueId());
        } else {
            // Player has never joined this server.
            // As requested, we will not check LuckPerms and just return null.
            return CompletableFuture.completedFuture(null);
        }
        // --- END OF FIX ---
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<gold>--- Rank Admin Help ---</gold>"));
        sender.sendMessage(mm.deserialize("<yellow>/rank set <player> <rank> <time></yellow> <gray>- Sets a player's rank (e.g., 1h, 30d, permanent).</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/rank remove <player></yellow> <gray>- Removes a player's rank and sets to default.</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/rank info <player></yellow> <gray>- Shows info about a player's managed rank.</gray>"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("login.rank.admin")) {
            return null;
        }

        if (args.length == 1) {
            return filter(Arrays.asList("set", "remove", "info"), args[0]);
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("info"))) {
            return null; // Bukkit's default player completion
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            // Get all groups, sort by weight descending
            List<String> ranks = luckPerms.getGroupManager().getLoadedGroups().stream()
                    .sorted((g1, g2) -> g2.getWeight().orElse(0) - g1.getWeight().orElse(0))
                    .map(Group::getName)
                    .collect(Collectors.toList());

            // Filter hidden ranks if sender is not OP
            if (!sender.isOp()) {
                ranks.removeAll(hiddenRanks);
            }

            return filter(ranks, args[2]);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("set")) {
            return filter(Arrays.asList("1h", "1d", "7d", "30d", "permanent"), args[3]);
        }

        return null;
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(input.toLowerCase()))
                .collect(Collectors.toList());
    }
}