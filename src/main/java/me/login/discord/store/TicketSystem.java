package me.login.discord.store;

import me.login.Login;
import me.login.discordlinking.DiscordLinking;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.util.EnumSet;

public class TicketSystem {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private JDA jda;

    public TicketSystem(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
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

        // Add the listener
        builder.addEventListeners(new TicketListeners(plugin, discordLinking, ticketDatabase));

        this.jda = builder.build().awaitReady();

        // Register slash commands
        registerCommands();
    }

    private void registerCommands() {
        if (this.jda == null) return;
        this.jda.updateCommands().addCommands(
                Commands.slash("purchase", "Submit a purchase for verification")
                        .addOption(OptionType.ATTACHMENT, "screenshot", "Screenshot of payment", true)
                        .addOption(OptionType.STRING, "txn-id", "Transaction ID", true)
                        .addOption(OptionType.STRING, "purchase-item", "The item(s) you purchased", true)
                        .addOption(OptionType.STRING, "paid-amount", "The total amount paid", true)
        ).queue(
                success -> plugin.getLogger().info("Successfully registered store slash commands."),
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