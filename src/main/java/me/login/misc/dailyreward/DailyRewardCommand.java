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
    private final DailyRewardGUI gui; // Singleton GUI instance

    public DailyRewardCommand(Login plugin, DailyRewardManager manager, DailyRewardGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("gui")) {
            if (player.hasPermission("mineaurora.dailyreward.rank")) {
                // Use singleton GUI instead of creating a new one every time
                gui.openGUI(player);
            } else {
                player.sendMessage(manager.getPrefix().append(manager.getMiniMessage().deserialize("<red>Only ranked players can use the GUI.</red>")));
            }
            return true;
        }

        // If ranked player runs /dailyreward without args, what should happen?
        // Manager's attemptClaim handles the logic (opening GUI if has perm, or claiming default)
        if (player.hasPermission("mineaurora.dailyreward.rank")) {
            gui.openGUI(player);
        } else {
            manager.claimDefaultReward(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        return Collections.emptyList();
    }
}