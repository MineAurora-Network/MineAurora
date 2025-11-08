package me.login.lifesteal;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;

public class LifestealLogger {

    private final Login plugin;
    private final JDA jda;
    private final long normalChannelId;
    private final long adminChannelId;
    private final long combatChannelId;

    // --- NEW FIELDS FOR LOG STACKING ---
    private final Map<Long, String> lastMessage = new HashMap<>();
    private final Map<Long, String> lastMessageId = new HashMap<>();
    private final Map<Long, Integer> messageCount = new HashMap<>();
    private final Map<Long, BukkitTask> flushTasks = new HashMap<>();
    private final long FLUSH_DELAY_TICKS = 400L; // 20 seconds (20 * 20 ticks)
    // --- END NEW FIELDS ---

    public LifestealLogger(Login plugin, JDA jda) {
        this.plugin = plugin;
        this.jda = jda;

        // Load channel IDs from config.yml
        this.normalChannelId = plugin.getConfig().getLong("lifesteal-normal-channel-id", 0);
        this.adminChannelId = plugin.getConfig().getLong("lifesteal-admin-channel-id", 0);
        this.combatChannelId = plugin.getConfig().getLong("combat-log-channel-id", 0);

        // Log initialization status
        if (normalChannelId == 0) plugin.getLogger().warning("Lifesteal Normal Log Channel ID ('lifesteal-normal-channel-id') not set!");
        if (adminChannelId == 0) plugin.getLogger().warning("Lifesteal Admin Log Channel ID ('lifesteal-admin-channel-id') not set!");
        if (combatChannelId == 0) plugin.getLogger().warning("Lifesteal Combat Log Channel ID ('combat-log-channel-id') not set!");

        if (jda == null) {
            plugin.getLogger().severe("LifestealLogger received a null JDA instance! Logging will be disabled.");
        }
    }

    /**
     * Sends a log message to the normal lifesteal channel.
     * Used for basic events like deaths, heart withdrawals, etc.
     */
    public void logNormal(String message) {
        sendLog(normalChannelId, "[Lifesteal] " + message, "Normal");
    }

    /**
     * Sends a log message to the admin lifesteal channel.
     * Used for admin commands like /ls sethearts, /ls give, etc.
     */
    public void logAdmin(String message) {
        sendLog(adminChannelId, "[Lifesteal Admin] " + message, "Admin");
    }

    /**
     * Sends a log message to the combat log channel.
     * Used for players logging out in combat.
     */
    public void logCombat(String message) {
        sendLog(combatChannelId, "[Combat Log] " + message, "Combat");
    }

    // --- MODIFIED: sendLog with stacking logic ---
    private void sendLog(long channelId, String message, String logType) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return;
        }

        String last = lastMessage.get(channelId);

        // If message is the same as the last one, stack it
        if (message.equals(last)) {
            messageCount.put(channelId, messageCount.getOrDefault(channelId, 1) + 1);

            // Cancel and reschedule the flush task
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
            // If message is different, flush the previous stack immediately
            flushLog(channelId, logType);

            // Start a new stack
            lastMessage.put(channelId, message);
            messageCount.put(channelId, 1);
            lastMessageId.remove(channelId); // Clear the last message ID

            // Schedule a flush task for this new message
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

    /**
     * Flushes the stacked log messages to Discord.
     * This will either send a new message or edit the last one.
     */
    private void flushLog(long channelId, String logType) {
        // Get and clear the data for this channel
        String message = lastMessage.remove(channelId);
        if (message == null) return; // Nothing to flush

        int count = messageCount.remove(channelId);
        String messageId = lastMessageId.remove(channelId);
        BukkitTask task = flushTasks.remove(channelId);
        if (task != null) task.cancel();

        MessageChannel channel = jda.getTextChannelById(channelId);
        if (channel == null) {
            plugin.getLogger().warning("Lifesteal " + logType + " log channel (" + channelId + ") not found.");
            return;
        }

        String finalMessage = message + (count > 1 ? " `(x" + count + ")`" : "");

        // If we have a message ID and the count is > 1, edit the message
        if (messageId != null && count > 1) {
            channel.editMessageById(messageId, finalMessage).queue(
                    null, // On success, do nothing
                    error -> {
                        // If editing fails (e.g., message deleted), send a new one
                        channel.sendMessage(finalMessage).queue(
                                (newMessage) -> lastMessageId.put(channelId, newMessage.getId()),
                                (sendError) -> plugin.getLogger().warning("Failed to (re)send Lifesteal " + logType + " log: " + sendError.getMessage())
                        );
                    }
            );
        } else {
            // Otherwise, send a new message
            channel.sendMessage(finalMessage).queue(
                    (newMessage) -> {
                        // Store the ID of this new message in case we need to edit it
                        if (count == 1) { // Only store if it's the start of a potential stack
                            lastMessage.put(channelId, message); // Re-add message to check against next time
                            messageCount.put(channelId, 1);
                            lastMessageId.put(channelId, newMessage.getId());
                        }
                    },
                    error -> plugin.getLogger().warning("Failed to send Lifesteal " + logType + " log: " + error.getMessage())
            );
        }
    }
}