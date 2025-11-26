package me.login.misc.playtimerewards;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class PlaytimeRewardLogger {

    private final Login plugin;
    private final JDA jda;
    private final long logChannelId;

    // 1-arg constructor (Used if no JDA passed)
    public PlaytimeRewardLogger(Login plugin) {
        this(plugin, null);
    }

    // FIXED: 2-arg constructor required by PlaytimeRewardModule
    public PlaytimeRewardLogger(Login plugin, JDA jda) {
        this.plugin = plugin;

        if (jda != null) {
            this.jda = jda;
        } else if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
            plugin.getLogger().warning("LagClearLogger not found. PlaytimeReward Discord logging disabled.");
        }

        this.logChannelId = plugin.getConfig().getLong("playtimereward-log-channel-id", 0);

        if (logChannelId == 0) {
            plugin.getLogger().warning("PlaytimeRewardLogger: 'playtimereward-log-channel-id' not set in config.yml. Discord logging disabled.");
        }
    }

    public void logClaim(String playerName, int level, int coins, int tokens) {
        String discordLog = "[PlaytimeReward Log] `" + playerName + "` claimed Playtime Level `" + level + "` (`" + coins + "` coins, `" + tokens + "` tokens).";
        log(discordLog);
    }

    private void log(String message) {
        String consoleMessage = message.replace("`", "");
        plugin.getLogger().info(consoleMessage);

        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED && logChannelId != 0) {
            MessageChannel channel = jda.getTextChannelById(logChannelId);
            if (channel != null) {
                channel.sendMessage(message).queue(null, error ->
                        plugin.getLogger().warning("Failed to send PlaytimeReward log: " + error.getMessage())
                );
            }
        }
    }
}