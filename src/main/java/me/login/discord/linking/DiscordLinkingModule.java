package me.login.discord.linking;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.DiscordCommandRegistrar;
import me.login.discord.moderation.DiscordModConfig;
import me.login.misc.rank.RankManager; // <-- IMPORT
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;

public class DiscordLinkingModule {

    private final Login plugin;
    private DiscordLinking discordLinking;
    private DiscordLinkDatabase discordLinkDatabase;
    private DiscordLinkLogger discordLinkLogger;
    private JDA mainBotJda;
    // --- NEW FIELD ---
    private DiscordCommandLogger discordCommandLogger;
    // --- END FIELD ---
    private RankManager rankManager; // <-- ADD

    public DiscordLinkingModule(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes all components for the Discord linking system.
     * @param lagClearLogger The logger module providing the shared JDA.
     * @param discordModConfig The config for discord moderation commands.
     * @param rankManager The rank manager from the RankModule. // <-- ADDED
     * @return true if initialization was successful.
     */
    // --- UPDATED METHOD SIGNATURE ---
    public boolean init(LagClearLogger lagClearLogger, DiscordModConfig discordModConfig, RankManager rankManager) {
        plugin.getLogger().info("Initializing DiscordLinking components...");

        // 1. Initialize Database
        this.discordLinkDatabase = new DiscordLinkDatabase(plugin);
        discordLinkDatabase.connect();
        if (discordLinkDatabase.getConnection() == null) {
            plugin.getLogger().severe("Failed to connect to DiscordLink database.");
            return false;
        }
        plugin.getLogger().info("DiscordLink database connected.");

        // 2. Get Shared JDA
        JDA sharedJda = lagClearLogger.getJDA();
        if (sharedJda == null) {
            plugin.getLogger().severe("Shared JDA from LagClearLogger is null! Cannot initialize DiscordLinking components.");
            return false;
        }

        // 3. Initialize Loggers (using shared JDA)
        this.discordLinkLogger = new DiscordLinkLogger(plugin, sharedJda);
        plugin.getLogger().info("DiscordLinkLogger initialized.");

        // --- NEW ---
        // 4. Initialize Command Logger (for main bot)
        this.discordCommandLogger = new DiscordCommandLogger(plugin, sharedJda);
        plugin.getLogger().info("DiscordCommandLogger initialized.");
        // --- END NEW ---

        // --- ADDED ---
        // 5. Store RankManager
        this.rankManager = rankManager;
        if (this.rankManager == null) {
            plugin.getLogger().warning("RankManager is null. Discord /rank command will be disabled.");
        }
        // --- END ---

        // 6. Initialize Main Bot Class (pass rankManager)
        // --- UPDATED CONSTRUCTOR ---
        this.discordLinking = new DiscordLinking(plugin, discordModConfig, discordLinkLogger, rankManager);
        plugin.getLogger().info("DiscordLinking main class initialized.");

        // 7. Start the Bot (asynchronously)
        //    This now starts the bot and registers commands
        String token = plugin.getConfig().getString("bot-token");
        if (token == null || token.isEmpty() || token.equals("YOUR_BOT_TOKEN_HERE")) {
            plugin.getLogger().severe("Bot token is not set in config.yml! Discord linking disabled.");
            return false;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Pass the command logger to the bot starter
                this.mainBotJda = discordLinking.startBot(token, discordCommandLogger);
                if (this.mainBotJda != null) {
                    plugin.getLogger().info("DiscordLinking Bot started successfully.");
                    // Register commands *after* bot is confirmed ready
                    DiscordCommandRegistrar.register(mainBotJda, plugin, discordCommandLogger);
                } else {
                    plugin.getLogger().severe("DiscordLinking Bot failed to start (JDA is null).");
                    plugin.getServer().getPluginManager().disablePlugin(plugin);
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Exception during Discord bot startup (Linking): " + e.getMessage());
                e.printStackTrace();
                plugin.getServer().getPluginManager().disablePlugin(plugin);
            }
        });

        // 8. Register Bukkit Commands
        DiscordLinkCmd discordCmd = new DiscordLinkCmd(plugin, this);
        plugin.getCommand("discord").setExecutor(discordCmd);
        plugin.getCommand("unlink").setExecutor(discordCmd);
        plugin.getCommand("adminunlink").setExecutor(discordCmd);

        plugin.getLogger().info("DiscordLinking components enabled.");
        return true;
    }

    /**
     * Shuts down all components of this module.
     */
    public void shutdown() {
        if (discordLinking != null) {
            discordLinking.shutdown(); // Shuts down mainBotJda
        }
        if (discordLinkDatabase != null) {
            discordLinkDatabase.disconnect();
        }
        // loggers don't need shutdown, as they use a shared JDA
    }

    // --- Getters ---

    public DiscordLinking getDiscordLinking() {
        return discordLinking;
    }

    public DiscordLinkDatabase getDiscordLinkDatabase() {
        return discordLinkDatabase;
    }

    public JDA getMainBotJda() {
        return mainBotJda;
    }

    public DiscordLinkLogger getDiscordLinkLogger() {
        return discordLinkLogger;
    }

    // --- NEW GETTER ---
    public DiscordCommandLogger getDiscordCommandLogger() {
        return discordCommandLogger;
    }
    // --- END GETTER ---
}