package me.login.discord.linking;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.DiscordCommandRegistrar;
import me.login.discord.moderation.discord.DiscordModConfig;
import me.login.discord.moderation.discord.DiscordModDatabase;
import me.login.misc.rank.RankModule;
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

    public DiscordLinkingModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init(LagClearLogger lagClearLogger, DiscordModConfig discordModConfig, DiscordModDatabase modDatabase, RankModule rankModule, ModerationModule moderationModule) {
        plugin.getLogger().info("Initializing DiscordLinking components...");

        this.discordLinkDatabase = new DiscordLinkDatabase(plugin);
        discordLinkDatabase.connect();

        JDA sharedJda = lagClearLogger.getJDA();
        if (sharedJda == null) return false;

        this.discordLinkLogger = new DiscordLinkLogger(plugin, sharedJda);
        this.discordCommandLogger = new DiscordCommandLogger(plugin, sharedJda);

        // Pass ModDatabase
        this.discordLinking = new DiscordLinking(plugin, discordModConfig, modDatabase, discordLinkLogger, rankModule, moderationModule);

        // Start Bot and Register Commands
        String token = plugin.getConfig().getString("bot-token");
        if (token == null || token.isEmpty()) return false;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Pass Database to startBot
                this.mainBotJda = discordLinking.startBot(token, discordCommandLogger, moderationModule, rankModule, modDatabase);
                if (this.mainBotJda != null) {
                    DiscordCommandRegistrar.register(mainBotJda, plugin, discordCommandLogger);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

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