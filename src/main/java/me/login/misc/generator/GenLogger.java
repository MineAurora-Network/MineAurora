package me.login.misc.generator;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class GenLogger {

    private final Login plugin;
    private final JDA jda;
    private final long logChannelId;
    private final long adminLogChannelId;

    public GenLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;
        // Load channels from config
        this.logChannelId = plugin.getConfig().getLong("generator.log-channel-id", 0);
        this.adminLogChannelId = plugin.getConfig().getLong("generator.admin-log-channel-id", 0);
    }

    public void logPlace(String player, String genType, String location) {
        logToDiscord(logChannelId, "**Gen Place** 🟩\nPlayer: `" + player + "`\nType: `" + genType + "`\nLoc: `" + location + "`");
    }

    public void logBreak(String player, String genType, String location) {
        logToDiscord(logChannelId, "**Gen Break** 🟥\nPlayer: `" + player + "`\nType: `" + genType + "`\nLoc: `" + location + "`");
    }

    public void logUpgrade(String player, String oldTier, String newTier, double cost, String currency) {
        logToDiscord(logChannelId, "**Gen Upgrade** ⬆️\nPlayer: `" + player + "`\nUpgrade: `" + oldTier + "` -> `" + newTier + "`\nCost: `" + cost + " " + currency + "`");
    }

    public void logAdmin(String admin, String action, String target) {
        logToDiscord(adminLogChannelId, "**Gen Admin** 🛡️\nAdmin: `" + admin + "`\nAction: `" + action + "`\nTarget: `" + target + "`");
    }

    private void logToDiscord(long channelId, String message) {
        if (jda == null || channelId == 0) return;
        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        }
    }
}