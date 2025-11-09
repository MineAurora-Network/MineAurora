package me.login.discord.store;

import me.login.Login;
import me.login.discordlinking.DiscordLinking;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.awt.Color;
import java.util.EnumSet;
import java.util.List;

public class TicketListeners extends ListenerAdapter {

    private final Login plugin;
    private final DiscordLinking discordLinking; // To check for linked accounts
    private final TicketDatabase ticketDatabase;

    public TicketListeners(Login plugin, DiscordLinking discordLinking, TicketDatabase ticketDatabase) {
        this.plugin = plugin;
        this.discordLinking = discordLinking;
        this.ticketDatabase = ticketDatabase;
    }

    @Override
    public void onReady(ReadyEvent event) {
        plugin.getLogger().info("Store Bot (" + event.getJDA().getSelfUser().getAsTag() + ") is ready.");
        // Setup the ticket creation panel
        try {
            long channelId = plugin.getConfig().getLong("store-ticket-channel");
            TextChannel channel = event.getJDA().getTextChannelById(channelId);
            if (channel == null) {
                plugin.getLogger().warning("Store ticket channel (" + channelId + ") not found!");
                return;
            }

            // Clear old messages (optional, good for cleanup)
            channel.getHistory().retrievePast(10).queue(messages -> {
                messages.forEach(msg -> {
                    if (msg.getAuthor().equals(event.getJDA().getSelfUser())) {
                        msg.delete().queue();
                    }
                });

                // Send the new panel
                EmbedBuilder eb = new EmbedBuilder()
                        .setTitle("MineAurora Store Support")
                        .setDescription("Welcome to the support center.\n\nPlease select an option below to create a ticket.")
                        .setColor(Color.CYAN);

                StringSelectMenu menu = StringSelectMenu.create("create-ticket-menu")
                        .setPlaceholder("Choose a ticket type")
                        .addOption("Enquiry", "enquiry", "For general questions about the store.")
                        .addOption("Purchase", "purchase", "For issues or questions about a purchase.")
                        .build();

                channel.sendMessageEmbeds(eb.build()).setComponents(ActionRow.of(menu)).queue();
                plugin.getLogger().info("Ticket creation panel sent to channel " + channel.getName());
            });

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to send ticket creation panel: " + e.getMessage());
        }
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("create-ticket-menu")) return;

        String type = event.getValues().get(0); // "enquiry" or "purchase"
        Member member = event.getMember();
        if (member == null) return;

        long staffRoleId = plugin.getConfig().getLong("store-staff-id");
        Role staffRole = event.getGuild().getRoleById(staffRoleId);
        if (staffRole == null) {
            event.reply("Error: Staff role not configured. Please contact an admin.").setEphemeral(true).queue();
            return;
        }

        String categoryName = type.substring(0, 1).toUpperCase() + type.substring(1); // "Enquiry" or "Purchase"
        String channelName = type + "-" + member.getUser().getName();

        // Find or create category
        Category category = event.getGuild().getCategoriesByName(categoryName, true).stream().findFirst().orElse(null);
        if (category == null) {
            category = event.getGuild().createCategory(categoryName).complete();
        }

        // Create the private channel
        final Category finalCategory = category;
        event.getGuild().createTextChannel(channelName, finalCategory)
                .addMemberPermissionOverride(member.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL), null)
                .addRolePermissionOverride(staffRole.getIdLong(), EnumSet.of(Permission.VIEW_CHANNEL), null)
                .addRolePermissionOverride(event.getGuild().getPublicRole().getIdLong(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .queue(channel -> {
                    // Send welcome message in the new channel
                    EmbedBuilder eb = new EmbedBuilder()
                            .setTitle("Welcome, " + member.getEffectiveName())
                            .setDescription("You have created a **" + categoryName + "** ticket.\n\nPlease describe your issue, and a staff member will be with you shortly.")
                            .setColor(Color.GREEN);
                    channel.sendMessage(member.getAsMention() + " " + staffRole.getAsMention()).setEmbeds(eb.build()).queue();

                    // Reply to the interaction
                    event.reply("Your ticket has been created: " + channel.getAsMention()).setEphemeral(true).queue();
                });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("purchase")) return;

        long userId = event.getUser().getIdLong();
        // Check if user is linked
        if (discordLinking.getLinkedUuid(userId) == null) {
            event.reply("You must link your Minecraft account to use this command. Use `/discord link` in-game to link.").setEphemeral(true).queue();
            return;
        }

        // Defer reply as this might take a moment
        event.deferReply(true).queue();

        try {
            long verificationChannelId = plugin.getConfig().getLong("payment-verification-channel-id");
            TextChannel verificationChannel = event.getJDA().getTextChannelById(verificationChannelId);
            if (verificationChannel == null) {
                event.getHook().sendMessage("Error: Payment verification channel is not configured correctly.").queue();
                return;
            }

            // Get options
            String txnId = event.getOption("txn-id", OptionMapping::getAsString);
            String purchaseItem = event.getOption("purchase-item", OptionMapping::getAsString);
            String paidAmount = event.getOption("paid-amount", OptionMapping::getAsString);
            String screenshotUrl = event.getOption("screenshot", OptionMapping::getAsAttachment).getUrl();

            EmbedBuilder eb = new EmbedBuilder()
                    .setAuthor(event.getUser().getAsTag() + " (" + userId + ")", null, event.getUser().getAvatarUrl())
                    .setTitle("New Purchase Submission")
                    .setColor(Color.ORANGE)
                    .addField("Item(s)", purchaseItem, false)
                    .addField("Amount Paid", paidAmount, true)
                    .addField("Transaction ID", txnId, true)
                    .setImage(screenshotUrl)
                    .setTimestamp(event.getTimeCreated());

            ActionRow buttons = ActionRow.of(
                    Button.success("confirm-purchase", "Confirm"),
                    Button.danger("deny-purchase", "Not Received"),
                    Button.secondary("hold-purchase", "Hold")
            );

            // Send to verification channel
            final String finalPurchaseItem = purchaseItem;
            verificationChannel.sendMessageEmbeds(eb.build()).setComponents(buttons).queue(message -> {
                // Log this purchase to the database
                ticketDatabase.logPurchase(message.getIdLong(), userId, finalPurchaseItem);
                event.getHook().sendMessage("Your purchase has been submitted for verification.").queue();
            });

        } catch (Exception e) {
            event.getHook().sendMessage("An error occurred while submitting your purchase.").queue();
            e.printStackTrace();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("confirm-") && !componentId.startsWith("deny-") && !componentId.startsWith("hold-")) {
            return;
        }

        event.deferReply(true).queue(); // Acknowledge the click
        long messageId = event.getMessageIdLong();

        // Get purchase data from DB
        TicketDatabase.PurchaseData purchaseData = ticketDatabase.getPurchase(messageId);
        if (purchaseData == null) {
            event.getHook().sendMessage("Error: Could not find a purchase record for this message. It might be too old.").queue();
            return;
        }

        // Check if already handled
        if (purchaseData.status != 0) {
            event.getHook().sendMessage("This purchase has already been handled.").queue();
            return;
        }

        // Get the user who made the purchase
        event.getJDA().retrieveUserById(purchaseData.userId).queue(user -> {
            String staffName = event.getMember().getEffectiveName();
            MessageEmbed originalEmbed = event.getMessage().getEmbeds().get(0);

            switch (componentId) {
                case "confirm-purchase":
                    // Send DM to user
                    EmbedBuilder confirmEb = new EmbedBuilder()
                            .setTitle("Payment Confirmed!")
                            .setColor(Color.GREEN)
                            .setDescription("We have successfully received your payment. Your **" + purchaseData.purchaseItem + "** will be delivered within 24 hours.")
                            .setFooter("Thank you for your purchase!");
                    sendPrivateEmbed(user, confirmEb.build());

                    // Update database
                    ticketDatabase.updatePurchaseStatus(messageId, 1);

                    // Edit original message
                    EmbedBuilder newConfirmEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase Confirmed")
                            .setColor(Color.GREEN)
                            .setFooter("Confirmed by " + staffName);
                    event.getMessage().editMessageEmbeds(newConfirmEmbed.build()).setComponents().queue(); // Remove buttons
                    event.getHook().sendMessage("Purchase confirmed. User has been notified.").queue();
                    break;

                case "deny-purchase":
                    // Send DM to user
                    EmbedBuilder denyEb = new EmbedBuilder()
                            .setTitle("Payment Not Received")
                            .setColor(Color.RED)
                            .setDescription("We were unable to confirm your payment for **" + purchaseData.purchaseItem + "**.\n\nIf you believe this is an error, please open a new ticket to speak with staff.")
                            .setFooter("Please do not reply to this message.");
                    sendPrivateEmbed(user, denyEb.build());

                    // Update database
                    ticketDatabase.updatePurchaseStatus(messageId, 2);

                    // Edit original message
                    EmbedBuilder newDenyEmbed = new EmbedBuilder(originalEmbed)
                            .setTitle("Purchase Denied")
                            .setColor(Color.RED)
                            .setFooter("Denied by " + staffName);
                    event.getMessage().editMessageEmbeds(newDenyEmbed.build()).setComponents().queue();
                    event.getHook().sendMessage("Purchase denied. User has been notified.").queue();
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
                    event.getHook().sendMessage("Purchase has been put on hold.").queue();
                    break;
            }
        }, failure -> {
            event.getHook().sendMessage("Error: Could not find the user who made this purchase.").queue();
        });
    }

    private void sendPrivateEmbed(User user, MessageEmbed embed) {
        user.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed).queue(),
                error -> plugin.getLogger().warning("Failed to send DM to " + user.getAsTag() + ": " + error.getMessage())
        );
    }
}