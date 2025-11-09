package me.login.clearlag;

import me.login.Login; // <-- ADDED
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LagClearCommand implements CommandExecutor, TabCompleter {

    private final Login plugin; // <-- CHANGED
    private final LagClearConfig lagClearConfig;

    public LagClearCommand(Login plugin, LagClearConfig lagClearConfig) { // <-- CHANGED
        this.plugin = plugin;
        this.lagClearConfig = lagClearConfig;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("msgtoggle")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(lagClearConfig.formatMessage("<red>This command can only be run by a player.")); // <-- CHANGED
                return true;
            }

            boolean currentSetting = lagClearConfig.getPlayerToggle(player.getUniqueId());
            boolean newSetting = !currentSetting;
            lagClearConfig.setPlayerToggle(player.getUniqueId(), newSetting);

            String status = newSetting ? "<green>ENABLED</green>" : "<red>DISABLED</red>";
            player.sendMessage(lagClearConfig.formatMessage("Your automatic lag clear messages are now " + status + "<white>.")); // <-- CHANGED
            return true;
        }

        if (!sender.hasPermission("lagclear.admin")) {
            sender.sendMessage(lagClearConfig.formatMessage("<red>You do not have permission to use this command.</red>")); // <-- CHANGED
            return true;
        }

        if (args[0].equalsIgnoreCase("clean")) {
            sender.sendMessage(lagClearConfig.formatMessage("<gray>Running manual cleanup...</gray>")); // <-- CHANGED

            // Run the cleanup and get the count
            int removed = EntityCleanup.performCleanup(plugin); // <-- CHANGED
            sender.sendMessage(lagClearConfig.formatMessage("<green>Successfully removed " + removed + " items and entities.</green>")); // <-- CHANGED
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        // We can use MiniMessage for help, even for console
        sender.sendMessage(lagClearConfig.formatMessage("<gold>--- ClearLag Help ---</gold>"));
        if (sender.hasPermission("lagclear.admin")) {
            sender.sendMessage(lagClearConfig.formatMessage("<aqua>/lagclear clean</aqua><white> - Manually cleans all ground items, projectiles and entities.</white>"));
        }
        sender.sendMessage(lagClearConfig.formatMessage("<aqua>/lagclear msgtoggle</aqua><white> - Toggles automatic cleanup messages for you.</white>"));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            completions.add("msgtoggle");
            if (sender.hasPermission("lagclear.admin")) {
                completions.add("clean");
            }
            return StringUtil.copyPartialMatches(args[0], completions, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}