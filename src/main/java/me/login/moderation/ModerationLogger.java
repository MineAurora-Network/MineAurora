package me.login.moderation;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class ModerationLogger {

    private final Login plugin;
    private final JDA jda;

    // Channel IDs
    private final long banChannelId;
    private final long muteChannelId;
    private final long kickChannelId;

    public enum LogType {
        BAN, MUTE, KICK, UNBAN, UNMUTE
    }

    public ModerationLogger(Login plugin) {
        this.plugin = plugin;

        // Use shared JDA
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
            plugin.getLogger().warning("ModerationLogger: LagClearLogger is not initialized. Discord logging disabled.");
        }

        // Load separate channel IDs
        this.banChannelId = plugin.getConfig().getLong("moderation-ban-channel-id", 0);
        this.muteChannelId = plugin.getConfig().getLong("moderation-mute-channel-id", 0);
        this.kickChannelId = plugin.getConfig().getLong("moderation-kick-channel-id", 0);
    }

    public void log(LogType type, String discordMessage) {
        // 1. Clean Console Log (Remove markdown like ` and **)
        String consoleMessage = discordMessage.replace("`", "").replace("**", "");
        plugin.getLogger().info("[Moderation] " + consoleMessage);

        // 2. Discord Log
        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) {
            long targetChannelId = 0;

            switch (type) {
                case BAN:
                case UNBAN:
                    targetChannelId = banChannelId;
                    break;
                case MUTE:
                case UNMUTE:
                    targetChannelId = muteChannelId;
                    break;
                case KICK:
                    targetChannelId = kickChannelId;
                    break;
            }

            if (targetChannelId != 0) {
                MessageChannel channel = jda.getTextChannelById(targetChannelId);
                if (channel != null) {
                    channel.sendMessage(discordMessage).queue();
                }
            }
        }
    }
}