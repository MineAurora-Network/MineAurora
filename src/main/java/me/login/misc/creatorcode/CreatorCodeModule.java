package me.login.misc.creatorcode;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.PluginCommand;

import java.util.logging.Level;

public class CreatorCodeModule {

    private final Login plugin;
    private final CreatorCodeManager manager;
    private final CreatorCodeLogger logger;
    private final Component serverPrefix;

    public CreatorCodeModule(Login plugin) {
        this.plugin = plugin;
        this.manager = new CreatorCodeManager(plugin);
        this.logger = new CreatorCodeLogger(plugin);

        // Load and deserialize the server prefix
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[Server]</gray><white>: </white>");
        this.serverPrefix = MiniMessage.miniMessage().deserialize(prefixString);
    }

    public boolean init(LagClearLogger lagClearLogger) { // <-- This is the correct method signature
        try {
            plugin.getLogger().info("Initializing CreatorCodeModule...");

            // Load codes from creatorcodes.yml
            this.manager.loadCodes();

            // Initialize the logger with the JDA instance
            if (lagClearLogger != null && lagClearLogger.getJDA() != null) {
                this.logger.init(lagClearLogger.getJDA());
                plugin.getLogger().info("CreatorCode Logger initialized.");
            } else {
                plugin.getLogger().warning("CreatorCodeModule JDA instance is null! Discord logging will be disabled.");
            }

            // Register Admin Command
            CreatorCodeCommand adminCommand = new CreatorCodeCommand(this.manager, this.logger, this.serverPrefix);
            PluginCommand creatorCodeCmd = plugin.getCommand("creatorcode");
            if (creatorCodeCmd != null) {
                creatorCodeCmd.setExecutor(adminCommand);
                creatorCodeCmd.setTabCompleter(adminCommand);
            } else {
                plugin.getLogger().severe("Command 'creatorcode' not found in plugin.yml!");
                return false;
            }

            // Register Player Command
            ApplyCreatorCodeCommand playerCommand = new ApplyCreatorCodeCommand(this.manager, this.logger, this.serverPrefix, this.plugin); // Pass plugin instance
            PluginCommand creditCodeCmd = plugin.getCommand("creditcode");
            if (creditCodeCmd != null) {
                creditCodeCmd.setExecutor(playerCommand);
                // No TabCompleter needed for SignGUI command
            } else {
                plugin.getLogger().severe("Command 'creditcode' not found in plugin.yml!");
                return false;
            }

            // Register Player Command Alias (as requested)
            PluginCommand removeCreditCodeCmd = plugin.getCommand("removecreditcode");
            if (removeCreditCodeCmd != null) {
                // Note: This command is handled by CreatorCodeCommand.java for admin removal
                removeCreditCodeCmd.setExecutor(adminCommand);
                removeCreditCodeCmd.setTabCompleter(adminCommand);
            } else {
                plugin.getLogger().severe("Command 'removecreditcode' not found in plugin.yml!");
                return false;
            }


            plugin.getLogger().info("CreatorCodeModule enabled successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize CreatorCodeModule", e);
            return false;
        }
    }

    public void shutdown() {
        plugin.getLogger().info("Shutting down CreatorCodeModule...");
        // Any shutdown logic (like saving) would go here,
        // but our manager saves on modification.
    }

    public CreatorCodeManager getManager() {
        return manager;
    }

    public CreatorCodeLogger getLogger() {
        return logger;
    }
}