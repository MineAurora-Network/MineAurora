package me.login.premiumfeatures.credits;

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

        // Safely get JDA from LagClearLogger
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
            plugin.getLogger().warning("CreditsLogger: LagClearLogger is not initialized or JDA is null.");
        }
    }

    public void logTransaction(String adminName, String targetName, String action, int amount, int newBalance) {
        if (channelId == null || channelId.isEmpty()) return;
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                MessageChannel channel = jda.getTextChannelById(channelId);
                if (channel == null) {
                    return;
                }

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("Credits Transaction");
                embed.setColor(action.equalsIgnoreCase("add") ? Color.GREEN : Color.RED);
                if (action.equalsIgnoreCase("set")) embed.setColor(Color.YELLOW);

                embed.addField("Admin", adminName, true);
                embed.addField("Target", targetName, true);
                embed.addField("Action", action.toUpperCase(), true);
                embed.addField("Amount", String.valueOf(amount), true);
                embed.addField("New Balance", String.valueOf(newBalance), true);

                embed.setTimestamp(Instant.now());
                embed.setFooter("MineAurora Network Credit System");

                channel.sendMessageEmbeds(embed.build()).queue();

            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}