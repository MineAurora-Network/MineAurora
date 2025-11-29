package me.login.misc.milestones;

import me.login.Login;
import me.login.misc.tokens.TokenManager;

public class MilestoneModule {

    private final Login plugin;
    private MilestoneDatabase database;
    private MilestoneManager manager;
    private MilestoneGUI gui;

    public MilestoneModule(Login plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getLogger().info("Initializing Milestone Module...");

        // 1. Database
        this.database = new MilestoneDatabase(plugin);
        this.database.connect();

        // 2. Logger (Get JDA from LagClearLogger)
        net.dv8tion.jda.api.JDA jda = null;
        if (plugin.getLagClearLogger() != null) {
            jda = plugin.getLagClearLogger().getJDA();
        }
        MilestoneLogger logger = new MilestoneLogger(plugin, jda);

        // 3. Manager (Needs TokenManager)
        TokenManager tokenManager = plugin.getTokenManager();
        if (tokenManager == null) {
            plugin.getLogger().severe("TokenManager not found! Milestone Module cannot reward tokens.");
            return;
        }
        this.manager = new MilestoneManager(plugin, database, logger, tokenManager);

        // 4. GUI & Listeners
        this.gui = new MilestoneGUI(plugin, manager);
        plugin.getServer().getPluginManager().registerEvents(gui, plugin);
        plugin.getServer().getPluginManager().registerEvents(new MilestoneListener(manager, database), plugin);

        // 5. NPC Listener
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            plugin.getServer().getPluginManager().registerEvents(new MilestoneNPCListener(plugin, gui), plugin);
        }

        plugin.getLogger().info("Milestone Module enabled.");
    }

    public void shutdown() {
        if (database != null) database.disconnect();
    }
}