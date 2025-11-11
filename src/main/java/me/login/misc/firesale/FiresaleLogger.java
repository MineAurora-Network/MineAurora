package me.login.misc.firesale;

import me.login.misc.firesale.model.Firesale;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.awt.Color;
import java.time.Instant;

public class FiresaleLogger {

    private final JDA jda;
    private final String channelId;

    public FiresaleLogger(JDA jda, String channelId) {
        this.jda = jda;
        this.channelId = channelId;
    }

    private void sendLog(MessageEmbed embed) {
        if (jda == null || channelId.isEmpty()) {
            return; // Logging disabled
        }
        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessageEmbeds(embed).queue();
            }
        } catch (Exception e) {
            // Log error
            System.err.println("Failed to send Firesale Discord log: " + e.getMessage());
        }
    }

    public void logSaleCreated(Player creator, Firesale sale) {
        String itemName = sale.getItem().getItemMeta().hasDisplayName() ?
                sale.getItem().getItemMeta().getDisplayName() :
                sale.getItem().getType().toString();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Created")
                .setColor(Color.CYAN)
                .setAuthor(creator.getName(), null, "https://crafatar.com/avatars/" + creator.getUniqueId() + "?overlay")
                .addField("Sale ID", "`" + sale.getSaleId() + "`", true)
                .addField("Item", "`" + itemName + "`", true)
                .addField("Quantity", "`" + sale.getInitialQuantity() + "`", true)
                .addField("Price", "`" + sale.getPrice() + " Credits`", true)
                .addField("Starts At", "<t:" + sale.getStartTime().getEpochSecond() + ":R>", true)
                .addField("Ends At", "<t:" + sale.getEndTime().getEpochSecond() + ":R>", true)
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }

    public void logSaleRemoved(Player admin, Firesale sale) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Removed")
                .setColor(Color.ORANGE)
                .setAuthor(admin.getName(), null, "https://crafatar.com/avatars/" + admin.getUniqueId() + "?overlay")
                .addField("Sale ID", "`" + sale.getSaleId() + "`", true)
                .addField("Status", "`" + sale.getStatus().toString() + "`", true)
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }

    public void logSaleStart(Firesale sale) {
        String itemName = sale.getItem().getItemMeta().hasDisplayName() ?
                sale.getItem().getItemMeta().getDisplayName() :
                sale.getItem().getType().toString();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Active!")
                .setColor(Color.GREEN)
                .setDescription("Firesale **ID: " + sale.getSaleId() + "** is now active.")
                .addField("Item", "`" + itemName + "`", true)
                .addField("Quantity", "`" + sale.getRemainingQuantity() + "`", true)
                .addField("Price", "`" + sale.getPrice() + " Credits`", true)
                .addField("Ends At", "<t:" + sale.getEndTime().getEpochSecond() + ":R>", true)
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }

    public void logSaleEnd(Firesale sale, String reason) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Ended")
                .setColor(Color.GRAY)
                .setDescription("Firesale **ID: " + sale.getSaleId() + "** has ended.")
                .addField("Reason", "`" + reason + "`", true)
                .addField("Status", "`" + sale.getStatus().toString() + "`", true)
                .addField("Sold", "`" + sale.getTotalSold() + " / " + sale.getInitialQuantity() + "`", true)
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }

    public void logPurchase(Player player, Firesale sale, int amount) {
        String itemName = sale.getItem().getItemMeta().hasDisplayName() ?
                sale.getItem().getItemMeta().getDisplayName() :
                sale.getItem().getType().toString();

        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Purchase")
                .setColor(Color.BLUE)
                .setAuthor(player.getName(), null, "https://crafatar.com/avatars/" + player.getUniqueId() + "?overlay")
                .setDescription("`" + player.getName() + "` purchased `" + amount + "x " + itemName + "`")
                .addField("Sale ID", "`" + sale.getSaleId() + "`", true)
                .addField("Price Paid", "`" + (sale.getPrice() * amount) + " Credits`", true)
                .addField("Stock Remaining", "`" + sale.getRemainingQuantity() + "`", true)
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }

    public void logAdminCommand(Player admin, String fullCommand) {
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle("Firesale Admin Command")
                .setColor(new Color(0xAE86FF)) // Purple
                .setAuthor(admin.getName(), null, "https://crafatar.com/avatars/" + admin.getUniqueId() + "?overlay")
                .setDescription("`/" + fullCommand + "`")
                .setTimestamp(Instant.now());
        sendLog(embed.build());
    }
}