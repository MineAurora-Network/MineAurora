package me.login.misc.dailyreward;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

/**
 * Handles logging Daily Rewards to a specific Discord channel.
 * This uses the "logger-bot-token" from config.yml, same as LagClearLogger.
 */
public class DailyRewardLogger {

    private final Login plugin;
    private final JDA jda; // Re-uses the JDA from LagClearLogger
    private final long dailyRewardChannelId;

    public DailyRewardLogger(Login plugin) {
        this.plugin = plugin;

        // --- IMPORTANT ---
        // We will RE-USE the JDA instance from the LagClearLogger to avoid creating
        // a second bot connection with the same token.
        // This assumes LagClearLogger is initialized before this module in Login.java
        // ---
        if (plugin.getLagClearLogger() != null && plugin.getLagClearLogger().getJDA() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
            plugin.getLogger().info("DailyRewardLogger is re-using JDA from LagClearLogger.");
        } else {
            this.jda = null;
            plugin.getLogger().warning("DailyRewardLogger could not find LagClearLogger's JDA! Logging will be disabled.");
        }

        this.dailyRewardChannelId = plugin.getConfig().getLong("daily-reward-channel-id", 0);
        if (this.dailyRewardChannelId == 0) {
            plugin.getLogger().warning("daily-reward-channel-id not set in config.yml. Daily Reward logging disabled.");
        }
    }

    /**
     * Sends a log message to the configured daily reward channel.
     * @param message The message to send.
     */
    public void log(String message) {
        plugin.getLogger().info("[DailyReward Log] " + message);

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
            return; // JDA isn't ready
        }

        if (dailyRewardChannelId == 0) {
            return; // Channel not configured
        }

        MessageChannel channel = jda.getTextChannelById(dailyRewardChannelId);
        if (channel != null) {
            channel.sendMessage("[DailyReward] " + message).queue(
                    null, // Don't care about success
                    error -> plugin.getLogger().warning("Failed to send Daily Reward log: " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("Daily Reward log channel (" + dailyRewardChannelId + ") not found.");
        }
    }

    /**
     * Shutdown is handled by LagClearLogger, so we don't need to do anything here.
     */
    public void shutdown() {
    }
}