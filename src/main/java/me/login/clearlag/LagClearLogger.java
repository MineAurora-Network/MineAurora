package me.login.clearlag;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.requests.GatewayIntent;
import javax.security.auth.login.LoginException;

public class LagClearLogger {

    private final Login plugin;
    private JDA jda;
    private long logChannelId;

    public LagClearLogger(Login plugin) {
        this.plugin = plugin;
        String token = plugin.getConfig().getString("logger-bot-token");
        this.logChannelId = plugin.getConfig().getLong("lagclear-log-channel-id", 0);

        if (token == null || token.isEmpty() || token.equals("YOUR_LOGGER_BOT_TOKEN_HERE")) {
            plugin.getLogger().warning("logger-bot-token is not set in config.yml. LagClear logging bot will not start.");
            return;
        }

        if (this.logChannelId == 0) {
            plugin.getLogger().warning("lagclear-log-channel-id is not set in config.yml. LagClear logging bot will not start.");
            return;
        }

        try {
            startBot(token);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to start LagClear logging bot: " + e.getMessage());
        }
    }

    private void startBot(String token) throws LoginException, InterruptedException {
        this.jda = JDABuilder.createDefault(token)
                .enableIntents(GatewayIntent.GUILD_MESSAGES) // Only need to send messages
                .build()
                .awaitReady();
        plugin.getLogger().info("LagClear Logger Bot started successfully.");
    }

    public void sendLog(String message) {
        if (jda == null) {
            // Bot isn't running, just log to console
            plugin.getLogger().info("[LagClear Log] " + message);
            return;
        }

        MessageChannel channel = jda.getTextChannelById(logChannelId);
        if (channel != null) {
            channel.sendMessage("[LagClear] " + message).queue(
                    null, // success
                    (error) -> plugin.getLogger().warning("Failed to send LagClear log to Discord: " + error.getMessage())
            );
        } else {
            plugin.getLogger().warning("LagClear log channel (" + logChannelId + ") not found.");
        }
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            plugin.getLogger().info("LagClear Logger Bot has been shut down.");
        }
    }
}