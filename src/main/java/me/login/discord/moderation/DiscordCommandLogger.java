package me.login.discord.moderation; // <-- CHANGED

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class DiscordCommandLogger {

    private final Login plugin;
    private final JDA jda;
    private final long normalChannelId;
    private final long staffChannelId;

    /**
     * Creates a logger for Discord commands using the shared JDA instance.
     * @param plugin The main plugin instance.
     * @param jda The shared JDA instance (e.g., from LagClearLogger).
     */
    public DiscordCommandLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;

        // Load channel IDs from config.yml
        this.normalChannelId = plugin.getConfig().getLong("normal-bot-channel", 0);
        this.staffChannelId = plugin.getConfig().getLong("staff-bot-channel", 0);

        if (this.jda == null) {
            plugin.getLogger().warning("DiscordCommandLogger received a null JDA! Command logging will be disabled.");
        }
        if (this.normalChannelId == 0) {
            plugin.getLogger().warning("'normal-bot-channel' not set in config.yml. Normal command logging will be disabled.");
        }
        if (this.staffChannelId == 0) {
            plugin.getLogger().warning("'staff-bot-channel' not set in config.yml. Staff command logging will be disabled.");
        }
    }

    /**
     * Sends a log message to the normal (public) bot channel.
     * @param message The message content to send.
     */
    public void logNormal(String message) {
        sendLog(normalChannelId, message, "Normal");
    }

    /**
     * Sends a log message to the staff-only bot channel.
     * @param message The message content to send.
     */
    public void logStaff(String message) {
        sendLog(staffChannelId, message, "Staff");
    }

    private void sendLog(long channelId, String message, String logType) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return;
        }

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue(
                    null,
                    error -> plugin.getLogger().warning("Failed to send Discord " + logType + " log: " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("Discord " + logType + " log channel (" + channelId + ") not found.");
        }
    }
}