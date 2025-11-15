package me.login.pets;

import me.login.Login;
import me.login.clearlag.LagClearLogger;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import java.awt.Color;
import java.time.Instant;

/**
 * Handles logging for the Pets module, including console and Discord.
 * Uses the shared JDA instance from LagClearLogger.
 */
public class PetsLogger {

    private final Login plugin;
    private String discordChannelId;
    private String discordAdminChannelId;
    // petsConfig removed - no longer needed for petDebug

    public PetsLogger(Login plugin) {
        this.plugin = plugin;
        loadConfig();
    }

    public void loadConfig() {
        this.discordChannelId = plugin.getConfig().getString("discord.pets_log_channel_id", "");
        this.discordAdminChannelId = plugin.getConfig().getString("discord.pets_admin_log_channel_id", "");
    }

    /**
     * Logs a general message to console.
     * @param message The message to log.
     */
    public void log(String message) {
        plugin.getLogger().info("[Pets] " + message);
    }

    // petDebug method is completely removed. Use PetDebug.java instead.

    /**
     * Logs a successful capture to console and Discord.
     * @param playerName The name of the player.
     * @param petType The type of pet captured.
     * @param itemName The item used for capture.
     */
    public void logCapture(String playerName, String petType, String itemName) {
        String logMessage = playerName + " captured a " + petType + " using " + itemName + ".";
        log(logMessage); // Log to console

        // Send to Discord (main log)
        sendEmbedToDiscord(
                discordChannelId,
                "Pet Captured!",
                logMessage,
                Color.GREEN,
                "Player: " + playerName + "\nPet: " + petType + "\nItem: " + itemName
        );
    }

    /**
     * Logs an admin command action to console and Discord.
     * @param adminName The admin who ran the command.
     * @param message The action they performed.
     */
    public void logAdmin(String adminName, String message) {
        String logMessage = "[ADMIN] " + adminName + " " + message;
        log(logMessage); // Log to console

        // Send to Discord (admin log)
        sendEmbedToDiscord(
                discordAdminChannelId,
                "Pet Admin Action",
                message,
                Color.ORANGE,
                "Admin: " + adminName
        );
    }

    /**
     * Logs a pet rename to console and Discord (admin channel).
     * @param playerName The player who renamed the pet.
     * @param petType The type of pet.
     * @param oldName The old name.
     * @param newName The new name.
     */
    public void logRename(String playerName, String petType, String oldName, String newName) {
        String logMessage = playerName + " renamed their " + petType + " from '" + oldName + "' to '" + newName + "'.";
        log(logMessage); // Log to console

        // Send to admin log
        sendEmbedToDiscord(
                discordAdminChannelId,
                "Pet Renamed",
                logMessage,
                Color.CYAN,
                "Player: " + playerName // <--- THIS LINE IS NOW FIXED
        );
    }

    /**
     * Logs an error to console and Discord.
     * @param errorMessage The error message.
     */
    public void logError(String errorMessage) {
        plugin.getLogger().severe("[Pets] " + errorMessage); // Log to console

        // Send to Discord (main log, as it's an error)
        sendEmbedToDiscord(
                discordChannelId,
                "Pet System Error",
                errorMessage,
                Color.RED,
                null
        );
    }

    /**
     * Private helper to send embedded messages to Discord.
     */
    private void sendEmbedToDiscord(String channelId, String title, String description, Color color, String fieldsContent) {
        if (channelId == null || channelId.isEmpty() || channelId.equals("YOUR_PET_LOG_CHANNEL_ID_HERE")) {
            return; // Discord logging disabled or not configured
        }

        try {
            LagClearLogger lagClearLogger = plugin.getLagClearLogger();
            if (lagClearLogger == null) {
                log("LagClearLogger instance is null, cannot log to Discord.");
                return;
            }
            JDA jda = lagClearLogger.getJDA();
            if (jda == null) {
                log("JDA instance is null, cannot log to Discord.");
                return;
            }

            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log("Pets log channel with ID " + channelId + " not found.");
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