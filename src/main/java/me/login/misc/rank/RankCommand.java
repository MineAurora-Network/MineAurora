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
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RankCommand implements CommandExecutor, TabCompleter {

    private final RankManager manager;
    private final LuckPerms luckPerms;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

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
                if (args.length < 4) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /rank set <player> <rank> <time|permanent></red>")));
                    return true;
                }
                handleSetRank(sender, args[1], args[2], args[3]);
                break;
            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /rank remove <player></red>")));
                    return true;
                }
                handleRemoveRank(sender, args[1]);
                break;
            case "info":
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
        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(timeString);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Invalid time format: " + e.getMessage() + "</red>")));
            return;
        }

        Group group = luckPerms.getGroupManager().getGroup(rankName);
        if (group == null) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>The rank '<white>" + rankName + "</white>' does not exist.</red>")));
            return;
        }

        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace();
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            manager.setRank(sender, targetUser, group, durationMillis);
        });
    }

    private void handleRemoveRank(CommandSender sender, String playerName) {
        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace();
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            manager.removeRank(sender, targetUser);
        });
    }

    private void handleRankInfo(CommandSender sender, String playerName) {
        loadUser(playerName).whenComplete((targetUser, error) -> {
            if (error != null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred loading user data.</red>")));
                error.printStackTrace();
                return;
            }
            if (targetUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not find player: <white>" + playerName + "</white>. They must have joined the server at least once.</red>")));
                return;
            }

            String infoMessage = mm.serialize(manager.getRankInfo(targetUser));
            for (String line : infoMessage.split("%nl%")) {
                sender.sendMessage(mm.deserialize(line));
            }
        });
    }

    private CompletableFuture<User> loadUser(String playerName) {
        @SuppressWarnings("deprecation")
        OfflinePlayer player = Bukkit.getOfflinePlayer(playerName);

        if (player.hasPlayedBefore() || player.isOnline()) {
            return luckPerms.getUserManager().loadUser(player.getUniqueId());
        } else {
            return CompletableFuture.completedFuture(null);
        }
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
            return null;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("set")) {
            List<String> ranks = luckPerms.getGroupManager().getLoadedGroups().stream()
                    .sorted((g1, g2) -> g2.getWeight().orElse(0) - g1.getWeight().orElse(0))
                    .map(Group::getName)
                    .collect(Collectors.toList());

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