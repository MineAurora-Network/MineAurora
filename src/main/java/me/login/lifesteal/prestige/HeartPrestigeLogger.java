package me.login.lifesteal.prestige;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class HeartPrestigeLogger {

    private final Login plugin;
    private final JDA jda;
    private final long channelId;

    public HeartPrestigeLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        this.channelId = plugin.getConfig().getLong("heart-prestige-log-channel-id", 0);
    }

    public void logPrestige(String playerName, int newLevel, int costPaid) {
        if (jda == null || channelId == 0) return;

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            String msg = String.format("Player `%s` upgraded to **Heart Prestige Tier %d**! Cost: %d Hearts.",
                    playerName, newLevel, costPaid);
            channel.sendMessage("[Heart Prestige] " + msg).queue();
        }
    }
}