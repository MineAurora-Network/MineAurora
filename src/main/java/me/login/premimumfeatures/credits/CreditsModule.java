package me.login.premimumfeatures.credits;

import me.login.Login;

public class CreditsModule {

    private final Login plugin;
    private CreditsDatabase database;
    private CreditsManager manager;

    public CreditsModule(Login plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // Initialize Database
        this.database = new CreditsDatabase(plugin);

        // Initialize Manager (Handles logic and logging)
        this.manager = new CreditsManager(plugin, database);

        // Register Command
        plugin.getCommand("credits").setExecutor(new CreditsCommand(plugin, manager));

        plugin.getLogger().info("Credits Module Enabled!");
    }

    public void disable() {
        if (database != null) {
            database.close();
        }
        plugin.getLogger().info("Credits Module Disabled!");
    }

    public CreditsManager getManager() {
        return manager;
    }
}