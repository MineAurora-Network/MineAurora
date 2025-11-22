package me.login.loginsystem;

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase;
import me.login.discord.linking.DiscordLinkingModule;
import net.dv8tion.jda.api.JDA;

public class LoginModule {

    private final Login plugin;
    private LoginDatabase loginDatabase;
    private LoginSystem loginSystem;
    private LoginSystemLogger loginSystemLogger;
    private ParkourManager parkourManager;

    public LoginModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init(DiscordLinkingModule discordLinkingModule, JDA jda) {
        try {
            this.loginDatabase = new LoginDatabase(plugin);
            this.loginDatabase.connect();
            if (this.loginDatabase.getConnection() == null) {
                plugin.getLogger().severe("Failed to connect to Login Database!");
                return false;
            }

            // Pass JDA directly to our specific logger
            this.loginSystemLogger = new LoginSystemLogger(plugin, jda);

            DiscordLinkDatabase discordLinkDatabase = (discordLinkingModule != null) ? discordLinkingModule.getDiscordLinkDatabase() : null;
            this.loginSystem = new LoginSystem(plugin, loginDatabase, discordLinkDatabase, loginSystemLogger);
            plugin.getServer().getPluginManager().registerEvents(loginSystem, plugin);

            // Initialize Parkour Manager with Logger
            this.parkourManager = new ParkourManager(plugin, loginDatabase, loginSystemLogger);

            registerCommands(discordLinkDatabase);

            plugin.getLogger().info("LoginModule initialized successfully.");
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Error initializing LoginModule: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void registerCommands(DiscordLinkDatabase discordLinkDb) {
        LoginSystemCmd loginCmd = new LoginSystemCmd(plugin, loginSystem, loginDatabase, discordLinkDb, loginSystemLogger, parkourManager);
        LoginSystemAdminCmd adminCmd = new LoginSystemAdminCmd(plugin, loginSystem, loginDatabase, discordLinkDb, loginSystemLogger);

        setCommandExecutor("login", loginCmd);
        setCommandExecutor("register", loginCmd);
        setCommandExecutor("changepassword", loginCmd);
        setCommandExecutor("loginparkour", loginCmd);
        setCommandExecutor("logindisplaykill", loginCmd);

        setCommandExecutor("unregister", adminCmd);
        setCommandExecutor("loginhistory", adminCmd);
        setCommandExecutor("checkalt", adminCmd);
        setCommandExecutor("adminchangepass", adminCmd);
    }

    private void setCommandExecutor(String commandName, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand command = plugin.getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            plugin.getLogger().warning("Command '" + commandName + "' not found in plugin.yml!");
        }
    }

    public void shutdown() {
        if (loginDatabase != null) {
            loginDatabase.disconnect();
        }
    }

    public LoginSystem getLoginSystem() { return loginSystem; }
    public LoginDatabase getLoginDatabase() { return loginDatabase; }
    public LoginSystemLogger getLogger() { return loginSystemLogger; }
    public ParkourManager getParkourManager() { return parkourManager; }
}