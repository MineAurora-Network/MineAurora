package me.login.misc.dailyreward;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class DailyRewardLogger {

    private final Login plugin;
    private final JDA jda;
    private final long logChannelId;

    public DailyRewardLogger(Login plugin) {
        this.plugin = plugin;
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
            plugin.getLogger().warning("LagClearLogger not found. DailyReward Discord logging disabled.");
        }

        this.logChannelId = plugin.getConfig().getLong("dailyreward-log-channel-id", 0);

        if (logChannelId == 0) {
            plugin.getLogger().warning("DailyRewardLogger: 'dailyreward-log-channel-id' not set in config.yml. Discord logging disabled.");
        }
    }

    // FIXED: Added shutdown method required by Module
    public void shutdown() {
        // No specific shutdown needed for JDA as it's shared, but method is required by Module contract
    }

    public void logDefault(String playerName, int coins, int tokens, int streak) {
        String discordLog = "[DailyReward Log] `" + playerName + "` claimed their Daily reward (`" + coins + "` coins, `" + tokens + "` tokens, Streak: `" + streak + "`).";
        log(discordLog);
    }

    public void logRanked(String playerName, String rankPrettyName, int coins, int tokens, int streak) {
        String discordLog = "[DailyReward Log] `" + playerName + "` claimed their " + rankPrettyName + " reward (`" + coins + "` coins, `" + tokens + "` tokens, Streak: `" + streak + "`).";
        log(discordLog);
    }

    private void log(String message) {
        String consoleMessage = message.replace("`", "").replace("<b>", "").replace("</b>", "")
                .replace("<green>", "").replace("</green>", "")
                .replace("<blue>", "").replace("</blue>", "")
                .replace("<red>", "").replace("</red>", "")
                .replace("<gold>", "").replace("</gold>", "")
                .replace("<dark_red>", "").replace("</dark_red>", "")
                .replace("<dark_purple>", "").replace("</dark_purple>", "");

        plugin.getLogger().info(consoleMessage);

        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED && logChannelId != 0) {
            MessageChannel channel = jda.getTextChannelById(logChannelId);
            if (channel != null) {
                channel.sendMessage(message).queue(null, error ->
                        plugin.getLogger().warning("Failed to send DailyReward log: " + error.getMessage())
                );
            }
        }
    }
}