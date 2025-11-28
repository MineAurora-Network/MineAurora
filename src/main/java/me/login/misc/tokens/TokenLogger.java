package me.login.misc.tokens;

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

import java.awt.Color;
import java.time.Instant;

public class TokenLogger {

    private final Login plugin;
    private final JDA jda;
    private final long shopChannelId;
    private final long adminChannelId;

    public TokenLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        this.shopChannelId = plugin.getConfig().getLong("token-shop-channel-id", 0);
        this.adminChannelId = plugin.getConfig().getLong("token-admin-channel-id", 0);

        if (this.jda == null) {
            plugin.getLogger().warning("TokenLogger: JDA is null. Discord logging will be disabled.");
        }
    }

    public void logShop(String message) {
        // Strip backticks for console log
        plugin.getLogger().info("[TokenShop Log] " + message.replace("`", ""));
        logToChannel(shopChannelId, "[TokenShop] " + message);
    }

    // Updated to support Embeds
    public void logAdmin(String adminName, String action, String targetName, long amount) {
        // Console Log (Stripped)
        plugin.getLogger().info("[TokenAdmin Log] " + adminName + " " + action + " " + amount + " tokens to/from/of " + targetName);

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || adminChannelId == 0) return;

        try {
            MessageChannel channel = jda.getTextChannelById(adminChannelId);
            if (channel != null) {
                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Token Admin Action");
                embed.setColor(Color.decode("#f1c40f")); // Gold/Yellow color

                embed.addField("Administrator", "`" + adminName + "`", true);
                embed.addField("Action", action, true);
                embed.addField("User", "`" + targetName + "`", true);
                embed.addField("Amount", "`" + amount + "`", true);

                embed.setTimestamp(Instant.now());
                embed.setFooter("MineAurora Token System");

                channel.sendMessageEmbeds(embed.build()).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to send Token Admin Embed: " + e.getMessage());
        }
    }

    private void logToChannel(long channelId, String message) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) return;

        try {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error sending Token log: " + e.getMessage());
        }
    }
}