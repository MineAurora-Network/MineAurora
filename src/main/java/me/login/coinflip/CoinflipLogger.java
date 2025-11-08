package me.login.coinflip;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class CoinflipLogger {

    private final Login plugin;
    private final JDA jda;
    private final long gameLogChannelId;
    private final long adminLogChannelId;

    public CoinflipLogger(Login plugin) {
        this.plugin = plugin;
        this.jda = plugin.getLagClearLogger().getJDA(); // Use the shared logger bot JDA
        this.gameLogChannelId = plugin.getConfig().getLong("coinflip-log-channel-id", 0);
        this.adminLogChannelId = plugin.getConfig().getLong("coinflip-admin-log-channel-id", 0);

        if (jda == null) {
            plugin.getLogger().warning("CoinflipLogger: JDA is null. Coinflip logging is disabled.");
        }
        if (gameLogChannelId == 0) {
            plugin.getLogger().warning("CoinflipLogger: 'coinflip-log-channel-id' not set in config.yml. Game logging disabled.");
        }
        if (adminLogChannelId == 0) {
            plugin.getLogger().warning("CoinflipLogger: 'coinflip-admin-log-channel-id' not set in config.yml. Admin logging disabled.");
        }
    }

    /**
     * Logs general game events (create, win, non-admin cancel).
     * @param message The message to log.
     */
    public void logGame(String message) {
        plugin.getLogger().info("[CoinflipGame] " + message);
        sendLog(gameLogChannelId, "[Game] " + message);
    }

    /**
     * Logs admin actions (admin cancel).
     * @param message The message to log.
     */
    public void logAdmin(String message) {
        plugin.getLogger().info("[CoinflipAdmin] " + message);
        sendLog(adminLogChannelId, "[Admin] " + message);
    }

    private void sendLog(long channelId, String message) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return;
        }

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.sendMessage(message).queue(
                    null,
                    error -> plugin.getLogger().warning("Failed to send Coinflip log to channel " + channelId + ": " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("Coinflip log channel (" + channelId + ") not found.");
        }
    }
}