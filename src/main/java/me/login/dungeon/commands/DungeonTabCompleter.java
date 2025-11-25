package me.login.dungeon.commands;

import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.manager.DungeonRewardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DungeonTabCompleter implements TabCompleter {

    private final DungeonManager dungeonManager;
    private final DungeonRewardManager rewardManager;

    public DungeonTabCompleter(DungeonManager dungeonManager, DungeonRewardManager rewardManager) {
        this.dungeonManager = dungeonManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList(
                    "create", "delete", "setup", "start", "stop", "check",
                    "redo", "tempopen", "rngmeter", "give",
                    "removemobspawn", "showremaining"
            ), args[0]);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("give")) {
                List<String> options = new ArrayList<>();
                options.add("all");
                for (DungeonRewardManager.RewardItem item : rewardManager.getAllRewards()) {
                    options.add(item.id);
                }
                return filter(options, args[1]);
            }
            if (sub.equals("create") || sub.equals("delete") || sub.equals("start") ||
                    sub.equals("setup") || sub.equals("redo") || sub.equals("check") ||
                    sub.equals("tempopen")) {
                return Collections.singletonList("<id>");
            }
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setup")) {
                return filter(Arrays.asList("room", "lastroom", "entrydoor", "chest"), args[2]);
            }
            if (sub.equals("tempopen")) {
                return Collections.singletonList("door");
            }
            if (sub.equals("check")) {
                return Collections.singletonList("room");
            }
        }

        if (args.length == 4) {
            String sub = args[0].toLowerCase();
            String type = args[2].toLowerCase();

            if (sub.equals("tempopen")) {
                return Collections.singletonList("<roomID>");
            }

            if (sub.equals("check")) {
                return Collections.singletonList("<roomID>");
            }

            if (sub.equals("setup")) {
                if (type.equals("room")) {
                    return Collections.singletonList("<roomID>");
                }
                if (type.equals("lastroom")) {
                    return filter(Arrays.asList("bossspawn", "rewardloc", "bossdoor", "treasuredoor"), args[3]);
                }
                if (type.equals("entrydoor")) {
                    return filter(Arrays.asList("pos1", "pos2"), args[3]);
                }
            }
        }

        if (args.length == 5) {
            String sub = args[0].toLowerCase();

            if (sub.equals("check")) {
                return Collections.singletonList("mobspawn");
            }

            if (sub.equals("setup")) {
                String type = args[2].toLowerCase();
                if (type.equals("room")) {
                    return filter(Arrays.asList("mobspawn", "door"), args[4]);
                }
                if (type.equals("lastroom")) {
                    String lastType = args[3].toLowerCase();
                    if (lastType.equals("bossdoor") || lastType.equals("treasuredoor")) {
                        return filter(Arrays.asList("pos1", "pos2"), args[4]);
                    }
                }
            }
        }

        if (args.length == 6) {
            String sub = args[0].toLowerCase();
            if (sub.equals("setup")) {
                String type = args[2].toLowerCase();
                if (type.equals("room") && args[4].equalsIgnoreCase("door")) {
                    return filter(Arrays.asList("pos1", "pos2"), args[5]);
                }
            }
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String arg) {
        return list.stream().filter(s -> s.toLowerCase().startsWith(arg.toLowerCase())).collect(Collectors.toList());
    }
}