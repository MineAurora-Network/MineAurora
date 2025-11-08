package me.login.misc.dailyreward;

import me.login.Login;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class DailyRewardCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final DailyRewardManager manager;

    public DailyRewardCommand(Login plugin, DailyRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        // The manager handles logic for ranked vs default
        // Ranked players get the GUI
        // Default players get the reward directly

        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            // This is an "internal" command for the manager to open the GUI
            // This simplifies the logic and ensures ranked players always see the GUI
            if (player.hasPermission("mineaurora.dailyreward.rank")) {
                DailyRewardGUI gui = new DailyRewardGUI(plugin, manager); // We can create a new instance
                gui.openGUI(player);
            } else {
                player.sendMessage(manager.getPrefix().append(manager.getMiniMessage().deserialize("<red>Only ranked players can use the GUI.</red>")));
            }
            return true;
        }

        // Default action: /dr or /dailyreward
        manager.attemptClaim(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList(); // No subcommands to suggest
    }
}