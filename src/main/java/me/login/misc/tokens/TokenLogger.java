package me.login.misc.tokens;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class TokenLogger {

    private final Login plugin;
    private final JDA jda;
    private final long shopChannelId;
    private final long adminChannelId;

    public TokenLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda; // This JDA is shared from LagClearLogger
        this.shopChannelId = plugin.getConfig().getLong("token-shop-channel-id", 0);
        this.adminChannelId = plugin.getConfig().getLong("token-admin-channel-id", 0);

        if (this.jda == null) {
            plugin.getLogger().warning("TokenLogger: JDA is null. Discord logging will be disabled.");
        } else {
            if (this.shopChannelId == 0) {
                plugin.getLogger().warning("TokenLogger: 'token-shop-channel-id' is not set. Shop logging disabled.");
            }
            if (this.adminChannelId == 0) {
                plugin.getLogger().warning("TokenLogger: 'token-admin-channel-id' is not set. Admin logging disabled.");
            }
        }
    }

    /**
     * Logs a message to the shop channel.
     * @param message The message to send.
     */
    public void logShop(String message) {
        plugin.getLogger().info("[TokenShop Log] " + message);
        logToChannel(shopChannelId, "[TokenShop] " + message);
    }

    /**
     * Logs a message to the admin channel.
     * @param message The message to send.
     */
    public void logAdmin(String message) {
        plugin.getLogger().info("[TokenAdmin Log] " + message);
        logToChannel(adminChannelId, "[TokenAdmin] " + message);
    }

    private void logToChannel(long channelId, String message) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return; // Don't even try if JDA is bad or channel ID is missing
        }

        try {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(message).queue(
                        null, // Success
                        error -> plugin.getLogger().warning("Failed to send Token log to " + channelId + ": " + error.getMessage())
                );
            } else {
                plugin.getLogger().warning("Token log channel (" + channelId + ") not found.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while sending Token log: " + e.getMessage());
        }
    }
}