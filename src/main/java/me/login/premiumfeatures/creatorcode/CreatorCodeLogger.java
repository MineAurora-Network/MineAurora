package me.login.premiumfeatures.creatorcode;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class CreatorCodeLogger {

    private final Login plugin;
    private JDA jda;
    private long logChannelId;

    // --- Fields for Log Stacking ---
    private final Map<Long, String> lastMessage = new HashMap<>();
    private final Map<Long, String> lastMessageId = new HashMap<>();
    private final Map<Long, Integer> messageCount = new HashMap<>();
    private final Map<Long, BukkitTask> flushTasks = new HashMap<>();
    private final long FLUSH_DELAY_TICKS = 400L; // 20 seconds

    public CreatorCodeLogger(Login plugin) {
        this.plugin = plugin;
        this.logChannelId = plugin.getConfig().getLong("creator-code-channel-id", 0);
    }

    public void init(JDA jda) {
        this.jda = jda;
        if (logChannelId == 0) {
            plugin.getLogger().warning("Creator Code Log Channel ID ('creator-code-channel-id') not set in config.yml!");
        }
    }

    public void logUsage(String message) {
        sendLog(logChannelId, "[Creator Code] " + message, "Usage");
    }

    public void logAdmin(String message) {
        sendLog(logChannelId, "[Creator Admin] " + message, "Admin");
    }

    private void sendLog(long channelId, String message, String logType) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return;
        }

        String last = lastMessage.get(channelId);

        if (message.equals(last)) {
            messageCount.put(channelId, messageCount.getOrDefault(channelId, 1) + 1);
            BukkitTask existingTask = flushTasks.remove(channelId);
            if (existingTask != null) existingTask.cancel();

            BukkitTask newTask = new BukkitRunnable() {
                @Override
                public void run() {
                    flushLog(channelId, logType);
                }
            }.runTaskLater(plugin, FLUSH_DELAY_TICKS);
            flushTasks.put(channelId, newTask);

        } else {
            flushLog(channelId, logType);

            lastMessage.put(channelId, message);
            messageCount.put(channelId, 1);
            lastMessageId.remove(channelId);

            BukkitTask existingTask = flushTasks.remove(channelId);
            if (existingTask != null) existingTask.cancel();

            BukkitTask newTask = new BukkitRunnable() {
                @Override
                public void run() {
                    flushLog(channelId, logType);
                }
            }.runTaskLater(plugin, FLUSH_DELAY_TICKS);
            flushTasks.put(channelId, newTask);
        }
    }

    private void flushLog(long channelId, String logType) {
        String message = lastMessage.remove(channelId);
        if (message == null) return;

        int count = messageCount.remove(channelId);
        String messageId = lastMessageId.remove(channelId);
        BukkitTask task = flushTasks.remove(channelId);
        if (task != null) task.cancel();

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) return;

        String finalMessage = message + (count > 1 ? " `(x" + count + ")`" : "");

        if (messageId != null && count > 1) {
            channel.editMessageById(messageId, finalMessage).queue(
                    null,
                    error -> channel.sendMessage(finalMessage).queue(
                            (newMessage) -> lastMessageId.put(channelId, newMessage.getId()),
                            (sendError) -> plugin.getLogger().warning("Failed to log: " + sendError.getMessage())
                    )
            );
        } else {
            channel.sendMessage(finalMessage).queue(
                    (newMessage) -> {
                        if (count == 1) {
                            lastMessage.put(channelId, message);
                            messageCount.put(channelId, 1);
                            lastMessageId.put(channelId, newMessage.getId());
                        }
                    },
                    error -> plugin.getLogger().warning("Failed to log: " + error.getMessage())
            );
        }
    }
}