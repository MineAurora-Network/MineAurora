package me.login.clearlag;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;

import java.util.EnumSet;

public class LagClearLogger {

    private final Login plugin;
    private JDA jda;
    private final long logChannelId;

    public LagClearLogger(Login plugin) {
        this.plugin = plugin;

        String token = plugin.getConfig().getString("logger-bot-token");
        this.logChannelId = plugin.getConfig().getLong("lagclear-log-channel-id", 0);

        if (token == null || token.isEmpty() || token.equalsIgnoreCase("YOUR_LOGGER_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("logger-bot-token not set in config.yml. LagClear logging bot will not start.");
            return;
        }

        if (logChannelId == 0) {
            plugin.getLogger().warning("lagclear-log-channel-id not set in config.yml. LagClear logging bot will not start.");
            return;
        }

        startLoggerBot(token);
    }

    private void startLoggerBot(String token) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                this.jda = JDABuilder.createLight(token)
                        .enableIntents(GatewayIntent.GUILD_MESSAGES)
                        .disableCache(EnumSet.allOf(CacheFlag.class))
                        .build()
                        .awaitReady();

                // ✅ Set the bot’s status and activity
                jda.getPresence().setPresence(OnlineStatus.ONLINE, Activity.watching("server logs 📜"));

                plugin.getLogger().info("LagClear Logger Bot started successfully with presence set to Online.");
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to start LagClear Logger bot: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public void sendLog(String message) {
        plugin.getLogger().info("[LagClear Log] " + message);

        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED) return;

        MessageChannel channel = jda.getTextChannelById(logChannelId);
        if (channel != null) {
            channel.sendMessage("[LagClear] " + message).queue(
                    null,
                    error -> plugin.getLogger().warning("Failed to send LagClear log: " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("LagClear log channel (" + logChannelId + ") not found.");
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdownNow();
            plugin.getLogger().info("LagClear Logger Bot has been shut down.");
        }
    }
}
