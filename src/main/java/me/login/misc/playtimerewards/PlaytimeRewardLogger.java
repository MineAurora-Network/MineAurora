package me.login.misc.playtimerewards;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class PlaytimeRewardLogger {

    private final Login plugin;
    private final JDA jda;
    private final long channelId;

    public PlaytimeRewardLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda; // This JDA is shared from LagClearLogger
        this.channelId = plugin.getConfig().getLong("playtime-reward-channel-id", 0);

        if (this.jda == null) {
            plugin.getLogger().warning("PlaytimeRewardLogger: JDA is null. Discord logging will be disabled.");
        } else if (this.channelId == 0) {
            plugin.getLogger().warning("PlaytimeRewardLogger: 'playtime-reward-channel-id' is not set in config.yml. Discord logging will be disabled.");
        }
    }

    public void log(String message) {
        // Also log to console
        plugin.getLogger().info("[PlaytimeReward Log] " + message);

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return; // Don't even try if JDA is bad or channel ID is missing
        }

        try {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage("[PlaytimeReward] " + message).queue(
                        null, // Success
                        error -> plugin.getLogger().warning("Failed to send PlaytimeReward log: " + error.getMessage())
                );
            } else {
                plugin.getLogger().warning("PlaytimeReward log channel (" + channelId + ") not found.");
            }
        } catch (Exception e) {
            plugin.getLogger().warning("An error occurred while sending PlaytimeReward log: " + e.getMessage());
        }
    }

    // No shutdown() method needed as we are sharing the JDA from LagClearLogger
}