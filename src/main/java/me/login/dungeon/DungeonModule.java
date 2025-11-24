package me.login.dungeon;

import me.login.Login;
import me.login.dungeon.commands.AdminCommands;
import me.login.dungeon.commands.DungeonTabCompleter;
import me.login.dungeon.data.Database;
import me.login.dungeon.game.GameManager;
import me.login.dungeon.listeners.DungeonListener;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.manager.DungeonRewardManager;
import me.login.dungeon.utils.DungeonLogger;
import me.login.dungeon.utils.DungeonPlaceholder;
import org.bukkit.Bukkit;

public class DungeonModule {

    private final Login plugin;
    private final Database database;
    private final DungeonManager dungeonManager;
    private final GameManager gameManager;
    private final DungeonRewardManager rewardManager;
    private final DungeonLogger logger;

    public DungeonModule(Login plugin) {
        this.plugin = plugin;

        // 1. Utils & Data
        this.database = new Database(plugin);
        this.logger = new DungeonLogger(plugin);
        this.rewardManager = new DungeonRewardManager(plugin, database);

        // 2. Managers
        this.dungeonManager = new DungeonManager(plugin, database);
        this.gameManager = new GameManager(plugin, dungeonManager);

        // 3. Register Commands
        if (plugin.getCommand("dungeon") != null) {
            plugin.getCommand("dungeon").setExecutor(new AdminCommands(plugin, dungeonManager, gameManager, rewardManager));
            plugin.getCommand("dungeon").setTabCompleter(new DungeonTabCompleter(dungeonManager, rewardManager));
        }

        // 4. Register Listeners
        Bukkit.getPluginManager().registerEvents(new DungeonListener(plugin, dungeonManager, gameManager, rewardManager, logger), plugin);

        // 5. Register Placeholders (Soft depend)
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DungeonPlaceholder(plugin, gameManager).register();
            plugin.getLogger().info("Dungeon Placeholders registered!");
        }

        plugin.getLogger().info("Dungeon Module loaded successfully!");
    }

    public void disable() {
        if (this.database != null) {
            this.database.close();
        }
        // Save all dungeons on disable to ensure data safety
        if (this.dungeonManager != null) {
            this.dungeonManager.saveAll();
        }
        // End all sessions
        if (this.gameManager != null) {
            this.gameManager.getAllSessions().forEach(session -> session.cleanup());
        }
    }

    // --- ADD THESE METHODS HERE ---

    public GameManager getGameManager() {
        return gameManager;
    }

    public DungeonManager getDungeonManager() {
        return dungeonManager;
    }

    public DungeonRewardManager getRewardManager() {
        return rewardManager;
    }
}