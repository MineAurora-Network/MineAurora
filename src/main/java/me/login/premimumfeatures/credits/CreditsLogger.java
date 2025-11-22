package me.login.premimumfeatures.credits;

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.Bukkit;

import java.awt.*;
import java.time.Instant;

public class CreditsLogger {

    private final Login plugin;
    private final JDA jda;
    private final String channelId;

    public CreditsLogger(Login plugin) {
        this.plugin = plugin;
        this.channelId = plugin.getConfig().getString("credit-log-channel-id", "");

        // Use the shared JDA instance from LagClearLogger
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
            plugin.getLogger().warning("CreditsLogger: LagClearLogger is not initialized. Discord logging will be disabled.");
        }
    }

    public void logTransaction(String adminName, String targetName, String action, double amount, double newBalance) {
        if (channelId == null || channelId.isEmpty()) return;
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MessageChannel channel = jda.getTextChannelById(channelId);
                if (channel == null) {
                    plugin.getLogger().warning("Credits Log Channel ID is invalid or bot cannot see it: " + channelId);
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Credits Transaction");
                embed.setColor(action.equalsIgnoreCase("add") ? Color.GREEN : Color.RED);

                embed.addField("Admin", adminName, true);
                embed.addField("Target", targetName, true);
                embed.addField("Action", action.toUpperCase(), true);
                embed.addField("Amount", String.format("%.2f", amount), true);
                embed.addField("New Balance", String.format("%.2f", newBalance), true);

                embed.setTimestamp(Instant.now());
                embed.setFooter("MineAurora Network Credit System");

                channel.sendMessageEmbeds(embed.build()).queue(
                        null,
                        error -> plugin.getLogger().warning("Failed to send Credits log: " + error.getMessage())
                );

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}