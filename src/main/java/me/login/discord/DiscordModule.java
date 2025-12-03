package me.login.discord;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.linking.DiscordLinkingModule;
import me.login.discord.moderation.discord.DiscordModConfig;
import me.login.discord.moderation.discord.DiscordModDatabase;
import me.login.discord.store.TicketModule;
import me.login.misc.rank.RankModule;
import me.login.moderation.ModerationModule;

public class DiscordModule {

    private final Login plugin;
    private DiscordLinkingModule linkingModule;
    private TicketModule ticketModule;
    private DiscordModConfig modConfig;
    private DiscordModDatabase modDatabase; // NEW

    public DiscordModule(Login plugin) {
        this.plugin = plugin;
    }

    public void init(LagClearLogger lagClearLogger, RankModule rankModule, ModerationModule moderationModule) {
        plugin.getLogger().info("Initializing DiscordModule...");

        if (lagClearLogger == null || lagClearLogger.getJDA() == null) {
            plugin.getLogger().severe("DiscordModule: Shared JDA is null. Aborting.");
            return;
        }

        // 1. Config & Database for Discord-side Moderation
        this.modConfig = new DiscordModConfig(plugin);
        this.modDatabase = new DiscordModDatabase(plugin); // NEW

        // 2. Linking Module
        this.linkingModule = new DiscordLinkingModule(plugin);
        // Pass modDatabase here
        boolean linkSuccess = this.linkingModule.init(lagClearLogger, modConfig, modDatabase, rankModule, moderationModule);

        if (!linkSuccess) {
            plugin.getLogger().severe("DiscordModule: Linking Module failed to initialize.");
            return;
        }

        // 3. Store/Ticket Module
        this.ticketModule = new TicketModule(plugin, linkingModule.getDiscordLinking(), rankModule.getManager());
        this.ticketModule.init();

        plugin.getLogger().info("DiscordModule initialized successfully.");
    }

    public void shutdown() {
        if (ticketModule != null) ticketModule.shutdown();
        if (linkingModule != null) linkingModule.shutdown();
        if (modDatabase != null) modDatabase.disconnect();
    }

    public DiscordLinkingModule getLinkingModule() {
        return linkingModule;
    }

    public TicketModule getTicketModule() {
        return ticketModule;
    }

    public DiscordModConfig getModConfig() {
        return modConfig;
    }
}