package me.login.misc.rank;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class RankLogger {

    private final Login plugin;
    private JDA jda;
    private long logChannelId;

    // --- Fields for Log Stacking ---
    private final Map<Long, String> lastMessage = new HashMap<>();
    private final Map<Long, String> lastMessageId = new HashMap<>();
    private final Map<Long, Integer> messageCount = new HashMap<>();
    private final Map<Long, BukkitTask> flushTasks = new HashMap<>();
    private final long FLUSH_DELAY_TICKS = 400L; // 20 seconds

    public RankLogger(Login plugin) {
        this.plugin = plugin;
        this.logChannelId = plugin.getConfig().getLong("rank-log-channel-id", 0);
    }

    public void init(JDA jda) {
        this.jda = jda;
        if (logChannelId == 0) {
            plugin.getLogger().warning("Rank Log Channel ID ('rank-log-channel-id') not set in config.yml!");
        }
        if (this.jda == null) {
            plugin.getLogger().severe("RankLogger received a null JDA instance! Logging will be disabled.");
        }
    }

    public void logRankSet(String setter, String target, String rank, String duration) {
        String message = String.format("`%s` set rank of `%s` to `%s` for `%s`.", setter, target, rank, duration);
        sendLog(logChannelId, "[Rank Admin] " + message, "Admin");
    }

    public void logRankRemove(String setter, String target, String newRank) {
        String message = String.format("`%s` removed rank from `%s` (set to `%s`).", setter, target, newRank);
        sendLog(logChannelId, "[Rank Admin] " + message, "Admin");
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
        if (channel == null) {
            plugin.getLogger().warning("Rank " + logType + " log channel (" + channelId + ") not found.");
            return;
        }

        String finalMessage = message + (count > 1 ? " `(x" + count + ")`" : "");

        if (messageId != null && count > 1) {
            channel.editMessageById(messageId, finalMessage).queue(
                    null,
                    error -> {
                        channel.sendMessage(finalMessage).queue(
                                (newMessage) -> lastMessageId.put(channelId, newMessage.getId()),
                                (sendError) -> plugin.getLogger().warning("Failed to (re)send Rank " + logType + " log: " + sendError.getMessage())
                        );
                    }
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
                    error -> plugin.getLogger().warning("Failed to send Rank " + logType + " log: " + error.getMessage())
            );
        }
    }
}