package me.login.discord;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import me.login.discord.linking.DiscordLinkingModule;
import me.login.discord.moderation.DiscordModConfig;
import me.login.discord.store.TicketModule;
import me.login.misc.rank.RankModule;
import me.login.moderation.ModerationModule;

public class DiscordModule {

    private final Login plugin;
    private DiscordLinkingModule linkingModule;
    private TicketModule ticketModule;
    private DiscordModConfig modConfig;

    public DiscordModule(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Initializes all Discord sub-modules using the shared JDA.
     */
    public void init(LagClearLogger lagClearLogger, RankModule rankModule, ModerationModule moderationModule) {
        plugin.getLogger().info("Initializing DiscordModule...");

        if (lagClearLogger == null || lagClearLogger.getJDA() == null) {
            plugin.getLogger().severe("DiscordModule: Shared JDA (LagClearLogger) is null. Aborting.");
            return;
        }

        // 1. Config for Moderation
        this.modConfig = new DiscordModConfig(plugin);

        // 2. Linking Module (Handles Link DB, Main Bot Commands, Rank Sync)
        this.linkingModule = new DiscordLinkingModule(plugin);
        // We pass ModerationModule here so Discord commands can punish players
        boolean linkSuccess = this.linkingModule.init(lagClearLogger, modConfig, rankModule.getManager(), moderationModule);

        if (!linkSuccess) {
            plugin.getLogger().severe("DiscordModule: Linking Module failed to initialize.");
            return;
        }

        // 3. Store/Ticket Module
        this.ticketModule = new TicketModule(plugin, linkingModule.getDiscordLinking(), rankModule.getManager());
        this.ticketModule.init(); // Uses its own bot token as per previous design

        plugin.getLogger().info("DiscordModule initialized successfully.");
    }

    public void shutdown() {
        if (ticketModule != null) ticketModule.shutdown();
        if (linkingModule != null) linkingModule.shutdown();
    }

    public DiscordLinkingModule getLinkingModule() {
        return linkingModule;
    }

    public TicketModule getTicketModule() {
        return ticketModule;
    }

    // --- ADDED THIS GETTER ---
    public DiscordModConfig getModConfig() {
        return modConfig;
    }
}