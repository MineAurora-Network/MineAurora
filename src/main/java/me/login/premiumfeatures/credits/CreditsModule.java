package me.login.premiumfeatures.credits;

import me.login.Login;

public class CreditsModule {

    private final Login plugin;
    private CreditsDatabase database;
    private CreditsManager manager;

    public CreditsModule(Login plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // 1. Initialize Database (Shared with CreatorCode)
        this.database = new CreditsDatabase(plugin);

        // 2. Initialize Manager
        this.manager = new CreditsManager(plugin, database);

        // 3. Register Command
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

    // Expose database for CreatorCodeModule to use
    public CreditsDatabase getDatabase() {
        return database;
    }
}