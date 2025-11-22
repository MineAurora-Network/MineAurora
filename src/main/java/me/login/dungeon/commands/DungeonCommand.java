package me.login.dungeon.commands;

import me.login.dungeon.gui.DungeonGUI;
import me.login.dungeon.manager.DungeonRewardManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DungeonCommand implements CommandExecutor {

    private final DungeonRewardManager rewardManager;

    public DungeonCommand(DungeonRewardManager rewardManager) {
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (args.length > 0 && args[0].equalsIgnoreCase("rngmeter")) {
            DungeonGUI.openRNGMeter(player, rewardManager);
            return true;
        }

        return true;
    }
}