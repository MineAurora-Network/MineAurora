package me.login.misc.milestones;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class MilestoneLogger {

    private final Login plugin;
    private final JDA jda;
    private final long channelId;

    public MilestoneLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        this.channelId = plugin.getConfig().getLong("milestone-log-channel-id", 0);
    }

    public void logClaim(String playerName, int milestone, int tokens, int streak) {
        if (jda == null || channelId == 0) return;

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            String msg = String.format("Player `%s` claimed **Milestone %d** (Streak: %d) and received %d Tokens.",
                    playerName, milestone, streak, tokens);
            channel.sendMessage("[Milestone] " + msg).queue();
        }
    }
}