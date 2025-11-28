package me.login.premiumfeatures.creatorcode;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.premiumfeatures.credits.CreditsDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;

import java.util.logging.Level;

public class CreatorCodeModule {

    private final Login plugin;
    private final CreditsDatabase database;
    private CreatorCodeManager manager;
    private CreatorCodeLogger logger;
    private final Component serverPrefix;

    public CreatorCodeModule(Login plugin, CreditsDatabase database) {
        this.plugin = plugin;
        this.database = database;

        // Load and deserialize the server prefix
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[Server]</gray><white>: </white>");
        this.serverPrefix = MiniMessage.miniMessage().deserialize(prefixString);
    }

    public boolean init(LagClearLogger lagClearLogger) {
        try {
            plugin.getLogger().info("Initializing CreatorCodeModule...");

            // Initialize Logger
            this.logger = new CreatorCodeLogger(plugin);
            if (lagClearLogger != null && lagClearLogger.getJDA() != null) {
                this.logger.init(lagClearLogger.getJDA());
            } else {
                plugin.getLogger().warning("CreatorCodeModule: Discord JDA is missing. Logging disabled.");
            }

            // Initialize Manager with Database
            this.manager = new CreatorCodeManager(plugin, database);
            this.manager.loadCodes(); // Cache codes from DB

            // Register Admin Command (/creatorcode)
            CreatorCodeCommand adminCommand = new CreatorCodeCommand(this.manager, this.logger, this.serverPrefix);
            PluginCommand creatorCodeCmd = plugin.getCommand("creatorcode");
            if (creatorCodeCmd != null) {
                creatorCodeCmd.setExecutor(adminCommand);
                creatorCodeCmd.setTabCompleter(adminCommand);
            }

            // Register Player Command (/creditcode alias: /applycreatorcode)
            ApplyCreatorCodeCommand playerCommand = new ApplyCreatorCodeCommand(this.manager, this.logger, this.serverPrefix, this.plugin);
            PluginCommand creditCodeCmd = plugin.getCommand("creditcode");
            if (creditCodeCmd != null) {
                creditCodeCmd.setExecutor(playerCommand);
            }

            // Register Remove Alias (/removecreditcode)
            PluginCommand removeCreditCodeCmd = plugin.getCommand("removecreditcode");
            if (removeCreditCodeCmd != null) {
                removeCreditCodeCmd.setExecutor(adminCommand);
                removeCreditCodeCmd.setTabCompleter(adminCommand);
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize CreatorCodeModule", e);
            return false;
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down CreatorCodeModule...");
    }

    public CreatorCodeManager getManager() {
        return manager;
    }
}