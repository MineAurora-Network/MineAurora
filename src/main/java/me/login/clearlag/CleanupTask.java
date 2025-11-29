package me.login.clearlag;

import me.login.Login; // <-- ADDED
import net.kyori.adventure.text.Component; // <-- ADDED
import net.kyori.adventure.text.minimessage.MiniMessage; // <-- ADDED
import net.kyori.adventure.text.format.NamedTextColor; // <-- ADDED
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // <-- REMOVED
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class CleanupTask extends BukkitRunnable {
    private final Plugin plugin;
    private final LagClearConfig lagClearConfig;
    private int secondsUntilCleanup = 5 * 60;

    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final net.kyori.adventure.title.Title warningTitle;
    private final net.kyori.adventure.title.Title cleanupTitle;
    private final Component warningMessage;
    private final Component cleanupMessage;
    private final Component doneMessagePrefix;

    public CleanupTask(Plugin plugin, LagClearConfig lagClearConfig) {
        this.plugin = plugin;
        this.lagClearConfig = lagClearConfig;

        // --- Use LagClearConfig for prefixes ---
        this.warningMessage = lagClearConfig.formatMessage("<white>Entities and dropped items will be cleared in 60 seconds!");
        this.cleanupMessage = lagClearConfig.formatMessage("<white>Cleaning entities and dropped items now.....");
        this.doneMessagePrefix = lagClearConfig.formatMessage("<green>Total entities removed: ");
        // --- END ---

        // Titles (re-usable, Adventure API)
        net.kyori.adventure.text.Component warningMain = miniMessage.deserialize("<red>Cleaner</red>");
        net.kyori.adventure.text.Component warningSub = miniMessage.deserialize("<white>Entities removal in 60 seconds!</white>"); // Legacy §f is fine here

        net.kyori.adventure.text.Component cleanupMain = miniMessage.deserialize("<red>Cleaner</red>");
        net.kyori.adventure.text.Component cleanupSub = miniMessage.deserialize("<white>Dropped items and entities have been removed.</white>");

        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                Duration.ofMillis(1000), // 20 ticks
                Duration.ofMillis(2500), // 50 ticks
                Duration.ofMillis(1000)  // 20 ticks
        );

        this.warningTitle = net.kyori.adventure.title.Title.title(warningMain, warningSub, times);
        this.cleanupTitle = net.kyori.adventure.title.Title.title(cleanupMain, cleanupSub, times);
    }

    @Override
    public void run() {
        secondsUntilCleanup--;

        if (secondsUntilCleanup == 60) {
            broadcastToWorlds(warningMessage, warningTitle); // <-- CHANGED
        }

        else if (secondsUntilCleanup <= 0) {
            broadcastToWorlds(cleanupMessage, cleanupTitle); // <-- CHANGED

            // Cast plugin to Login to pass to static method
            Login login = (Login) plugin;
            int removed = EntityCleanup.performCleanup(login); // <-- CHANGED

            String plural = removed == 1 ? " entity" : " entities";
            // Build the final "done" message component
            Component doneMessage = doneMessagePrefix.append(Component.text(removed + plural, NamedTextColor.GREEN));

            broadcastToWorlds(doneMessage, null); // <-- CHANGED
            secondsUntilCleanup = 3 * 60;
        }
    }

    // --- METHOD UPDATED to use Component ---
    private void broadcastToWorlds(Component chatMessage, net.kyori.adventure.title.Title title) {
        for (String worldName : EntityCleanup.WORLDS_TO_CLEAN) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    if (lagClearConfig.getPlayerToggle(player.getUniqueId())) {
                        if (chatMessage != null) {
                            player.sendMessage(chatMessage); // <-- CHANGED
                        }
                        if (title != null) {
                            player.showTitle(title);
                        }
                    }
                }
            }
        }
    }
}