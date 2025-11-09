package me.login.misc.playtimerewards;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class PlaytimeRewardCommand implements CommandExecutor {

    private final PlaytimeRewardGUI gui;
    private final PlaytimeRewardManager manager;

    public PlaytimeRewardCommand(PlaytimeRewardGUI gui, PlaytimeRewardManager manager) {
        this.gui = gui;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (manager.getPlayerData(player.getUniqueId()) == null) {
            player.sendMessage(manager.getPrefix().append(manager.getMiniMessage().deserialize("<red>Your playtime data is still loading. Please try again in a moment.</red>")));
            return true;
        }

        // --- FIX: Open the GUI to page 0 (which is Page 1) ---
        gui.openGUI(player, 0);
        return true;
    }
}