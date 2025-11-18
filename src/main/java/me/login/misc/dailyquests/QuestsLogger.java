package me.login.misc.dailyquests;

import me.login.clearlag.LagClearLogger; // <-- IMPORT FIXED
import me.login.clearlag.LagClearModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.time.Instant;

public class QuestsLogger {

    private final LagClearLogger lagClearLogger; // <-- TYPE CHANGED
    private final String channelId;

    public QuestsLogger(LagClearLogger lagClearLogger, String channelId) { // <-- CONSTRUCTOR CHANGED
        this.lagClearLogger = lagClearLogger;
        this.channelId = channelId;
    }

    public void logQuestCompletion(Player player, Quest quest, double cash, int tokens) {
        if (lagClearLogger == null || lagClearLogger.getJDA() == null) return; // <-- FIXED
        JDA jda = lagClearLogger.getJDA(); // <-- FIXED
        if (jda == null) {
            return; // JDA not ready
        }

        try {
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                return; // Channel not found
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setTitle("Quest Completed!")
                    .setColor(Color.GREEN)
                    .setDescription("Player **" + player.getName() + "** completed a quest.")
                    .addField("Player UUID", player.getUniqueId().toString(), false)
                    .addField("Quest Type", quest.getType().name(), true)
                    .addField("Objective", quest.getObjectiveDescription(), true)
                    .addField("Rewards",
                            "Cash: $" + String.format("%,.2f", cash) + "\n" +
                                    "Tokens: " + tokens,
                            false)
                    .setTimestamp(Instant.now())
                    .setFooter("MineAurora Quests");

            channel.sendMessageEmbeds(embed.build()).queue();

        } catch (Exception e) {
            // Don't spam console, just in case
            System.err.println("Failed to send Quest log to Discord: " + e.getMessage());
        }
    }
}