package me.login.discord.store;

import me.login.Login;
import me.login.discordlinking.DiscordLinking;
import org.bukkit.Bukkit;

public class TicketModule {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private TicketDatabase ticketDatabase;
    private TicketSystem ticketSystem;

    public TicketModule(Login plugin, DiscordLinking discordLinking) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
    }

    public void init() {
        // Initialize the database
        this.ticketDatabase = new TicketDatabase(plugin);
        this.ticketDatabase.connect();

        // Start the bot asynchronously
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String storeBotToken = plugin.getConfig().getString("store-bot-token");
                String mainBotToken = plugin.getConfig().getString("bot-token");

                if (isConfigValueInvalid(storeBotToken, "YOUR_STORE_BOT_TOKEN_HERE")) {
                    plugin.getLogger().warning("Store Bot Token is not set. Store ticket system will be disabled.");
                    return;
                }

                if (storeBotToken.equals(mainBotToken)) {
                    plugin.getLogger().severe("Store Bot Token CANNOT be the same as the main bot token! Store system disabled.");
                    return;
                }

                // Pass main linking system and new store DB
                this.ticketSystem = new TicketSystem(plugin, this.discordLinking, this.ticketDatabase);
                this.ticketSystem.startBot(storeBotToken);
                // The TicketSystem now logs its own readiness

            } catch (Exception e) {
                plugin.getLogger().severe("Async Store Discord startup failed: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void shutdown() {
        if (ticketSystem != null) {
            ticketSystem.shutdown();
            plugin.getLogger().info("Store JDA shut down.");
        }
        if (ticketDatabase != null) {
            ticketDatabase.disconnect();
        }
    }

    private boolean isConfigValueInvalid(String value, String placeholder) {
        return value == null || value.isEmpty() || value.equals(placeholder);
    }

    // You can add getters here if other modules need to access parts of this one
    public TicketDatabase getTicketDatabase() {
        return ticketDatabase;
    }

    public TicketSystem getTicketSystem() {
        return ticketSystem;
    }
}