package me.login.misc.rank;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import org.bukkit.command.PluginCommand;

import java.util.logging.Level;

public class RankModule {

    private final Login plugin;
    private final RankDatabase database;
    private final RankLogger logger;
    private final RankManager manager;
    private final Component serverPrefix;

    public RankModule(Login plugin) {
        this.plugin = plugin;
        this.database = new RankDatabase(plugin);
        this.logger = new RankLogger(plugin);

        // Load and deserialize the server prefix
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[Server]</gray><white>: </white>");
        this.serverPrefix = MiniMessage.miniMessage().deserialize(prefixString);

        // Manager will be initialized in init() after we get LuckPerms
        this.manager = new RankManager(plugin, this.database, this.logger, this.serverPrefix);
    }

    /**
     * Initializes the Rank Module.
     * @param lagClearLogger The shared JDA logger.
     * @param luckPerms The LuckPerms API instance.
     * @return true if initialization was successful.
     */
    public boolean init(LagClearLogger lagClearLogger, LuckPerms luckPerms) {
        try {
            plugin.getLogger().info("Initializing RankModule...");

            // 1. Connect to the database
            if (!this.database.connect()) {
                plugin.getLogger().severe("Failed to connect to the Rank database!");
                return false;
            }

            // 2. Initialize the Discord logger
            if (lagClearLogger != null && lagClearLogger.getJDA() != null) {
                this.logger.init(lagClearLogger.getJDA());
                plugin.getLogger().info("Rank Logger initialized.");
            } else {
                plugin.getLogger().warning("RankModule JDA instance is null! Discord logging will be disabled.");
            }

            // 3. Pass LuckPerms to the manager and load tasks
            this.manager.init(luckPerms);
            this.manager.loadScheduledTasks(); // Reschedule temporary ranks

            // 4. Register Commands
            RankCommand rankCommand = new RankCommand(this.manager, luckPerms, this.serverPrefix);
            PluginCommand cmd = plugin.getCommand("rank");
            if (cmd != null) {
                cmd.setExecutor(rankCommand);
                cmd.setTabCompleter(rankCommand);
            } else {
                plugin.getLogger().severe("Command 'rank' not found in plugin.yml!");
                return false;
            }

            plugin.getLogger().info("RankModule enabled successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize RankModule", e);
            return false;
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down RankModule...");
        if (manager != null) {
            manager.shutdown(); // Cancel any running tasks
        }
        if (database != null) {
            database.disconnect();
        }
    }
}