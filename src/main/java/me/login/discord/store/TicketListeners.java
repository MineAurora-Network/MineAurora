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
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
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
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

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
    private long storeHelperRoleId;

    private final Map<Long, Long> helpCooldowns = new ConcurrentHashMap<>();
    private final long HELP_COOLDOWN_MS = TimeUnit.HOURS.toMillis(6);

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
        this.storeHelperRoleId = plugin.getConfig().getLong("store-helper-role-id", 0);

        if (ticketChannelId == 0) {
            plugin.getLogger().warning("Store Bot: 'store-ticket-channel-id' is not set in config.yml. Ticket creation embed will not be sent.");
            return;
        }
        if (storeHelperRoleId == 0) {
            plugin.getLogger().warning("Store Bot: 'store-helper-role-id' is not set in config.yml. 'Call for Help' button will not ping anyone.");
        }

        TextChannel channel = event.getJDA().getTextChannelById(ticketChannelId);
        if (channel == null) {
            plugin.getLogger().warning("Store Bot: Cannot find 'store-ticket-channel-id': " + ticketChannelId);
            return;
        }

        channel.getHistory().retrievePast(50).queue(messages -> {
            List<Message> botMessages = messages.stream().filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser())).toList();

            if (!botMessages.isEmpty()) {
                if (botMessages.size() > 1) {
                    channel.deleteMessages(botMessages).queue(
                            v -> plugin.getLogger().info("Store Bot: Cleared " + botMessages.size() + " old ticket panels."),
                            error -> plugin.getLogger().warning("Store Bot: Failed to clear old panels. Might lack MANAGE_MESSAGES perm.")
                    );
                } else if (botMessages.size() == 1) {
                    botMessages.get(0).delete().queue(
                            v -> plugin.getLogger().info("Store Bot: Cleared 1 old ticket panel."),
                            error -> plugin.getLogger().warning("Store Bot: Failed to clear old panel.")
                    );
                }
            }

            plugin.getLogger().info("Store Bot: Sending new ticket panel.");
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

        Member member = event.getMember();
        if (member == null) return;

        String selection = event.getValues().get(0);
        User user = event.getUser();
        Guild guild = event.getGuild();

        if (guild == null) {
            event.reply("This must be used in a server.").setEphemeral(true).queue();
            return;
        }

        String categoryName = selection.substring(0, 1).toUpperCase() + selection.substring(1);
        Category category = guild.getCategoriesByName(categoryName, true).stream().findFirst().orElse(null);

        if (category != null) {
            for (GuildChannel channel : category.getChannels()) {
                if (channel.getName().endsWith("-" + user.getIdLong())) {
                    event.reply("You already have an open ticket in this category! " + channel.getAsMention())
                            .setEphemeral(true).queue();
                    return;
                }
            }
        }

        Role staffRole = guild.getRoleById(staffRoleId);
        if (staffRole == null) {
            event.reply("Error: Staff role not found. Please contact an admin.").setEphemeral(true).queue();
            plugin.getLogger().severe("Store Bot: 'store-staff-role-id' is invalid!");
            return;
        }

        if (category == null) {
            guild.createCategory(categoryName)
                    .addPermissionOverride(guild.getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                    .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL), null)
                    .queue(cat -> createTicketChannel(cat, staffRole, member, selection),
                            failure -> {
                                event.reply("Error: Could not create ticket category. Does the bot have 'Manage Channels' permission?")
                                        .setEphemeral(true).queue();
                                plugin.getLogger().severe("Store Bot: Failed to create category: " + failure.getMessage());
                            });
        } else {
            createTicketChannel(category, staffRole, member, selection);
        }

        event.reply("Your ticket has been created!").setEphemeral(true).queue();
    }

    private void createTicketChannel(Category category, Role staffRole, Member member, String type) {
        User user = member.getUser();
        String cleanName = user.getName().toLowerCase().replaceAll("[^a-z0-9-]", "");
        String truncatedName = cleanName.substring(0, Math.min(cleanName.length(), 20));
        String channelName = type + "-" + truncatedName + "-" + user.getIdLong();

        category.createTextChannel(channelName)
                .addPermissionOverride(category.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(staffRole, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_MANAGE, Permission.MESSAGE_HISTORY), null)
                .addPermissionOverride(Objects.requireNonNull(member),
                        EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_HISTORY), null)
                .queue(channel -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle(type.substring(0, 1).toUpperCase() + type.substring(1) + " Ticket")
                            .setDescription("Welcome, " + user.getAsMention() + "!\n\nPlease describe your issue, and a "
                                    + staffRole.getAsMention() + " will be with you shortly.");

                    Button closeButton = Button.danger("close-ticket", "Close Ticket");
                    Button helpButton = Button.primary("call-for-help", "Call for Help");

                    channel.sendMessage(user.getAsMention() + " " + staffRole.getAsMention())
                            .setEmbeds(eb.build())
                            .setActionRow(closeButton, helpButton)
                            .queue();
                }, failure -> plugin.getLogger().severe("Store Bot: Failed to create text channel: " + failure.getMessage()));
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("purchase")) {
            handlePurchase(event);
        } else if (event.getName().equals("rank")) {
            handleRank(event);
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        Member member = event.getMember();
        if (member == null) return;

        switch (componentId) {
            case "close-ticket":
                handleCloseTicket(event, member);
                break;
            case "call-for-help":
                handleCallForHelp(event, member);
                break;
            case "confirm-purchase":
            case "deny-purchase":
            case "hold-purchase":
                handlePurchaseButton(event, member);
                break;
        }
    }

    private void handleCloseTicket(ButtonInteractionEvent event, Member member) {
        if (member.getRoles().stream().noneMatch(r -> r.getIdLong() == staffRoleId)) {
            event.reply("You do not have permission to close this ticket.").setEphemeral(true).queue();
            return;
        }

        event.reply("Closing ticket in 5 seconds...").queue();
        event.getChannel().delete().queueAfter(5, TimeUnit.SECONDS,
                v -> helpCooldowns.remove(event.getChannelIdLong()),
                error -> plugin.getLogger().warning("Could not delete ticket channel: " + error.getMessage())
        );
    }

    private void handleCallForHelp(ButtonInteractionEvent event, Member member) {
        long channelId = event.getChannelIdLong();
        long currentTime = System.currentTimeMillis();
        long lastCallTime = helpCooldowns.getOrDefault(channelId, 0L);

        if (currentTime - lastCallTime < HELP_COOLDOWN_MS) {
            long remaining = (lastCallTime + HELP_COOLDOWN_MS) - currentTime;
            event.reply("This button is on cooldown. Please wait " + TimeUtil.formatDuration(remaining) + ".").setEphemeral(true).queue();
            return;
        }

        Role helperRole = event.getGuild().getRoleById(storeHelperRoleId);
        if (helperRole == null) {
            event.reply("Error: Store Helper role not found. Please contact staff directly.").setEphemeral(true).queue();
            return;
        }

        helpCooldowns.put(channelId, currentTime);
        event.reply("A " + helperRole.getAsMention() + " has been pinged for assistance!").queue();
    }

    private void handlePurchaseButton(ButtonInteractionEvent event, Member member) {
        if (member.getRoles().stream().noneMatch(r -> r.getIdLong() == staffRoleId)) {
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
            switch (event.getComponentId()) {
                case "confirm-purchase":
                    ticketDatabase.updatePurchaseStatus(messageId, 1);
                    EmbedBuilder confirmDm = new EmbedBuilder()
                            .setColor(Color.GREEN)
                            .setTitle("Payment Confirmed!")
                            .setDescription("We have received your payment for **" + data.purchaseItem + "**.\n\nYour item(s) will be delivered within 24 hours. Thank you!");
                    sendPrivateEmbed(user, confirmDm.build());
                    EmbedBuilder newConfirmEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase VERIFIED")
                            .setColor(Color.GREEN)
                            .setFooter("Confirmed by " + staffName);
                    event.getMessage().editMessageEmbeds(newConfirmEmbed.build()).setComponents().queue();
                    break;
                case "deny-purchase":
                    ticketDatabase.updatePurchaseStatus(messageId, 2);
                    EmbedBuilder denyDm = new EmbedBuilder()
                            .setColor(Color.RED)
                            .setTitle("Payment Not Received")
                            .setDescription("We were unable to confirm your payment for **" + data.purchaseItem + "**.\n\nIf you believe this is a mistake, please open an enquiry ticket to speak with staff.");
                    sendPrivateEmbed(user, denyDm.build());
                    EmbedBuilder newDenyEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase DENIED")
                            .setColor(Color.RED)
                            .setFooter("Denied by " + staffName);
                    event.getMessage().editMessageEmbeds(newDenyEmbed.build()).setComponents().queue();
                    break;
                case "hold-purchase":
                    ticketDatabase.updatePurchaseStatus(messageId, 3);
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

    private void handlePurchase(SlashCommandInteractionEvent event) {
        if (discordLinking == null) {
            event.reply("Error: Linking system is not available.").setEphemeral(true).queue();
            return;
        }

        event.deferReply(true).queue();

        User user = event.getUser();
        UUID linkedUuid = discordLinking.getLinkedUuid(user.getIdLong());
        if (linkedUuid == null) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle("Account Not Linked")
                    .setDescription("You must link your Minecraft account to your Discord account before submitting a purchase.\n" +
                            "Please use the `/discord link` command on the main server.")
                    .build()).setEphemeral(true).queue();
            return;
        }

        Message.Attachment screenshot;
        String txnId, purchaseItem, paidAmount;

        try {
            screenshot = Objects.requireNonNull(event.getOption("screenshot")).getAsAttachment();
            txnId = Objects.requireNonNull(event.getOption("txn-id")).getAsString();
            purchaseItem = Objects.requireNonNull(event.getOption("purchase-item")).getAsString();
            paidAmount = Objects.requireNonNull(event.getOption("paid-amount")).getAsString();
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get required options for /purchase command: " + e.getMessage());
            event.getHook().sendMessage("Error: All options are required. Please fill out the entire form.").setEphemeral(true).queue();
            return;
        }


        if (!screenshot.isImage()) {
            event.getHook().sendMessage("The `screenshot` must be an image file.").setEphemeral(true).queue();
            return;
        }

        TextChannel verificationChannel = event.getJDA().getTextChannelById(verificationChannelId);
        if (verificationChannel == null) {
            event.getHook().sendMessage("Error: Payment verification channel not found. Please contact staff.").setEphemeral(true).queue();
            plugin.getLogger().severe("Store Bot: 'payment-verification-channel-id' is invalid!");
            return;
        }

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

    private void handleRank(SlashCommandInteractionEvent event) {
        Member senderMember = event.getMember();
        if (senderMember == null) {
            event.reply("This command must be used in a server.").setEphemeral(true).queue();
            return;
        }

        if (senderMember.getRoles().stream().noneMatch(r -> r.getIdLong() == ownerRoleId) && !senderMember.isOwner()) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        if (luckPerms == null || rankManager == null) {
            event.reply("Error: The Rank System is not connected. Please contact an admin.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.getHook().sendMessage("Error: Subcommand not found.").setEphemeral(true).queue();
            return;
        }

        if (subcommand.equals("info")) {
            handleRankInfo(event);
        } else if (subcommand.equals("set")) {
            handleRankSet(event);
        }
    }

    private void handleRankSet(SlashCommandInteractionEvent event) {
        User discordTarget;
        String rankName, durationString;

        try {
            discordTarget = Objects.requireNonNull(event.getOption("user")).getAsUser();
            rankName = Objects.requireNonNull(event.getOption("rank")).getAsString();
            durationString = Objects.requireNonNull(event.getOption("duration")).getAsString();
        } catch (Exception e) {
            event.getHook().sendMessage("Error: All options are required.").setEphemeral(true).queue();
            return;
        }

        User discordSender = event.getUser();

        UUID targetUuid = discordLinking.getLinkedUuid(discordTarget.getIdLong());
        if (targetUuid == null) {
            event.getHook().sendMessage("Target user " + discordTarget.getAsMention() + " does not have a linked Minecraft account.").queue();
            return;
        }

        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(durationString);
        } catch (IllegalArgumentException e) {
            event.getHook().sendMessage("Invalid time format: `" + durationString + "`. Use `1h`, `7d`, `perm`, etc.").queue();
            return;
        }

        Group group = luckPerms.getGroupManager().getGroup(rankName);
        if (group == null) {
            event.getHook().sendMessage("The rank `" + rankName + "` does not exist.").queue();
            return;
        }

        luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(targetUser -> {
            if (targetUser == null) {
                event.getHook().sendMessage("Could not load Minecraft user data for " + discordTarget.getAsTag()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

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
        User discordTarget;
        try {
            discordTarget = Objects.requireNonNull(event.getOption("user")).getAsUser();
        } catch (Exception e) {
            event.getHook().sendMessage("Error: You must specify a user.").setEphemeral(true).queue();
            return;
        }

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
                    .setAuthor(targetName + "'s Rank Info", null, "https.crafatar.com/avatars/" + targetUuid + "?overlay")
                    .setDescription(plainInfo);
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void sendPrivateEmbed(User user, MessageEmbed embed) {
        if (user == null) return;
        user.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed).queue(null, (error) ->
                        plugin.getLogger().log(Level.WARNING, "Failed to send DM to " + user.getAsTag(), error)
                ),
                error -> plugin.getLogger().log(Level.WARNING, "Failed to open DM with " + user.getAsTag(), error)
        );
    }
}