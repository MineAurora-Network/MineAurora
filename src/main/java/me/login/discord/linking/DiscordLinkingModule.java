package me.login.discord.linking;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.DiscordCommandRegistrar;
import me.login.discord.moderation.DiscordModConfig;
import me.login.misc.rank.RankManager;
import me.login.moderation.ModerationModule;
import net.dv8tion.jda.api.JDA;
import org.bukkit.Bukkit;

public class DiscordLinkingModule {

    private final Login plugin;
    private DiscordLinking discordLinking;
    private DiscordLinkDatabase discordLinkDatabase;
    private DiscordLinkLogger discordLinkLogger;
    private JDA mainBotJda;
    private DiscordCommandLogger discordCommandLogger;
    private RankManager rankManager;

    public DiscordLinkingModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init(LagClearLogger lagClearLogger, DiscordModConfig discordModConfig, RankManager rankManager, ModerationModule moderationModule) {
        plugin.getLogger().info("Initializing DiscordLinking components...");

        this.discordLinkDatabase = new DiscordLinkDatabase(plugin);
        discordLinkDatabase.connect();

        JDA sharedJda = lagClearLogger.getJDA();
        if (sharedJda == null) return false;

        this.discordLinkLogger = new DiscordLinkLogger(plugin, sharedJda);
        this.discordCommandLogger = new DiscordCommandLogger(plugin, sharedJda);
        this.rankManager = rankManager;

        // Initialize Main Bot Class
        this.discordLinking = new DiscordLinking(plugin, discordModConfig, discordLinkLogger, rankManager);

        // Start Bot and Register Commands
        String token = plugin.getConfig().getString("bot-token");
        if (token == null || token.isEmpty()) return false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                this.mainBotJda = discordLinking.startBot(token, discordCommandLogger, moderationModule);
                if (this.mainBotJda != null) {
                    DiscordCommandRegistrar.register(mainBotJda, plugin, discordCommandLogger);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        // Register Bukkit Commands
        DiscordLinkCmd discordCmd = new DiscordLinkCmd(plugin, this);
        plugin.getCommand("discord").setExecutor(discordCmd);
        plugin.getCommand("unlink").setExecutor(discordCmd);
        plugin.getCommand("adminunlink").setExecutor(discordCmd);

        return true;
    }

    public void shutdown() {
        if (discordLinking != null) discordLinking.shutdown();
        if (discordLinkDatabase != null) discordLinkDatabase.disconnect();
    }

    public DiscordLinking getDiscordLinking() { return discordLinking; }
    public DiscordLinkDatabase getDiscordLinkDatabase() { return discordLinkDatabase; }
}