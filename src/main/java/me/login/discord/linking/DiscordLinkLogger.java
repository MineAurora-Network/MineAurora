package me.login.discord.linking; // <-- CHANGED

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class DiscordLinkLogger {

    private final Login plugin;
    private final JDA jda;
    private final long logChannelId;

    /**
     * Creates a logger that uses the shared JDA instance (e.g., from LagClearLogger).
     * @param plugin The main plugin instance.
     * @param jda The shared JDA instance to use for logging.
     */
    public DiscordLinkLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;

        // Make sure to add 'link-system-log-channel-id' to your config.yml
        this.logChannelId = plugin.getConfig().getLong("link-system-log-channel-id", 0);

        if (this.jda == null) {
            plugin.getLogger().warning("DiscordLinkLogger received a null JDA! Link logging will be disabled.");
        }
        if (this.logChannelId == 0) {
            plugin.getLogger().warning("'link-system-log-channel-id' not set in config.yml. DiscordLink logging will be disabled.");
        }
    }

    /**
     * Sends a log message to the designated link-log channel.
     * @param message The message content to send.
     */
    public void sendLog(String message) {
        // Also log to console
        plugin.getLogger().info("[DiscordLink Log] " + message.replace("`", "").replace("*", ""));

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || logChannelId == 0) {
            return;
        }

        MessageChannel channel = jda.getTextChannelById(logChannelId);
        if (channel != null) {
            channel.sendMessage("[Link] " + message).queue(
                    null,
                    error -> plugin.getLogger().warning("Failed to send DiscordLink log: " + error.getMessage())
            );
        }
    }
}