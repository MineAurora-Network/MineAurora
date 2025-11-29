package me.login.level;

import me.login.Login;
import me.login.level.listener.BlockBreakListener;
import me.login.level.listener.CombatListener;
import me.login.level.listener.PlayerActivityListener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class LevelModule implements Listener {

    private final Login plugin;
    private LevelDatabase database;
    private LevelManager manager;
    private LevelLogger logger;
    private LevelGUI gui;

    public LevelModule(Login plugin) {
        this.plugin = plugin;
    }

    public void init() {
        plugin.getLogger().info("Initializing Lifesteal Level Module...");

        // 1. Database
        this.database = new LevelDatabase(plugin);
        this.database.connect();

        // 2. Logger (Shared JDA)
        net.dv8tion.jda.api.JDA jda = (plugin.getLagClearLogger() != null) ? plugin.getLagClearLogger().getJDA() : null;
        this.logger = new LevelLogger(plugin, jda);

        // 3. Manager
        this.manager = new LevelManager(plugin, database, logger);

        // 4. GUI & NPC Listener
        this.gui = new LevelGUI(plugin);
        plugin.getServer().getPluginManager().registerEvents(this.gui, plugin);

        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            plugin.getServer().getPluginManager().registerEvents(new LevelNPCListener(plugin, gui), plugin);
        }

        // 5. Listeners
        plugin.getServer().getPluginManager().registerEvents(new BlockBreakListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new CombatListener(manager), plugin);
        plugin.getServer().getPluginManager().registerEvents(new PlayerActivityListener(manager), plugin);

        // Register this module as a listener for Join/Quit events to load data
        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        // 6. Commands
        LevelCommands cmd = new LevelCommands(plugin, manager, logger);
        plugin.getCommand("lifesteallevel").setExecutor(cmd);
        plugin.getCommand("lifesteallevel").setTabCompleter(cmd);

        plugin.getLogger().info("Lifesteal Level Module enabled.");
    }

    public void shutdown() {
        if (database != null) database.disconnect();
    }

    // --- Data Loading Listeners ---

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.loadData(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.unloadData(event.getPlayer().getUniqueId());
    }
}