package me.login.items;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class ArmorLogger {

    private final Login plugin;
    private final long channelId;

    public ArmorLogger(Login plugin) {
        this.plugin = plugin;
        this.channelId = plugin.getConfig().getLong("armor-system.log-channel-id", 0L);
    }

    public void log(String message) {
        // Log to console
        plugin.getLogger().info("[ArmorLog] " + message);

        // Log to Discord using shared JDA
        if (plugin.getLagClearLogger() != null) {
            JDA jda = plugin.getLagClearLogger().getJDA();
            if (jda != null) {
                TextChannel channel = jda.getTextChannelById(channelId);
                if (channel != null) {
                    channel.sendMessage(message).queue();
                }
            }
        }
    }
}