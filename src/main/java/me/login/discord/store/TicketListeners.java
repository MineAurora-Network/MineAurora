package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.misc.rank.RankManager;
import me.login.misc.rank.util.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.awt.Color;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class TicketListeners extends ListenerAdapter {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private final RankManager rankManager;
    private final LuckPerms luckPerms;

    // Config values
    private long ticketChannelId;
    private long staffRoleId;
    private long ownerRoleId;
    private long verificationChannelId;

    public TicketListeners(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase, RankManager rankManager) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
        this.rankManager = rankManager;

        // Load LuckPerms
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
        // Load config values
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

        // Clear old bot messages
        channel.getHistory().retrievePast(50).queue(messages -> {
            List<Message> botMessages = messages.stream().filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser())).toList();
            if (botMessages.size() > 0) {
                try {
                    channel.deleteMessages(botMessages).queue(null, (error) -> plugin.getLogger().warning("Store Bot: Failed to clear old messages. Might lack MANAGE_MESSAGES perm."));
                } catch (Exception e) {
                    plugin.getLogger().warning("Store Bot: Error clearing messages: " + e.getMessage());
                }
            }

            // Send the new ticket creation panel
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(new Color(0x5865F2)) // Discord Blurple
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

        String selection = event.getValues().get(0); // "purchase" or "enquiry"
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

        // Find or create the category
        String categoryName = selection.substring(0, 1).toUpperCase() + selection.substring(1); // "Purchase" or "Enquiry"
        Category category = guild.getCategoriesByName(categoryName, true).stream().findFirst().orElse(null);

        if (category == null) {
            // Create category with permissions for staff
            guild.createCategory(categoryName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .queue(cat -> {
                        createTicketChannel(cat, staffRole, user, selection);
                    }, failure -> {
                        event.reply("Error: Could not create ticket category. Does the bot have 'Manage Channels' permission?").setEphemeral(true).queue();
                        plugin.getLogger().severe("Store Bot: Failed to create category: " + failure.getMessage());
                    });
        } else {
            createTicketChannel(category, staffRole, user, selection);
        }

        event.reply("Your ticket has been created!").setEphemeral(true).queue();
    }

    private void createTicketChannel(Category category, Role staffRole, User user, String type) {
        String channelName = type + "-" + user.getName().toLowerCase().replaceAll("[^a-z0-9-]", "").substring(0, Math.min(user.getName().length(), 20));

        category.createTextChannel(channelName)
                .addPermissionOverride(category.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_MANAGE), null)
                .addPermissionOverride(Objects.requireNonNull(category.getGuild().getMember(user)), EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES), null)
                .queue(channel -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(type.substring(0, 1).toUpperCase() + type.substring(1) + " Ticket")
                            .setDescription("Welcome, " + user.getAsMention() + "!\n\nPlease describe your issue, and a " + staffRole.getAsMention() + " will be with you shortly.");

                    MessageCreateData message = MessageCreateData.fromContent(user.getAsMention() + " " + staffRole.getAsMention());
                    message.setEmbeds(eb.build());
                    channel.sendMessage(message).queue();
                }, failure -> {
                    plugin.getLogger().severe("Store Bot: Failed to create text channel: " + failure.getMessage());
                });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("purchase")) {
            handlePurchase(event);
        } else if (event.getName().equals("rank")) {
            handleRank(event);
        }
    }

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

        // Get options
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

        // Build Embed
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

        // Build Buttons
        Button confirm = Button.success("confirm-purchase", "Confirm");
        Button deny = Button.danger("deny-purchase", "Not Received");
        Button hold = Button.secondary("hold-purchase", "Hold");

        verificationChannel.sendMessageEmbeds(eb.build()).setActionRow(confirm, deny, hold).queue(message -> {
            // Save to database
            ticketDatabase.addPurchase(message.getIdLong(), user.getIdLong(), purchaseItem);
            event.getHook().sendMessage("Your purchase submission has been sent to the staff for review!").setEphemeral(true).queue();
        }, failure -> {
            event.getHook().sendMessage("Failed to send purchase verification. Please try again or contact staff.").setEphemeral(true).queue();
        });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("confirm-") && !componentId.startsWith("deny-") && !componentId.startsWith("hold-")) {
            return;
        }

        if (event.getMember() == null || event.getMember().getRoles().stream().noneMatch(r -> r.getIdLong() == staffRoleId)) {
            event.reply("You do not have permission to use this button.").setEphemeral(true).queue();
            return;
        }

        event.deferEdit().queue();
        long messageId = event.getMessageIdLong();
        String staffName = event.getUser().getName();

        TicketDatabase.PurchaseData data = ticketDatabase.getPurchase(messageId);
        if (data == null) {
            event.getHook().sendMessage("Error: Could not find this purchase in the database.").setEphemeral(true).queue();
            return;
        }

        if (data.status != 0) {
            event.getHook().sendMessage("This purchase has already been actioned.").setEphemeral(true).queue();
            return;
        }

        MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);
        if (originalEmbed == null) {
            event.getHook().sendMessage("Error: Could not read original embed.").setEphemeral(true).queue();
            return;
        }

        event.getJDA().retrieveUserById(data.userId).queue(user -> {
            switch (componentId) {
                case "confirm-purchase":
                    // Update database
                    ticketDatabase.updatePurchaseStatus(messageId, 1);

                    // Send DM
                    EmbedBuilder confirmDm = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("Payment Confirmed!")
                            .setDescription("We have received your payment for **" + data.purchaseItem + "**.\n\nYour item(s) will be delivered within 24 hours. Thank you!");
                    sendPrivateEmbed(user, confirmDm.build());

                    // Edit original message
                    EmbedBuilder newConfirmEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase VERIFIED")
                            .setColor(Color.GREEN)
                            .setFooter("Confirmed by " + staffName);
                    event.getMessage().editMessageEmbeds(newConfirmEmbed.build()).setComponents().queue();
                    break;

                case "deny-purchase":
                    // Update database
                    ticketDatabase.updatePurchaseStatus(messageId, 2);

                    // Send DM
                    EmbedBuilder denyDm = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Payment Not Received")
                            .setDescription("We were unable to confirm your payment for **" + data.purchaseItem + "**.\n\nIf you believe this is a mistake, please open an enquiry ticket to speak with staff.");
                    sendPrivateEmbed(user, denyDm.build());

                    // Edit original message
                    EmbedBuilder newDenyEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase DENIED")
                            .setColor(Color.RED)
                            .setFooter("Denied by " + staffName);
                    event.getMessage().editMessageEmbeds(newDenyEmbed.build()).setComponents().queue();
                    break;

                case "hold-purchase":
                    // Update database
                    ticketDatabase.updatePurchaseStatus(messageId, 3);

                    // Edit original message
                    EmbedBuilder newHoldEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase On Hold")
                            .setColor(Color.GRAY)
                            .setFooter("Put on hold by " + staffName);
                    event.getMessage().editMessageEmbeds(newHoldEmbed.build()).setComponents().queue();
                    break;
            }
        }, failure -> {
            event.getHook().sendMessage("Error: Could not find the user who made this purchase.").setEphemeral(true).queue();
        });
    }

    // --- New Rank Command Logic for Store Bot ---
    private void handleRank(SlashCommandInteractionEvent event) {
        Member senderMember = event.getMember();
        if (senderMember == null) {
            event.reply("This command must be used in a server.").setEphemeral(true).queue();
            return;
        }

        // Check for Store Owner Role
        if (senderMember.getRoles().stream().noneMatch(r -> r.getIdLong() == ownerRoleId) && !senderMember.isOwner()) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        if (luckPerms == null || rankManager == null) {
            event.reply("Error: The Rank System is not connected. Please contact an admin.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) return;

        if (subcommand.equals("info")) {
            handleRankInfo(event);
        } else if (subcommand.equals("set")) {
            handleRankSet(event);
        }
    }

    private void handleRankSet(SlashCommandInteractionEvent event) {
        User discordTarget = Objects.requireNonNull(event.getOption("user")).getAsUser();
        String rankName = Objects.requireNonNull(event.getOption("rank")).getAsString();
        String durationString = Objects.requireNonNull(event.getOption("duration")).getAsString();
        User discordSender = event.getUser();

        event.deferReply().queue();

        // 1. Get target's linked UUID (from main linking system)
        UUID targetUuid = discordLinking.getLinkedUuid(discordTarget.getIdLong());
        if (targetUuid == null) {
            event.getHook().sendMessage("Target user " + discordTarget.getAsMention() + " does not have a linked Minecraft account.").queue();
            return;
        }

        // 2. Parse duration
        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(durationString);
        } catch (IllegalArgumentException e) {
            event.getHook().sendMessage("Invalid time format: `" + durationString + "`. Use `1h`, `7d`, `perm`, etc.").queue();
            return;
        }

        // 3. Get LP Group
        Group group = luckPerms.getGroupManager().getGroup(rankName);
        if (group == null) {
            event.getHook().sendMessage("The rank `" + rankName + "` does not exist.").queue();
            return;
        }

        // 4. Load LP data and set rank
        luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(targetUser -> {
            if (targetUser == null) {
                event.getHook().sendMessage("Could not load Minecraft user data for " + discordTarget.getAsTag()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

            // Use the Discord Sender's name and a console UUID for the manager
            UUID consoleUuid = UUID.fromString("00000000-0000-0000-0000-000000000000");
            rankManager.setRank(discordSender.getName(), consoleUuid, targetUser, group, durationMillis);

            String timeString = TimeUtil.formatDuration(durationMillis);
            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Rank Updated")
                    .setDescription(String.format("Successfully set **%s's** rank to **%s** for **%s**.",
                            targetName, rankName, timeString))
                    .addField("Moderator", discordSender.getAsMention(), false);
            event.getHook().sendMessageEmbeds(eb.build()).queue();

        });
    }

    private void handleRankInfo(SlashCommandInteractionEvent event) {
        User discordTarget = Objects.requireNonNull(event.getOption("user")).getAsUser();
        event.deferReply().queue();

        UUID targetUuid = discordLinking.getLinkedUuid(discordTarget.getIdLong());
        if (targetUuid == null) {
            event.getHook().sendMessage("User " + discordTarget.getAsMention() + " does not have a linked Minecraft account.").queue();
            return;
        }

        luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(targetUser -> {
            if (targetUser == null) {
                event.getHook().sendMessage("Could not load Minecraft user data.").queue();
                return;
            }
            Component infoComponent = rankManager.getRankInfo(targetUser);
            String plainInfo = PlainTextComponentSerializer.plainText().serialize(infoComponent).replace("%nl%", "\n");
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setAuthor(targetName + "'s Rank Info", null, "https://crafatar.com/avatars/" + targetUuid + "?overlay")
                    .setDescription(plainInfo);
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void sendPrivateEmbed(User user, MessageEmbed embed) {
        user.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed).queue(null, (error) ->
                        plugin.getLogger().log(Level.WARNING, "Failed to send DM to " + user.getAsTag(), error)
                ),
                error -> plugin.getLogger().log(Level.WARNING, "Failed to open DM with " + user.getAsTag(), error)
        );
    }
}