package me.login.moderation;

import me.login.Login;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.CommandExecutor;
import me.login.moderation.staff.StaffModule;

public class ModerationModule {

    private final Login plugin;
    private ModerationDatabase database;
    private ModerationLogger logger;
    private ModerationTabCompleter tabCompleter;

    public ModerationModule(Login plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        plugin.getLogger().info("Initializing ModerationModule...");

        this.logger = new ModerationLogger(plugin);
        this.database = new ModerationDatabase(plugin);
        this.tabCompleter = new ModerationTabCompleter();

        StaffModule staffModule = new StaffModule(plugin, database);
        staffModule.enable();

        // Mute Commands
        MuteCommand muteCmd = new MuteCommand(plugin, database, logger);
        registerCommand("mute", muteCmd, true);
        registerCommand("muteinfo", muteCmd, true);
        registerCommand("unmute", muteCmd, true);

        // Ban Commands
        BanCommand banCmd = new BanCommand(plugin, database, logger);
        registerCommand("ban", banCmd, true);
        registerCommand("ipban", banCmd, true);
        registerCommand("baninfo", banCmd, true);
        registerCommand("unban", banCmd, true);
        registerCommand("unbanip", banCmd, true);

        // Kick Command
        registerCommand("kick", new KickCommand(plugin, logger), true);

        // History Commands
        HistoryCommand histCmd = new HistoryCommand(plugin, database);
        registerCommand("mutehistory", histCmd, true);
        registerCommand("banhistory", histCmd, true);

        // Inventory Check
        CheckInvCommand checkInv = new CheckInvCommand(plugin);
        registerCommand("checkinv", checkInv, false);
        registerCommand("admincheckinv", checkInv, false);

        // Listener
        plugin.getServer().getPluginManager().registerEvents(new ModerationListener(plugin, database), plugin);
    }

    public void disable() {
        if (database != null) {
            database.closeConnection();
        }
    }

    private void registerCommand(String name, CommandExecutor executor, boolean useTabCompleter) {
        PluginCommand cmd = plugin.getCommand(name);
        if (cmd != null) {
            cmd.setExecutor(executor);
            if (useTabCompleter) {
                cmd.setTabCompleter(this.tabCompleter);
            }
        } else {
            plugin.getLogger().warning("Failed to register moderation command: " + name);
        }
    }

    public ModerationDatabase getDatabase() {
        return database;
    }
}