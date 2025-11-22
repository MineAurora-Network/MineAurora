package me.login.dungeon.commands;

import me.login.dungeon.manager.DungeonManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class DungeonTabCompleter implements TabCompleter {

    private final DungeonManager dungeonManager;

    public DungeonTabCompleter(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("rngmeter");
            if (sender.hasPermission("dungeon.admin")) {
                completions.addAll(Arrays.asList("create", "setup", "delete", "start", "stop", "redo", "check", "teleport"));
            }
            return completions;
        }

        if (args.length == 2 && sender.hasPermission("dungeon.admin")) {
            return Arrays.asList("1", "2", "3");
        }

        if (!sender.hasPermission("dungeon.admin")) return Collections.emptyList();

        if (args[0].equalsIgnoreCase("setup")) {
            if (args.length == 3) return Arrays.asList("entrydoor", "lastroom", "room");

            String type = args[2].toLowerCase();

            if (type.equals("entrydoor")) {
                if (args.length == 4) return Arrays.asList("pos1", "pos2");
            }
            else if (type.equals("lastroom")) {
                // Updated list with both doors
                if (args.length == 4) return Arrays.asList("bossspawn", "rewardloc", "bossdoor", "treasuredoor");
                if ((args[3].equalsIgnoreCase("bossdoor") || args[3].equalsIgnoreCase("treasuredoor")) && args.length == 5) {
                    return Arrays.asList("pos1", "pos2");
                }
            }
            else if (type.equals("room")) {
                if (args.length == 4) return Arrays.asList("1", "2", "3", "4", "5", "6", "7");
                if (args.length == 5) return Arrays.asList("door", "mobspawn", "resetspawns");
                if (args.length == 6 && args[4].equalsIgnoreCase("door")) return Arrays.asList("pos1", "pos2");
            }
        }
        return Collections.emptyList();
    }
}