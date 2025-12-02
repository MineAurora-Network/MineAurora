package me.login.discord.store;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.misc.rank.RankManager;
import me.login.misc.rank.util.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class TicketListeners extends ListenerAdapter {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final TicketDatabase ticketDatabase;
    private final RankManager rankManager;
    private final LuckPerms luckPerms;

    private long ticketChannelId;
    private long storeAdminRoleId; // Role for closing tickets
    private long storeOwnerRoleId; // Role for confirming payments
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
        this.luckPerms = (provider != null) ? provider.getProvider() : null;
    }

    @Override
    public void onReady(ReadyEvent event) {
        this.ticketChannelId = plugin.getConfig().getLong("store-ticket-channel-id", 0);
        this.verificationChannelId = plugin.getConfig().getLong("payment-verification-channel-id", 0);

        // Load roles from config
        this.storeAdminRoleId = plugin.getConfig().getLong("store-admin-role-id", 0);
        this.storeOwnerRoleId = plugin.getConfig().getLong("store-owner-role-id", 0);
        this.storeHelperRoleId = plugin.getConfig().getLong("store-helper-role-id", 0);

        if (ticketChannelId == 0) {
            plugin.getLogger().warning("Store Bot: 'store-ticket-channel-id' is not set. Ticket panel disabled.");
            return;
        }

        TextChannel channel = event.getJDA().getTextChannelById(ticketChannelId);
        if (channel == null) return;

        // Cleanup old messages and send new panel
        channel.getHistory().retrievePast(20).queue(messages -> {
            List<Message> botMsgs = messages.stream().filter(m -> m.getAuthor().equals(event.getJDA().getSelfUser())).toList();
            if (!botMsgs.isEmpty()) {
                if (botMsgs.size() == 1) botMsgs.get(0).delete().queue();
                else channel.deleteMessages(botMsgs).queue();
            }

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(new Color(0x5865F2))
                    .setTitle("Create a Support Ticket")
                    .setDescription("Please select a category below to open a ticket.");

            StringSelectMenu menu = StringSelectMenu.create("create-ticket")
                    .setPlaceholder("Select a ticket type...")
                    .addOption("Purchase Issue", "purchase", "Issues with store purchases.", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("🛒"))
                    .addOption("General Enquiry", "enquiry", "General store questions.", net.dv8tion.jda.api.entities.emoji.Emoji.fromUnicode("❓"))
                    .build();

            channel.sendMessageEmbeds(eb.build()).setActionRow(menu).queue();
        });
    }

    @Override
    public void onStringSelectInteraction(StringSelectInteractionEvent event) {
        if (!event.getComponentId().equals("create-ticket")) return;

        Member member = event.getMember();
        if (member == null) return;
        Guild guild = event.getGuild();
        if (guild == null) return;

        String selection = event.getValues().get(0);
        String categoryName = selection.substring(0, 1).toUpperCase() + selection.substring(1);

        Category category = guild.getCategoriesByName(categoryName, true).stream().findFirst().orElse(null);
        Role adminRole = guild.getRoleById(storeAdminRoleId);

        if (adminRole == null) {
            event.reply("Error: Store Admin Role (ID: " + storeAdminRoleId + ") not found.").setEphemeral(true).queue();
            return;
        }

        if (category == null) {
            guild.createCategory(categoryName).queue(cat -> createTicketChannel(cat, adminRole, member, selection));
        } else {
            createTicketChannel(category, adminRole, member, selection);
        }

        event.reply("Creating your ticket...").setEphemeral(true).queue();
    }

    private void createTicketChannel(Category category, Role adminRole, Member member, String type) {
        String channelName = type + "-" + member.getUser().getName();

        category.createTextChannel(channelName)
                .addPermissionOverride(category.getGuild().getPublicRole(), null, EnumSet.of(Permission.VIEW_CHANNEL))
                .addPermissionOverride(adminRole, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
                .addPermissionOverride(member, EnumSet.of(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND), null)
                .queue(channel -> {
                    EmbedBuilder eb = new EmbedBuilder().setColor(Color.GREEN).setTitle("Support Ticket")
                            .setDescription("Welcome " + member.getAsMention() + "! Support will be with you shortly.");

                    Button close = Button.danger("close-ticket", "Close Ticket");
                    Button help = Button.primary("call-for-help", "Call for Help");

                    channel.sendMessage(member.getAsMention()).setEmbeds(eb.build()).setActionRow(close, help).queue();
                });
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String id = event.getComponentId();
        Member member = event.getMember();
        if (member == null) return;

        if (id.equals("close-ticket")) {
            // Permission Check: Store Admin Role OR Owner
            if (member.getRoles().stream().noneMatch(r -> r.getIdLong() == storeAdminRoleId) && !member.isOwner()) {
                event.reply("You do not have permission to close tickets (Required: Store Admin Role).").setEphemeral(true).queue();
                return;
            }
            event.reply("Closing ticket...").queue();
            event.getChannel().delete().queueAfter(3, TimeUnit.SECONDS);
        }
        else if (id.equals("confirm-purchase") || id.equals("deny-purchase") || id.equals("hold-purchase")) {
            // Permission Check: Store Owner Role OR Owner
            if (member.getRoles().stream().noneMatch(r -> r.getIdLong() == storeOwnerRoleId) && !member.isOwner()) {
                event.reply("You do not have permission to manage purchases (Required: Store Owner Role).").setEphemeral(true).queue();
                return;
            }
            handlePurchaseButton(event, member);
        }
        else if (id.equals("call-for-help")) {
            handleCallForHelp(event);
        }
    }

    private void handleCallForHelp(ButtonInteractionEvent event) {
        long channelId = event.getChannelIdLong();
        long now = System.currentTimeMillis();
        long last = helpCooldowns.getOrDefault(channelId, 0L);

        if (now - last < HELP_COOLDOWN_MS) {
            event.reply("Cooldown active.").setEphemeral(true).queue();
            return;
        }

        Role helperRole = event.getGuild().getRoleById(storeHelperRoleId);
        if (helperRole != null) {
            helpCooldowns.put(channelId, now);
            event.reply(helperRole.getAsMention() + " assistance required!").queue();
        } else {
            event.reply("Helper role not found.").setEphemeral(true).queue();
        }
    }

    private void handlePurchaseButton(ButtonInteractionEvent event, Member member) {
        event.deferEdit().queue();
        long messageId = event.getMessageIdLong();
        TicketDatabase.PurchaseData data = ticketDatabase.getPurchase(messageId);

        if (data == null || data.status != 0) {
            event.getHook().sendMessage("Purchase already actioned or not found.").setEphemeral(true).queue();
            return;
        }

        int status = 0;
        String statusTxt = "";
        Color color = Color.GRAY;

        switch (event.getComponentId()) {
            case "confirm-purchase": status=1; statusTxt="VERIFIED"; color=Color.GREEN; break;
            case "deny-purchase": status=2; statusTxt="DENIED"; color=Color.RED; break;
            case "hold-purchase": status=3; statusTxt="ON HOLD"; color=Color.ORANGE; break;
        }

        ticketDatabase.updatePurchaseStatus(messageId, status);

        MessageEmbed old = event.getMessage().getEmbeds().get(0);
        EmbedBuilder eb = new EmbedBuilder(old)
                .setTitle("Purchase " + statusTxt)
                .setColor(color)
                .setFooter("Actioned by " + member.getUser().getName());

        event.getMessage().editMessageEmbeds(eb.build()).setComponents().queue();

        final int finalStatus = status;
        final Color finalColor = color; // <--- Create final copy here

        event.getJDA().retrieveUserById(data.userId).queue(user -> {
            String msg = (finalStatus == 1) ? "Payment Confirmed! Items will arrive shortly." : "Payment issue. Please open an enquiry ticket.";
            sendPrivateEmbed(user, new EmbedBuilder().setColor(finalColor).setDescription(msg).build());
        });
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getName().equals("purchase")) {
            handlePurchase(event);
        }
    }

    private void handlePurchase(SlashCommandInteractionEvent event) {
        if (discordLinking == null) { event.reply("Link system unavailable.").setEphemeral(true).queue(); return; }
        event.deferReply(true).queue();

        UUID uuid = discordLinking.getLinkedUuid(event.getUser().getIdLong());
        if(uuid == null) {
            event.getHook().sendMessage("You must link your account first!").queue();
            return;
        }

        String purchaseItem, txnId, paidAmount;
        Message.Attachment screenshot;

        try {
            screenshot = Objects.requireNonNull(event.getOption("screenshot")).getAsAttachment();
            txnId = Objects.requireNonNull(event.getOption("txn-id")).getAsString();
            purchaseItem = Objects.requireNonNull(event.getOption("purchase-item")).getAsString();
            paidAmount = Objects.requireNonNull(event.getOption("paid-amount")).getAsString();
        } catch (Exception e) {
            event.getHook().sendMessage("Missing arguments.").queue();
            return;
        }

        TextChannel vChannel = event.getJDA().getTextChannelById(verificationChannelId);
        if(vChannel != null) {
            EmbedBuilder eb = new EmbedBuilder()
                    .setTitle("New Purchase Verification")
                    .setColor(Color.YELLOW)
                    .addField("User", event.getUser().getAsMention(), false)
                    .addField("Item", purchaseItem, true)
                    .addField("Amount", paidAmount, true)
                    .addField("TXN ID", txnId, false)
                    .setImage(screenshot.getProxyUrl())
                    .setTimestamp(Instant.now())
                    .setFooter("Status: PENDING");

            vChannel.sendMessageEmbeds(eb.build()).setActionRow(
                    Button.success("confirm-purchase", "Confirm"),
                    Button.danger("deny-purchase", "Deny"),
                    Button.secondary("hold-purchase", "Hold")
            ).queue(msg -> {
                ticketDatabase.addPurchase(msg.getIdLong(), event.getUser().getIdLong(), purchaseItem);
                event.getHook().sendMessage("Purchase submitted for review.").queue();
            });
        } else {
            event.getHook().sendMessage("Verification channel not set up.").queue();
        }
    }

    private void sendPrivateEmbed(User user, MessageEmbed embed) {
        user.openPrivateChannel().queue(c -> c.sendMessageEmbeds(embed).queue());
    }
}