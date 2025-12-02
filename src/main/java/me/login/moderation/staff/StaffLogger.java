package me.login.moderation.staff;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;

public class StaffLogger {

    private final Login plugin;
    private final JDA jda;

    // Channel IDs
    private final long chatChannelId;
    private final long maintenanceChannelId;
    private final long vanishChannelId;
    private final long reportChannelId;
    private final long restartChannelId;

    public enum LogType {
        CHAT, MAINTENANCE, VANISH, REPORT, RESTART
    }

    public StaffLogger(Login plugin) {
        this.plugin = plugin;

        // Use shared JDA
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
        }

        this.chatChannelId = plugin.getConfig().getLong("staff-chat-channel-id", 0);
        this.maintenanceChannelId = plugin.getConfig().getLong("maintenance-log-channel-id", 0);
        this.vanishChannelId = plugin.getConfig().getLong("vanish-log-channel-id", 0);
        this.reportChannelId = plugin.getConfig().getLong("report-log-channel-id", 0);
        this.restartChannelId = plugin.getConfig().getLong("restart-log-channel-id", 0);
    }

    public void log(LogType type, String message) {
        String cleanMessage = message.replace("`", "").replace("**", "");
        plugin.getLogger().info("[StaffLog][" + type.name() + "] " + cleanMessage);

        if (jda != null && jda.getStatus() == JDA.Status.CONNECTED) {
            long targetId = 0;
            switch (type) {
                case CHAT: targetId = chatChannelId; break;
                case MAINTENANCE: targetId = maintenanceChannelId; break;
                case VANISH: targetId = vanishChannelId; break;
                case REPORT: targetId = reportChannelId; break;
                case RESTART: targetId = restartChannelId; break;
            }

            if (targetId != 0) {
                MessageChannel channel = jda.getTextChannelById(targetId);
                if (channel != null) {
                    channel.sendMessage(message).queue();
                }
            }
        }
    }
}