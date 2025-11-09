package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.misc.rank.RankManager;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.luckperms.api.LuckPerms;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class TicketListeners extends ListenerAdapter {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private final RankManager rankManager;
    private final LuckPerms luckPerms;

    private long ticketChannelId;
    private long staffRoleId;
    private long ownerRoleId;
    private long verificationChannelId;

    public TicketListeners(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase, RankManager rankManager) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
        this.rankManager = rankManager;

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        } else {
            this.luckPerms = null;
            plugin.getLogger().severe("Store Bot: LuckPerms API not found! /rank command will fail.");
        }
    }

    @Override
    public void onReady(ReadyEvent event) {
        this.ticketChannelId = plugin.getConfig().getLong("store-ticket-channel-id", 0);
        this.staffRoleId = plugin.getConfig().getLong("store-staff-role-id", 0);
        this.ownerRoleId = plugin.getConfig().getLong("store-owner-role-id", 0);
        this.verificationChannelId = plugin.getConfig().getLong("payment-verification-channel-id", 0);

        if (ticketChannelId == 0) {
            plugin.getLogger().warning("Store Bot: 'store-ticket-channel-id' is not set in config.yml. Ticket creation embed will not be sent.");
            return;
        }

        TextChannel channel = event.getJDA().getTextChannelById(ticketChannelId);
        if (channel == null) {
            plugin.getLogger().warning("Store Bot: Cannot find 'store-ticket-channel-id': " + ticketChannelId);
            return;
        }

        channel.getHistory().retrievePast(50).queue(messages -> {
            List<Message> botMessages = messages.stream().filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser())).toList();
            if (!botMessages.isEmpty()) {
                try {
                    channel.deleteMessages(botMessages).queue(null, (error) ->
                            plugin.getLogger().warning("Store Bot: Failed to clear old messages. Might lack MANAGE_MESSAGES perm."));
                } catch (Exception e) {
                    plugin.getLogger().warning("Store Bot: Error clearing messages: " + e.getMessage());
                }
            }

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(new Color(0x5865F2))
                    .setTitle("Create a Support Ticket")
                    .setDescription("Please select a category below to open a ticket.\n\n" +
                            "**Purchase:** For issues or confirmation of a purchase.\n" +
                            "**Enquiry:** For any other questions about the store.");

            StringSelectMenu menu = StringSelectMenu.create("create-ticket")
                    .setPlaceholder("Select a ticket type...")
                    .addOption("Purchase", "purchase", "Open a ticket for a purchase.", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🛒"))
                    .addOption("Enquiry", "enquiry", "Ask a question about the store.", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❓"))
                    .build();

            channel.sendMessageEmbeds(eb.build()).setActionRow(menu).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("create-ticket")) return;
        if (event.getMember() == null) return;

        String selection = event.getValues().get(0);
        User user = event.getUser();
        Guild guild = event.getGuild();

        if (guild == null) {
            event.reply("This must be used in a server.").setEphemeral(true).queue();
            return;
        }

        Role staffRole = guild.getRoleById(staffRoleId);
        if (staffRole == null) {
            event.reply("Error: Staff role not found. Please contact an admin.").setEphemeral(true).queue();
            plugin.getLogger().severe("Store Bot: 'store-staff-role-id' is invalid!");
            return;
        }

        String categoryName = selection.substring(0, 1).toUpperCase() + selection.substring(1);
        Category category = guild.getCategoriesByName(categoryName, true).stream().findFirst().orElse(null);

        if (category == null) {
            guild.createCategory(categoryName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .queue(cat -> createTicketChannel(cat, staffRole, user, selection),
                            failure -> {
                                event.reply("Error: Could not create ticket category. Does the bot have 'Manage Channels' permission?")
                                        .setEphemeral(true).queue();
                                plugin.getLogger().severe("Store Bot: Failed to create category: " + failure.getMessage());
                            });
        } else {
            createTicketChannel(category, staffRole, user, selection);
        }

        event.reply("Your ticket has been created!").setEphemeral(true).queue();
    }

    // ✅ Fixed and simplified JDA 5 compatible method
    private void createTicketChannel(Category category, Role staffRole, User user, String type) {
        String cleanName = user.getName().toLowerCase().replaceAll("[^a-z0-9-]", "");
        String truncatedName = cleanName.substring(0, Math.min(cleanName.length(), 20));
        String channelName = type + "-" + truncatedName;

        category.createTextChannel(channelName)
                .addPermissionOverride(category.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_MANAGE), null)
                .addPermissionOverride(Objects.requireNonNull(category.getGuild().getMember(user)),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES), null)
                .queue(channel -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(type.substring(0, 1).toUpperCase() + type.substring(1) + " Ticket")
                            .setDescription("Welcome, " + user.getAsMention() + "!\n\nPlease describe your issue, and a "
                                    + staffRole.getAsMention() + " will be with you shortly.");

                    channel.sendMessage(user.getAsMention() + " " + staffRole.getAsMention())
                            .setEmbeds(eb.build())
                            .queue();
                }, failure -> plugin.getLogger().severe("Store Bot: Failed to create text channel: " + failure.getMessage()));
    }

    // 🔽 Rest of your original file continues unchanged 🔽
    // (handlePurchase, onButtonInteraction, handleRank, etc.)

    private void handlePurchase(SlashCommandInteractionEvent event) {
        if (discordLinking == null) {
            event.reply("Error: Linking system is not available.").setEphemeral(true).queue();
            return;
        }

        User user = event.getUser();
        UUID linkedUuid = discordLinking.getLinkedUuid(user.getIdLong());
        if (linkedUuid == null) {
            event.replyEmbeds(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Account Not Linked")
                    .setDescription("You must link your Minecraft account to your Discord account before submitting a purchase.\n" +
                            "Please use the `/discord link` command on the main server.")
                    .build()).setEphemeral(true).queue();
            return;
        }

        Message.Attachment screenshot = Objects.requireNonNull(event.getOption("screenshot")).getAsAttachment();
        String txnId = Objects.requireNonNull(event.getOption("txn-id")).getAsString();
        String purchaseItem = Objects.requireNonNull(event.getOption("purchase-item")).getAsString();
        String paidAmount = Objects.requireNonNull(event.getOption("paid-amount")).getAsString();

        if (!screenshot.isImage()) {
            event.reply("The `screenshot` must be an image file.").setEphemeral(true).queue();
            return;
        }

        TextChannel verificationChannel = event.getJDA().getTextChannelById(verificationChannelId);
        if (verificationChannel == null) {
            event.reply("Error: Payment verification channel not found. Please contact staff.").setEphemeral(true).queue();
            plugin.getLogger().severe("Store Bot: 'payment-verification-channel-id' is invalid!");
            return;
        }

        event.deferReply(true).queue();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.YELLOW)
                .setTitle("New Purchase Verification")
                .setAuthor(user.getAsTag(), null, user.getEffectiveAvatarUrl())
                .addField("User", user.getAsMention() + " (`" + user.getId() + "`)", false)
                .addField("Transaction ID", "`" + txnId + "`", true)
                .addField("Amount Paid", "`" + paidAmount + "`", true)
                .addField("Item(s) Purchased", "```" + purchaseItem + "```", false)
                .setImage(screenshot.getProxyUrl())
                .setTimestamp(Instant.now())
                .setFooter("Status: PENDING");

        Button confirm = Button.success("confirm-purchase", "Confirm");
        Button deny = Button.danger("deny-purchase", "Not Received");
        Button hold = Button.secondary("hold-purchase", "Hold");

        verificationChannel.sendMessageEmbeds(eb.build()).setActionRow(confirm, deny, hold).queue(message -> {
            ticketDatabase.addPurchase(message.getIdLong(), user.getIdLong(), purchaseItem);
            event.getHook().sendMessage("Your purchase submission has been sent to the staff for review!").setEphemeral(true).queue();
        }, failure -> {
            event.getHook().sendMessage("Failed to send purchase verification. Please try again or contact staff.").setEphemeral(true).queue();
        });
    }

    // (Rest of your button interactions, rank handling, etc. stay same)
}
