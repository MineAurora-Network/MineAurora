package me.login.premiumfeatures.credits;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
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

public class CreditsCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final CreditsManager manager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CreditsCommand(Login plugin, CreditsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    private Component getPrefix() {
        String prefixStr = plugin.getConfig().getString("server_prefix", "<gray>[Server] </gray>");
        return mm.deserialize(prefixStr);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        Component prefix = getPrefix();

        // 1. /credits (Check self)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(prefix.append(mm.deserialize("<red>Console usage: /credits check <player></red>")));
                return true;
            }
            int balance = manager.getBalance(player);
            sender.sendMessage(prefix.append(mm.deserialize("<gray>Your credits: <gold>" + balance + "</gold></gray>")));
            return true;
        }

        String subCmd = args[0].toLowerCase();

        // 2. /credits check <player>
        if (subCmd.equals("check")) {
            if (args.length < 2) {
                // If player runs "/credits check" without args, treat as self-check
                if (sender instanceof Player player) {
                    int balance = manager.getBalance(player);
                    sender.sendMessage(prefix.append(mm.deserialize("<gray>Your credits: <gold>" + balance + "</gold></gray>")));
                } else {
                    sender.sendMessage(prefix.append(mm.deserialize("<red>Usage: /credits check <player></red>")));
                }
                return true;
            }

            if (!sender.hasPermission("login.credits.check.others")) {
                sender.sendMessage(prefix.append(mm.deserialize("<red>You do not have permission to check others' credits.</red>")));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            int balance = manager.getBalance(target);
            sender.sendMessage(prefix.append(mm.deserialize("<gray>" + target.getName() + "'s credits: <gold>" + balance + "</gold></gray>")));
            return true;
        }

        // 3. Admin Commands (add/remove/set)
        if (!sender.hasPermission("login.credits.admin")) {
            sender.sendMessage(prefix.append(mm.deserialize("<red>You do not have permission.</red>")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(prefix.append(mm.deserialize("<red>Usage: /credits <add/remove/set> <player> <amount></red>")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        int amount;

        try {
            amount = Integer.parseInt(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(prefix.append(mm.deserialize("<red>Invalid amount. Must be a positive integer.</red>")));
            return true;
        }

        String adminName = sender.getName();

        switch (subCmd) {
            case "add":
                manager.addCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(mm.deserialize("<green>Added <gold>" + amount + "</gold> credits to " + target.getName() + ".</green>")));
                break;
            case "remove":
                manager.removeCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(mm.deserialize("<red>Removed <gold>" + amount + "</gold> credits from " + target.getName() + ".</red>")));
                break;
            case "set":
                manager.setCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(mm.deserialize("<yellow>Set " + target.getName() + "'s credits to <gold>" + amount + "</gold>.</yellow>")));
                break;
            default:
                sender.sendMessage(prefix.append(mm.deserialize("<red>Usage: /credits <add/remove/set/check> <player> <amount></red>")));
                break;
        }

        return true;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Arg 1: Subcommand
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();

            // Everyone can check
            completions.add("check");

            // Admin only
            if (sender.hasPermission("login.credits.admin")) {
                completions.add("add");
                completions.add("remove");
                completions.add("set");
            }

            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        // Arg 2: Player Name (for all commands)
        if (args.length == 2) {
            if (sender.hasPermission("login.credits.admin") || sender.hasPermission("login.credits.check.others")) {
                return null; // Return null to let Bukkit handle player names
            }
        }

        // Arg 3: Amount (for add/remove/set)
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if (sender.hasPermission("login.credits.admin") && (sub.equals("add") || sub.equals("remove") || sub.equals("set"))) {
                return Arrays.asList("10", "100", "1000");
            }
        }

        return Collections.emptyList();
    }
}