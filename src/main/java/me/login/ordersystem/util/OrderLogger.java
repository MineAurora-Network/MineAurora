package me.login.ordersystem.util;

import me.login.Login;
import me.login.ordersystem.data.Order; // --- FIX: Correct import ---
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.text.DecimalFormat;
import java.time.Instant; // --- FIX: Added import ---
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles logging all Order System actions to Discord via JDA.
 * (Point 11)
 */
public class OrderLogger {

    private final Login plugin;
    private JDA jda;
    private final String creationChannelId;
    private final String fillChannelId;
    private final String manageChannelId;
    private final String adminChannelId;
    private final String errorChannelId;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "OrderLogger-DiscordThread");
        t.setDaemon(true);
        return t;
    });

    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");

    public OrderLogger(Login plugin) {
        this.plugin = plugin;
        this.jda = plugin.getJda();

        // Load channel IDs from config.yml
        // --- FIX: Added .discord-logging prefix ---
        this.creationChannelId = plugin.getConfig().getString("discord-logging.order-channels.creation", "");
        this.fillChannelId = plugin.getConfig().getString("discord-logging.order-channels.filling", "");
        this.manageChannelId = plugin.getConfig().getString("discord-logging.order-channels.management", "");
        this.adminChannelId = plugin.getConfig().getString("discord-logging.order-channels.admin", "");
        this.errorChannelId = plugin.getConfig().getString("discord-logging.order-channels.errors", "");

        if (creationChannelId.isEmpty() || fillChannelId.isEmpty() || manageChannelId.isEmpty() || adminChannelId.isEmpty() || errorChannelId.isEmpty()) {
            plugin.getLogger().warning("[OrderSystem] Discord logging channel IDs are not fully configured!");
        }
    }

    public void logCreation(Order order) {
        executor.submit(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order Created (ID: " + order.getOrderId() + ")")
                    .setColor(Color.GREEN)
                    .addField("Placer", order.getPlacerName() + " (`" + order.getPlacerUUID() + "`)", false)
                    .addField("Item", order.getFormattedItemName(), true)
                    .addField("Quantity", String.valueOf(order.getTotalAmount()), true)
                    .addField("Price / Item", "$" + moneyFormat.format(order.getPricePerItem()), true)
                    .addField("Total Value", "$" + moneyFormat.format(order.getTotalPrice()), true)
                    .setTimestamp(Instant.ofEpochMilli(order.getCreationTimestamp())); // --- FIX: Use Instant ---
            sendEmbed(creationChannelId, embed);
        });
    }

    public void logFill(Player filler, Order order, int amountFilled, double payment) {
        executor.submit(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order Filled (ID: " + order.getOrderId() + ")")
                    .setColor(Color.CYAN)
                    .addField("Filler", filler.getName() + " (`" + filler.getUniqueId() + "`)", false)
                    .addField("Placer", order.getPlacerName(), false)
                    .addField("Item", order.getFormattedItemName(), true)
                    .addField("Amount", String.valueOf(amountFilled), true)
                    .addField("Payout", "$" + moneyFormat.format(payment), true)
                    .setTimestamp(Instant.now()); // --- FIX: Use Instant ---
            sendEmbed(fillChannelId, embed);
        });
    }

    public void logClaim(Player placer, long orderId, int itemsClaimed) {
        executor.submit(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order Items Claimed (ID: " + orderId + ")")
                    .setColor(Color.ORANGE)
                    .addField("Placer", placer.getName() + " (`" + placer.getUniqueId() + "`)", false)
                    .addField("Items Claimed", String.valueOf(itemsClaimed), true)
                    .setTimestamp(Instant.now()); // --- FIX: Use Instant ---
            sendEmbed(manageChannelId, embed);
        });
    }

    public void logCancel(Player placer, Order order, double refundAmount) {
        executor.submit(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order Cancelled (ID: " + order.getOrderId() + ")")
                    .setColor(Color.YELLOW)
                    .addField("Placer", placer.getName() + " (`" + placer.getUniqueId() + "`)", false)
                    .addField("Refunded", "$" + moneyFormat.format(refundAmount), true)
                    .addField("Items Returned", String.valueOf(order.getAmountDelivered()), true)
                    .setTimestamp(Instant.now()); // --- FIX: Use Instant ---
            sendEmbed(manageChannelId, embed);
        });
    }

    public void logAdminCancel(Player admin, Order order, double refund, int itemsReturned) {
        executor.submit(() -> {
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order Force Cancelled (ID: " + order.getOrderId() + ")")
                    .setColor(Color.RED)
                    .addField("Admin", admin.getName() + " (`" + admin.getUniqueId() + "`)", false)
                    .addField("Placer", order.getPlacerName() + " (`" + order.getPlacerUUID() + "`)", false)
                    .addField("Refunded", "$" + moneyFormat.format(refund), true)
                    .addField("Items Returned", String.valueOf(itemsReturned), true)
                    .setTimestamp(Instant.now()); // --- FIX: Use Instant ---
            sendEmbed(adminChannelId, embed);
        });
    }

    public void logError(String action, Throwable error, long orderId) {
        executor.submit(() -> {
            String message = error != null ? (error.getMessage() != null ? error.getMessage() : "N/A") : "Unknown Error";
            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Order System Error")
                    .setColor(Color.BLACK)
                    .addField("Action", action, false)
                    .addField("Order ID", String.valueOf(orderId), true)
                    .addField("Error", "```" + message + "```", false)
                    .setTimestamp(Instant.now()); // --- FIX: Use Instant ---
            sendEmbed(errorChannelId, embed);
        });
    }

    private void sendEmbed(String channelId, EmbedBuilder embed) {
        if (jda == null) {
            this.jda = plugin.getJda(); // Attempt to re-acquire JDA
            if (jda == null) {
                // plugin.getLogger().warning("JDA is null, cannot send Discord log.");
                return;
            }
        }
        if (channelId.isEmpty() || channelId.equals("0")) return; // --- FIX: Check for "0"

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed.build()).queue();
            } else {
                plugin.getLogger().warning("[OrderLogger] Invalid channel ID: " + channelId);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("[OrderLogger] Failed to send Discord embed: " + e.getMessage());
        }
    }
}