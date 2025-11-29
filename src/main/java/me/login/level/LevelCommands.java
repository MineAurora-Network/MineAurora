package me.login.level;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer; // Added Legacy serializer
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LevelCommands implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final LevelManager manager;
    private final LevelLogger logger;
    private final MiniMessage mm;
    private final Component serverPrefix;

    public LevelCommands(Login plugin, LevelManager manager, LevelLogger logger) {
        this.plugin = plugin;
        this.manager = manager;
        this.logger = logger;
        this.mm = MiniMessage.miniMessage();

        // Load prefix using MiniMessage
        String prefixStr = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>: ");
        this.serverPrefix = mm.deserialize(prefixStr);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        // Player Command: /lifesteallevel (Stats)
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Only players can check their own stats.")));
                return true;
            }
            sendPlayerStats(player, player);
            return true;
        }

        // Admin Commands
        if (!sender.hasPermission("lifesteallevel.admin")) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You do not have permission.")));
            return true;
        }

        String sub = args[0].toLowerCase();

        // /lifesteallevel checklevel <player>
        if (sub.equals("checklevel") && args.length == 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player not found.")));
                return true;
            }
            int lvl = manager.getLevel(target);
            sender.sendMessage(serverPrefix.append(mm.deserialize("<gray>Player <yellow>" + target.getName() + "</yellow> is level <gold>" + lvl + "</gold>.")));
            return true;
        }

        // /lifesteallevel checkxp <player>
        if (sub.equals("checkxp") && args.length == 2) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player not found.")));
                return true;
            }
            sendPlayerStats(sender, target);
            return true;
        }

        // /lifesteallevel setlevel <player> <level>
        if (sub.equals("setlevel") && args.length == 3) {
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player not found.")));
                return true;
            }
            try {
                int newLevel = Integer.parseInt(args[2]);
                manager.setLevel(target, newLevel);
                manager.setXp(target, 0); // Reset XP on force set

                sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Set " + target.getName() + "'s level to " + newLevel + ".")));
                logger.logAdminSet(sender.getName(), target.getName(), newLevel);
            } catch (NumberFormatException e) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Invalid level number.")));
            }
            return true;
        }

        return true;
    }

    private void sendPlayerStats(CommandSender viewer, Player target) {
        int level = manager.getLevel(target);
        int xp = manager.getXp(target);
        int needed = manager.getXpRequiredForNextLevel(level);

        // Progress Bar
        int totalBars = 20;
        float percent = (float) xp / needed;
        int filled = (int) (totalBars * percent);
        StringBuilder bar = new StringBuilder("<green>");
        for (int i=0; i<filled; i++) bar.append("|");
        bar.append("<gray>");
        for (int i=filled; i<totalBars; i++) bar.append("|");

        int displayPercent = (int) (percent * 100);

        // Header/Footer style from images
        viewer.sendMessage(mm.deserialize("<dark_gray><st>                      </st> <gold>Lifesteal Level <dark_gray><st>                      </st>"));
        viewer.sendMessage(Component.empty());
        viewer.sendMessage(mm.deserialize(" <gray>Current Level: <gold>" + level));
        viewer.sendMessage(mm.deserialize(" <gray>XP: <yellow>" + xp + " <gray>/ <yellow>" + needed));
        viewer.sendMessage(mm.deserialize(" <gray>Progress: <dark_gray>[" + bar + "<dark_gray>] <green>" + displayPercent + "%"));
        viewer.sendMessage(Component.empty());
        viewer.sendMessage(mm.deserialize("<dark_gray><st>                                                      </st>"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("lifesteallevel.admin")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            List<String> subcommands = new ArrayList<>();
            subcommands.add("checklevel");
            subcommands.add("checkxp");
            subcommands.add("setlevel");
            return StringUtil.copyPartialMatches(args[0], subcommands, new ArrayList<>());
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("checklevel") || sub.equals("checkxp") || sub.equals("setlevel")) {
                return null; // Return null to suggest online player names
            }
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("setlevel")) {
            // Suggest levels 0-130
            List<String> levels = new ArrayList<>();
            for (int i = 0; i <= 130; i += 10) { // Suggest increments of 10 to not spam list
                levels.add(String.valueOf(i));
            }
            return StringUtil.copyPartialMatches(args[2], levels, new ArrayList<>());
        }

        return Collections.emptyList();
    }
}