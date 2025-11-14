package me.login.misc.generator;

import me.login.Login;
import me.login.clearlag.LagClearLogger;

public class GenModule {
    private final Login plugin;
    private final LagClearLogger parentLogger;

    private GenDatabase database;
    private GenItemManager itemManager;
    private GenLogger logger;
    private GenManager manager;

    public GenModule(Login plugin, LagClearLogger parentLogger) {
        this.plugin = plugin;
        this.parentLogger = parentLogger;
    }

    public void init() {
        this.database = new GenDatabase(plugin);
        this.itemManager = new GenItemManager(plugin);
        this.logger = new GenLogger(plugin, parentLogger.getJDA());
        this.manager = new GenManager(plugin, database, itemManager, logger);

        manager.loadGenerators();

        plugin.getServer().getPluginManager().registerEvents(new GenListener(plugin, manager, itemManager), plugin);

        GenCommand genCmd = new GenCommand(plugin, manager, itemManager);
        plugin.getCommand("generator").setExecutor(genCmd);
        plugin.getCommand("generator").setTabCompleter(genCmd); // Register Tab Completer

        plugin.getCommand("sellgendrop").setExecutor(new SellGenDropCommand(plugin, itemManager));
    }

    public void shutdown() {
        if (manager != null) manager.shutdown();
    }
}