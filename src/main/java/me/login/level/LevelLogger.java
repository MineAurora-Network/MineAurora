package me.login.level;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class LevelLogger {

    private final Login plugin;
    private final JDA jda;
    private final long logChannelId;
    private final long adminLogChannelId;

    public LevelLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        this.logChannelId = plugin.getConfig().getLong("lifesteal-level-log-channel-id", 0);
        this.adminLogChannelId = plugin.getConfig().getLong("lifesteal-level-admin-log-channel-id", 0);
    }

    public void logLevelUp(String playerName, int newLevel) {
        sendLog(logChannelId, ":arrow_up: Player **" + playerName + "** leveled up to **Lifesteal Level " + newLevel + "**!");
    }

    public void logXpChange(String playerName, String reason, int amount, boolean gain) {
        // Optional: Can be spammy, maybe only log significant changes or keep for debug
        // sendLog(logChannelId, (gain ? ":heavy_plus_sign:" : ":heavy_minus_sign:") + " Player **" + playerName + "** " + (gain ? "gained" : "lost") + " " + amount + " XP (" + reason + ")");
    }

    public void logAdminSet(String adminName, String targetName, int level) {
        sendLog(adminLogChannelId, ":tools: Admin **" + adminName + "** set **" + targetName + "'s** level to **" + level + "**.");
    }

    private void sendLog(long channelId, String message) {
        if (jda == null || channelId == 0) return;
        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }
}