package me.login.misc.dailyquests.commands;

import me.login.misc.dailyquests.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class QuestsCommand implements CommandExecutor, TabCompleter {
    // --- DUPLICATE IMPORTS AND CLASS DECLARATION REMOVED ---

    private final QuestsModule module;
    private final QuestsGui questsGui;
    private final QuestManager questManager;
    private final Component serverPrefix;

    // List for tab completion
    private static final List<String> SUBCOMMANDS = Arrays.asList("progress", "reset"); // <-- ADDED "reset"

    public QuestsCommand(QuestsModule module) {
        this.module = module;
        this.questsGui = module.getQuestsGui();
        this.questManager = module.getQuestManager();
        this.serverPrefix = module.getQuestManager().getServerPrefix();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("progress")) {
                showQuestProgress(player);
                return true;
            } else if (args[0].equalsIgnoreCase("reset")) { // <-- ADDED ELSE IF
                questManager.resetActiveQuest(player);
                return true;
            }
        }

        // Default action: open GUI
        questsGui.openMainQuestGui(player);
        return true;
    }

    private void showQuestProgress(Player player) {
        PlayerQuestData data = questManager.getPlayerQuestData(player);
        if (data == null) {
            player.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<red>Your quest data is loading. Please try again.</red>")));
            return;
        }

        // Ensure quests are loaded/reset
        questManager.checkAndResetDailies(data);

        if (data.getActiveQuest() == null) {
            player.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize("<gray>You do not have an active quest. Type <yellow>/quests</yellow> to accept one!</gray>")));
            return;
        }

        Quest activeQuest = data.getActiveQuest();
        player.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize(
                "<green>Active Quest: <white>" + activeQuest.getObjectiveDescription() + "</white></green>"
        )));
        player.sendMessage(serverPrefix.append(MiniMessage.miniMessage().deserialize(
                "<green>Progress: <yellow>" + data.getActiveQuestProgress() + " / " + activeQuest.getRequiredAmount() + "</yellow></green>"
        )));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 1) {
            // If user is typing the first argument, show matching subcommands
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new ArrayList<>());
        }
        // No suggestions for other arguments
        return Collections.emptyList();
    }
}