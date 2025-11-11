package me.login.moderation.commands;

import me.login.Login;
import me.login.moderation.commands.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

public class RestartManager {

    private final Login plugin;
    private BukkitTask scheduledRestartTask = null;

    public RestartManager(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Helper to get the plugin's prefix and serializer
     */
    private Component getPrefixedComponent(String message) {
        return plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message);
    }

    /**
     * Helper to broadcast a prefixed message
     */
    private void broadcastPrefixedMessage(String message) {
        Bukkit.broadcast(getPrefixedComponent(message));
    }


    /**
     * Schedules a server restart after a set duration.
     * @param ticks The number of ticks to wait.
     * @param formattedTime A string like "1h 30m" for broadcast messages.
     */
    public void scheduleRestart(long ticks, String formattedTime) {
        cancelScheduledRestart(); // Cancel any existing task

        // Broadcast warnings
        broadcastPrefixedMessage("<red>[WARNING] <gold>The server will restart in <yellow>" + formattedTime + "<gold>.");

        // Schedule final warnings and restart
        long seconds = ticks / 20;
        if (seconds > 300) {
            // 5 minute warning
            scheduleWarning(TimeUtil.formatTime(seconds - 300), (ticks - 6000L));
        }
        if (seconds > 60) {
            // 1 minute warning
            scheduleWarning(TimeUtil.formatTime(seconds - 60), (ticks - 1200L));
        }
        if (seconds > 30) {
            // 30 second warning
            scheduleWarning(TimeUtil.formatTime(seconds - 30), (ticks - 600L));
        }
        if (seconds > 10) {
            // 10 second warning
            scheduleWarning(TimeUtil.formatTime(seconds - 10), (ticks - 200L));
        }
        if (seconds > 0) {
            // 1 second warning
            scheduleWarning(TimeUtil.formatTime(1), (ticks - 20L));
        }

        // Schedule the restart command
        this.scheduledRestartTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastPrefixedMessage("<red>[WARNING] <gold>Server restarting now!");
            // This command is handled by your server start script
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "restart");
            scheduledRestartTask = null;
        }, ticks);
    }

    private void scheduleWarning(String timeString, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastPrefixedMessage("<red>[WARNING] <gold>Server restart in <yellow>" + timeString + "<gold>!");
        }, ticks);
    }

    /**
     * Cancels any pending restart task.
     */
    public void cancelScheduledRestart() {
        if (scheduledRestartTask != null && !scheduledRestartTask.isCancelled()) {
            scheduledRestartTask.cancel();
            broadcastPrefixedMessage("<green>The scheduled server restart has been <bold>CANCELLED</bold>.");
        }
        scheduledRestartTask = null;
    }
}