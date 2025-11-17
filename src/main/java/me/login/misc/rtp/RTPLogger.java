package me.login.misc.rtp;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
// Correct Import: Matches your LagClearLogger
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class RTPLogger {

    private final Login plugin;
    private final JDA jda;
    private final long generalLogChannelId; // For general info like module startup
    private final long rtpLogChannelId; // For specific RTP events

    public RTPLogger(Login plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Get JDA from the main plugin's LagClearLogger instance
        this.jda = (plugin.getLagClearLogger() != null) ? plugin.getLagClearLogger().getJDA() : null;

        // Use lagclear channel for general module logs (enable/disable)
        this.generalLogChannelId = config.getLong("lagclear-log-channel-id", 0L);
        // Use rtp channel for specific teleport logs (as requested)
        this.rtpLogChannelId = config.getLong("rtp-log-channel-id", 0L);
    }

    private String getTimestamp() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Logs a general message (like module status) to the main log channel.
     *
     * @param message The message to log.
     */
    public void log(String message) {
        // Log to console
        plugin.getLogger().info("[RTP] " + message);

        // Log to Discord
        if (jda == null || generalLogChannelId == 0L || jda.getStatus() != JDA.Status.CONNECTED) {
            return;
        }
        // Correct Type: Matches your LagClearLogger
        MessageChannel channel = jda.getTextChannelById(generalLogChannelId);
        if (channel != null) {
            channel.sendMessage("`[" + getTimestamp() + "]` **[RTP]** " + message).queue();
        }
    }

    /**
     * Logs a random teleport event to the specific RTP Discord channel.
     *
     * @param playerName The name of the player who teleported.
     * @param loc        The safe location they were teleported to.
     */
    public void logRTP(String playerName, Location loc) {
        if (jda == null || rtpLogChannelId == 0L || jda.getStatus() != JDA.Status.CONNECTED) {
            return; // RTP logging disabled or not configured
        }
        // Correct Type: Matches your LagClearLogger
        MessageChannel channel = jda.getTextChannelById(rtpLogChannelId);
        if (channel != null) {
            String worldName = loc.getWorld() != null ? loc.getWorld().getName() : "unknown";
            String message = String.format("`[%s]` **RTP:** Player `%s` teleported to `%s` [X: %d, Y: %d, Z: %d]",
                    getTimestamp(),
                    playerName,
                    worldName,
                    loc.getBlockX(),
                    loc.getBlockY(),
                    loc.getBlockZ()
            );
            channel.sendMessage(message).queue();
        }
    }
}