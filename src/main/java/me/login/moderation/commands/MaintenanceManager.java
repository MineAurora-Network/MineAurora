package me.login.moderation.commands;

import me.login.Login;
import me.login.moderation.commands.util.TimeUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.scheduler.BukkitTask;

public class MaintenanceManager {

    private final Login plugin;
    private volatile boolean maintenanceActive = false;
    private BukkitTask scheduledMaintenanceTask = null;

    // Use Components now
    private final Component maintenanceKickComponent;
    private final Component maintenanceLoginComponent;

    public MaintenanceManager(Login plugin) {
        this.plugin = plugin;
        // Pre-serialize the components
        this.maintenanceKickComponent = getPrefixedComponent("<red>Server is now under maintenance.\nPlease check back later!");
        this.maintenanceLoginComponent = getPrefixedComponent("<red>The server is currently under maintenance. Only staff may join.");
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
     * Enables or disables maintenance mode.
     * @param active True to enable, false to disable.
     */
    public void setMaintenanceMode(boolean active) {
        this.maintenanceActive = active;

        if (active) {
            broadcastPrefixedMessage("<gold>Server maintenance has been <red>ENABLED</red>.");
            broadcastPrefixedMessage("<gray>Non-staff players will be kicked.");

            // Kick non-bypass players
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.hasPermission("login.admin.maintenance.bypass")) {
                    player.kick(maintenanceKickComponent); // Use component kick
                }
            }
        } else {
            broadcastPrefixedMessage("<gold>Server maintenance has been <green>DISABLED</green>.");
            if (scheduledMaintenanceTask != null) {
                cancelScheduledMaintenance();
            }
        }
    }

    /**
     * Schedules maintenance to begin after a set duration.
     * @param ticks The number of ticks to wait.
     * @param formattedTime A string like "1h 30m" for broadcast messages.
     */
    public void scheduleMaintenance(long ticks, String formattedTime) {
        cancelScheduledMaintenance(); // Cancel any existing task

        // Broadcast warnings
        broadcastPrefixedMessage("<red>[WARNING] <gold>Server maintenance will begin in <yellow>" + formattedTime + "<gold>.");

        // Schedule final warnings and activation
        long seconds = ticks / 20;
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

        // Schedule the maintenance activation
        this.scheduledMaintenanceTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            setMaintenanceMode(true);
            scheduledMaintenanceTask = null;
        }, ticks);
    }

    private void scheduleWarning(String timeString, long ticks) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            broadcastPrefixedMessage("<red>[WARNING] <gold>Server maintenance in <yellow>" + timeString + "<gold>!");
        }, ticks);
    }

    /**
     * Cancels any pending maintenance task.
     */
    public void cancelScheduledMaintenance() {
        if (scheduledMaintenanceTask != null && !scheduledMaintenanceTask.isCancelled()) {
            scheduledMaintenanceTask.cancel();
            broadcastPrefixedMessage("<green>Scheduled server maintenance has been <bold>CANCELLED</bold>.");
        }
        scheduledMaintenanceTask = null;
    }

    /**
     * Checks if a player is allowed to log in.
     * @param event The PlayerLoginEvent.
     */
    public void checkLogin(PlayerLoginEvent event) {
        if (isMaintenanceActive()) {
            if (!event.getPlayer().hasPermission("login.admin.maintenance.bypass")) {
                event.disallow(PlayerLoginEvent.Result.KICK_OTHER, maintenanceLoginComponent); // Use component disallow
            }
        }
    }

    public boolean isMaintenanceActive() {
        return maintenanceActive;
    }

    /**
     * Getter for the plugin instance, used by MaintenanceCommand.
     * @return The Login plugin instance.
     */
    public Login getPlugin() {
        return plugin;
    }
}