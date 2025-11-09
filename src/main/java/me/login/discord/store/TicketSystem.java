package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.misc.rank.RankManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;

public class TicketSystem {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private final RankManager rankManager; // <-- NEW
    private JDA jda;

    // --- UPDATED CONSTRUCTOR ---
    public TicketSystem(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase, RankManager rankManager) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
        this.rankManager = rankManager; // <-- NEW
    }

    public void startBot(String token) throws InterruptedException {
        JDABuilder builder = JDABuilder.createDefault(token);

        builder.enableIntents(
                GatewayIntent.GUILD_MEMBERS,
                GatewayIntent.GUILD_MESSAGES,
                GatewayIntent.MESSAGE_CONTENT
        );
        builder.disableIntents(
                GatewayIntent.GUILD_PRESENCES,
                GatewayIntent.GUILD_MESSAGE_TYPING
        );
        builder.setActivity(Activity.watching("the store"));

        // Add the listener and pass RankManager
        builder.addEventListeners(new TicketListeners(plugin, discordLinking, ticketDatabase, rankManager)); // <-- UPDATED

        this.jda = builder.build().awaitReady();
        plugin.getLogger().info("Store Bot connected successfully!");

        // Register slash commands
        registerCommands();
    }

    private void registerCommands() {
        if (this.jda == null) return;
        this.jda.updateCommands().addCommands(
                // --- NEW /PURCHASE COMMAND ---
                Commands.slash("purchase", "Submit a purchase for verification")
                        .addOption(OptionType.ATTACHMENT, "screenshot", "Screenshot of payment", true)
                        .addOption(OptionType.STRING, "txn-id", "Transaction ID", true)
                        .addOption(OptionType.STRING, "purchase-item", "The item(s) you purchased", true)
                        .addOption(OptionType.STRING, "paid-amount", "The total amount paid", true),

                // --- NEW /RANK COMMAND (for store bot) ---
                Commands.slash("rank", "Manage player ranks.")
                        .setGuildOnly(true) // Recommended for admin commands
                        .addSubcommands(
                                new SubcommandData("set", "Set a player's rank.")
                                        .addOption(OptionType.USER, "user", "The Discord user to set rank for.", true)
                                        .addOption(OptionType.STRING, "rank", "The rank name.", true)
                                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 7d, perm).", true),
                                new SubcommandData("info", "Get info on a player's rank.")
                                        .addOption(OptionType.USER, "user", "The Discord user to check.", true)
                        )
        ).queue(
                success -> plugin.getLogger().info("Successfully registered " + success.size() + " store slash commands."),
                error -> plugin.getLogger().severe("Failed to register store slash commands: " + error.getMessage())
        );
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
        }
    }

    public JDA getJDA() {
        return jda;
    }
}