package me.login.pets;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.Instant;

public class PetsLogger {

    private final Login plugin;
    private String discordChannelId;
    private String discordAdminChannelId;

    public PetsLogger(Login plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.discordChannelId = plugin.getConfig().getString("discord.pets_log_channel_id", "");
        this.discordAdminChannelId = plugin.getConfig().getString("discord.pets_admin_log_channel_id", "");
    }

    public void log(String message) {
        plugin.getLogger().info("[Pets] " + message);
    }

    public void logCapture(String playerName, String petType, String itemName) {
        String logMessage = playerName + " captured a " + petType + " using " + itemName + ".";
        log(logMessage);

        // FIX: Ensure correct channel ID usage
        sendEmbedToDiscord(
                discordChannelId,
                "Pet Captured!",
                logMessage,
                Color.GREEN,
                "Player: " + playerName + "\nPet: " + petType + "\nItem: " + itemName
        );
    }

    public void logAdmin(String adminName, String message) {
        String logMessage = "[ADMIN] " + adminName + " " + message;
        log(logMessage);

        // FIX: Ensure admin channel ID usage
        sendEmbedToDiscord(
                discordAdminChannelId,
                "Pet Admin Action",
                message,
                Color.ORANGE,
                "Admin: " + adminName
        );
    }

    public void logRename(String playerName, String petType, String oldName, String newName) {
        String logMessage = playerName + " renamed their " + petType + " from '" + oldName + "' to '" + newName + "'.";
        log(logMessage);

        sendEmbedToDiscord(
                discordAdminChannelId,
                "Pet Renamed",
                logMessage,
                Color.CYAN,
                "Player: " + playerName
        );
    }

    public void logError(String errorMessage) {
        plugin.getLogger().severe("[Pets] " + errorMessage);

        sendEmbedToDiscord(
                discordChannelId,
                "Pet System Error",
                errorMessage,
                Color.RED,
                null
        );
    }

    private void sendEmbedToDiscord(String channelId, String title, String description, Color color, String fieldsContent) {
        if (channelId == null || channelId.isEmpty() || channelId.equals("YOUR_PET_LOG_CHANNEL_ID_HERE")) {
            return;
        }

        try {
            LagClearLogger lagClearLogger = plugin.getLagClearLogger();
            if (lagClearLogger == null) {
                return;
            }
            JDA jda = lagClearLogger.getJDA();
            if (jda == null) {
                return;
            }

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log("Pets log channel with ID " + channelId + " not found on Discord.");
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle(title)
                    .setDescription(description)
                    .setColor(color)
                    .setTimestamp(Instant.now());

            if (fieldsContent != null && !fieldsContent.isEmpty()) {
                embed.addField("Details", fieldsContent, false);
            }

            channel.sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            log("Failed to send log message to Discord: " + e.getMessage());
        }
    }
}