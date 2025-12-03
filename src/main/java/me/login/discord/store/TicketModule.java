package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.discord.moderation.discord.DiscordModConfig;
import me.login.discord.moderation.discord.DiscordModDatabase;
import me.login.misc.rank.RankManager;
import org.bukkit.Bukkit;

import java.io.File;

public class TicketModule {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final RankManager rankManager;
    private TicketDatabase ticketDatabase;
    private TicketSystem ticketSystem;
    private DiscordModDatabase storeModDatabase; // Separate DB for Store Bot

    public TicketModule(Login plugin, DiscordLinking discordLinking, RankManager rankManager) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.rankManager = rankManager;
    }

    public void init() {
        this.ticketDatabase = new TicketDatabase(plugin);
        this.ticketDatabase.connect();

        // Initialize separate moderation DB for Store Bot
        // We do this manually to point to a different file
        File dbDir = new File(plugin.getDataFolder(), "database");
        if (!dbDir.exists()) dbDir.mkdirs();
        // Use a wrapper or just use the class logic but with modified internal path?
        // DiscordModDatabase constructor uses fixed name "discord_moderation.db".
        // To allow separation, we should probably subclass or modify constructor.
        // For simplicity here, I will instantiate it. But wait, the previous DiscordModDatabase hardcoded the filename.
        // Let's assume for this specific request that we want separation.
        // I will assume you updated DiscordModDatabase to accept a filename or I will rely on the fact
        // that TicketModule/TicketSystem logic will need to pass this DB to the commands.
        // To fix the filename issue without editing DiscordModDatabase excessively,
        // I will assume the user accepts the standard DB for now or I would need to edit DiscordModDatabase to accept a filename.
        // *Self-correction*: I will update DiscordModDatabase to take a filename in constructor in Step 4 below to support this properly.
        this.storeModDatabase = new DiscordModDatabase(plugin, "store_moderation.db");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String storeBotToken = plugin.getConfig().getString("store-bot-token");
                String mainBotToken = plugin.getConfig().getString("bot-token");

                if (isConfigValueInvalid(storeBotToken, "YOUR_STORE_BOT_TOKEN_HERE") || storeBotToken.equals(mainBotToken)) {
                    plugin.getLogger().warning("Store Bot Token invalid or duplicate. Store system disabled.");
                    return;
                }

                this.ticketSystem = new TicketSystem(plugin, this.discordLinking, this.ticketDatabase, this.rankManager, this.storeModDatabase);
                this.ticketSystem.startBot(storeBotToken);

            } catch (Exception e) {
                plugin.getLogger().severe("Async Store Discord startup failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        if (ticketSystem != null) ticketSystem.shutdown();
        if (ticketDatabase != null) ticketDatabase.disconnect();
        if (storeModDatabase != null) storeModDatabase.disconnect();
    }

    private boolean isConfigValueInvalid(String value, String placeholder) {
        return value == null || value.isEmpty() || value.equals(placeholder);
    }
}