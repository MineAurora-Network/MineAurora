package me.login.loginsystem;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

public class LoginSystemLogger {

    private final Login plugin;
    private final JDA jda;
    private final long loginChannelId;
    private final long adminLoginChannelId;
    private final long parkourChannelId;

    public LoginSystemLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;

        this.loginChannelId = plugin.getConfig().getLong("login-log-channel-id", 0L);
        this.adminLoginChannelId = plugin.getConfig().getLong("admin-login-log-channel-id", 0L);
        this.parkourChannelId = plugin.getConfig().getLong("parkour-log-channel-id", 0L);
    }

    public void logLogin(String message) {
        logToChannel(loginChannelId, "[Login] " + message);
    }

    public void logAdmin(String message) {
        logToChannel(adminLoginChannelId, "[Admin Login] " + message);
    }

    public void logParkour(String message) {
        logToChannel(parkourChannelId, "[Parkour] " + message);
    }

    private void logToChannel(long channelId, String message) {
        // Always log to console as fallback
        plugin.getLogger().info(message);

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) {
            return;
        }

        if (channelId == 0) {
            return;
        }

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue();
        } else {
            plugin.getLogger().warning("Could not find Discord channel with ID: " + channelId);
        }
    }
}