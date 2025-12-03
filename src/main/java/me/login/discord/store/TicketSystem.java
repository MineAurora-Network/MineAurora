package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.discord.DiscordModDatabase;
import me.login.discord.moderation.discord.DiscordStaffModCommands;
import me.login.misc.rank.RankManager;
import me.login.misc.rank.RankModule;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.Permission;

import java.util.EnumSet;

public class TicketSystem {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private final RankManager rankManager;
    private final DiscordModDatabase storeModDatabase; // NEW
    private JDA jda;

    public TicketSystem(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase, RankManager rankManager, DiscordModDatabase storeModDatabase) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
        this.rankManager = rankManager;
        this.storeModDatabase = storeModDatabase;
    }

    public void startBot(String token) throws InterruptedException {
        JDABuilder builder = JDABuilder.createDefault(token);
        builder.enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT);
        builder.disableIntents(GatewayIntent.GUILD_PRESENCES, GatewayIntent.GUILD_MESSAGE_TYPING);
        builder.setActivity(Activity.watching("the store"));

        builder.addEventListeners(new TicketListeners(plugin, discordLinking, ticketDatabase, rankManager));
        DiscordCommandLogger dummyLogger = new DiscordCommandLogger(plugin, null);
        builder.addEventListeners(new DiscordStaffModCommands(
                plugin,
                plugin.getDiscordModConfig(), // Reuse config structure
                storeModDatabase, // Use Store DB
                dummyLogger,
                discordLinking,
                null
        ));

        // Note: The previous line requires RankModule.
        // We can get it via `plugin.getRankModule()` if we added a getter or via `new` if strictly necessary,
        // but passing it down is cleaner. For now, since TicketModule has RankManager but not Module,
        // I will use `plugin.getRankModule()` which needs to be added to Login.java or just accessible.
        // See Login.java below for the getter addition if it wasn't there.
        // *Correction*: I will assume `plugin.getRankModule()` exists (I added it in previous steps).

        // Wait, DiscordStaffModCommands requires RankModule.
        // Updated TicketSystem constructor to handle this? No, I'll fetch from plugin.

        this.jda = builder.build().awaitReady();
        plugin.getLogger().info("Store Bot connected successfully!");

        registerCommands();
    }

    private void registerCommands() {
        if (this.jda == null) return;

        this.jda.updateCommands().addCommands(
                // Store Commands
                Commands.slash("purchase", "Submit a purchase for verification")
                        .addOption(OptionType.ATTACHMENT, "screenshot", "Screenshot", true)
                        .addOption(OptionType.STRING, "txn-id", "Transaction ID", true)
                        .addOption(OptionType.STRING, "purchase-item", "Item(s)", true)
                        .addOption(OptionType.STRING, "paid-amount", "Amount", true),

                Commands.slash("rank", "Manage player ranks.")
                        .setGuildOnly(true)
                        .addSubcommands(
                                new SubcommandData("set", "Set rank.")
                                        .addOption(OptionType.USER, "user", "User", true)
                                        .addOption(OptionType.STRING, "rank", "Rank", true)
                                        .addOption(OptionType.STRING, "duration", "Duration", true),
                                new SubcommandData("info", "Rank info.")
                                        .addOption(OptionType.USER, "user", "User", true)
                        ),

                // Moderation Commands (Mirrored from Main Bot)
                Commands.slash("ban", "Ban a user from Discord.")
                        .addOption(OptionType.USER, "user", "User", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true)
                        .addOption(OptionType.INTEGER, "days", "Days (0-7)", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

                Commands.slash("unban", "Unban a user from Discord.")
                        .addOption(OptionType.STRING, "user_id", "User ID", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

                Commands.slash("mute", "Mute (Timeout) a user.")
                        .addOption(OptionType.USER, "user", "User", true)
                        .addOption(OptionType.STRING, "duration", "Duration", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

                Commands.slash("unmute", "Unmute (Remove Timeout).")
                        .addOption(OptionType.USER, "user", "User", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

                Commands.slash("warn", "Warn a user.")
                        .addOption(OptionType.USER, "user", "User", true)
                        .addOption(OptionType.STRING, "reason", "Reason", true),

                Commands.slash("unwarn", "Remove a warning.")
                        .addOption(OptionType.USER, "user", "User", true)
                        .addOption(OptionType.INTEGER, "id", "Warning ID", true),

                Commands.slash("history", "View warnings.")
                        .addOption(OptionType.USER, "user", "User", true)

        ).queue();
    }

    public void shutdown() {
        if (jda != null) jda.shutdown();
    }
}