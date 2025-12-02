package me.login.moderation;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ModerationTabCompleter implements TabCompleter {

    private final List<String> DURATIONS = Arrays.asList("1h", "12h", "1d", "7d", "30d", "perm");
    private final List<String> REASONS = Arrays.asList("Spamming", "Abuse", "Hacking", "Advertising", "Toxicity", "Harassment", "Griefing");

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        // Arg 1: Player Names (Always)
        if (args.length == 1) {
            return null; // Return null to let Bukkit handle online player names automatically
        }

        // Logic for Ban, IPBan, Mute (Format: /cmd <player> <duration> <reason>)
        if (cmdName.equals("ban") || cmdName.equals("ipban") || cmdName.equals("mute")) {
            // Arg 2: Duration
            if (args.length == 2) {
                return filter(DURATIONS, args[1]);
            }
            // Arg 3: Reason
            if (args.length == 3) {
                return filter(REASONS, args[2]);
            }
        }

        // Logic for Kick (Format: /kick <player> <reason>)
        if (cmdName.equals("kick")) {
            // Arg 2: Reason
            if (args.length == 2) {
                return filter(REASONS, args[1]);
            }
        }

        // Logic for Unban, Unmute, Histories (Format: /cmd <player>)
        // These only have Arg 1, which is handled above.

        return Collections.emptyList();
    }

    private List<String> filter(List<String> list, String input) {
        String lowerInput = input.toLowerCase();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(lowerInput))
                .collect(Collectors.toList());
    }
}