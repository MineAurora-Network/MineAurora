package me.login.misc.dailyquests;

import me.login.Login;
import me.login.clearlag.LagClearModule;
import me.login.misc.dailyquests.commands.QuestsCommand;
import me.login.misc.dailyquests.listeners.QuestsGuiListener;
import me.login.misc.dailyquests.listeners.QuestsNpcListener;
import me.login.misc.dailyquests.listeners.QuestProgressListener;
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public class QuestsModule {

    private final Login plugin;
    private final LagClearModule lagClearModule; // Keep this
    private QuestsDatabase questsDatabase;
    private QuestManager questManager;
    private QuestsLogger questsLogger;
    private QuestsGui questsGui;
    private File questsFile;
    private FileConfiguration questsConfig;

    public QuestsModule(Login plugin, LagClearModule lagClearModule) { // Keep this constructor
        this.plugin = plugin;
        this.lagClearModule = lagClearModule;
    }

    public void enable() {
        // 1. Initialize Database
        this.questsDatabase = new QuestsDatabase(this); // Pass this module
        this.questsDatabase.connect();

        // 2. Initialize Logger
        String channelId = plugin.getConfig().getString("quests-log-channel-id", "");
        if (channelId.isEmpty() || plugin.getLagClearLogger() == null) { // <-- Safety check added
            plugin.getLogger().warning("Quests log channel ID not set or LagClearLogger not ready! Discord logging will be disabled.");
            this.questsLogger = null;
        } else {
            // JDA is retrieved from lagClearModule inside the logger itself.
            // THIS IS THE FIX: Use plugin.getLagClearLogger()
            this.questsLogger = new QuestsLogger(plugin.getLagClearLogger(), channelId);
        }

        // 3. Load quests.yml
        loadQuestsConfig();

        // 4. Initialize Manager
        this.questManager = new QuestManager(this);
        this.questManager.loadQuests();

        // 5. Initialize GUI
        this.questsGui = new QuestsGui(this);

        // 6. Register Commands
        plugin.getCommand("quests").setExecutor(new QuestsCommand(this));

        // 7. Register Listeners
        Bukkit.getPluginManager().registerEvents(new QuestsGuiListener(this), plugin);
        Bukkit.getPluginManager().registerEvents(new QuestProgressListener(this), plugin);

        // Check for Citizens API and register NPC listener
        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            Bukkit.getPluginManager().registerEvents(new QuestsNpcListener(this), plugin);
            plugin.getLogger().info("Citizens found, registered Quests NPC listener.");
        } else {
            plugin.getLogger().warning("Citizens plugin not found. Quest NPC interaction will not work.");
        }

        plugin.getLogger().info("Daily Quests module enabled successfully.");
    }

    public void disable() {
        if (this.questsDatabase != null) {
            this.questsDatabase.disconnect();
        }
        plugin.getLogger().info("Daily Quests module disabled.");
    }

    private void loadQuestsConfig() {
        questsFile = new File(plugin.getDataFolder(), "quests.yml");
        if (!questsFile.exists()) {
            plugin.saveResource("quests.yml", false);
        }
        questsConfig = YamlConfiguration.loadConfiguration(questsFile);
    }

    public Login getPlugin() {
        return plugin;
    }

    public QuestsDatabase getQuestsDatabase() {
        return questsDatabase;
    }

    public QuestManager getQuestManager() {
        return questManager;
    }

    public QuestsLogger getQuestsLogger() {
        return questsLogger;
    }

    public QuestsGui getQuestsGui() {
        return questsGui;
    }

    public FileConfiguration getQuestsConfig() {
        return questsConfig;
    }
}