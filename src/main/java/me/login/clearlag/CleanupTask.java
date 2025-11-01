package me.login.clearlag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;

public class CleanupTask extends BukkitRunnable {
    private final Plugin plugin;
    private final LagClearConfig lagClearConfig; // <-- ADDED
    private int secondsUntilCleanup = 3 * 60;

    // Titles (re-usable)
    private final com.destroystokyo.paper.profile.PlayerProfile titleProfile;
    private final net.kyori.adventure.title.Title warningTitle;
    private final net.kyori.adventure.title.Title cleanupTitle;

    // --- CONSTRUCTOR UPDATED ---
    public CleanupTask(Plugin plugin, LagClearConfig lagClearConfig) {
        this.plugin = plugin;
        this.lagClearConfig = lagClearConfig; // <-- ADDED

        net.kyori.adventure.text.Component warningMain = net.kyori.adventure.text.Component.text("Cleaner", net.kyori.adventure.text.format.NamedTextColor.RED);
        net.kyori.adventure.text.Component warningSub = net.kyori.adventure.text.Component.text("§fEntities removal in 60 seconds!", net.kyori.adventure.text.format.NamedTextColor.WHITE);

        net.kyori.adventure.text.Component cleanupMain = net.kyori.adventure.text.Component.text("Cleaner", net.kyori.adventure.text.format.NamedTextColor.RED);
        net.kyori.adventure.text.Component cleanupSub = net.kyori.adventure.text.Component.text("Dropped items and entities have been removed.", net.kyori.adventure.text.format.NamedTextColor.WHITE);

        // Title timings: 20 ticks fade-in, 50 ticks stay, 20 ticks fade-out
        net.kyori.adventure.title.Title.Times times = net.kyori.adventure.title.Title.Times.times(
                Duration.ofMillis(1000), // 20 ticks
                Duration.ofMillis(2500), // 50 ticks
                Duration.ofMillis(1000)  // 20 ticks
        );

        this.warningTitle = net.kyori.adventure.title.Title.title(warningMain, warningSub, times);
        this.cleanupTitle = net.kyori.adventure.title.Title.title(cleanupMain, cleanupSub, times);

        // Deprecated Spigot API fallback (less ideal)
        // Note: This part is just for reference; the Adventure API is preferred for 1.21.1
        // We are fully using the Adventure API above.
        this.titleProfile = null; // Not needed for Adventure API
    }

    @Override
    public void run() {
        // Decrease timer
        secondsUntilCleanup--;

        if (secondsUntilCleanup == 60) {
            String message = ChatColor.RED + "Cleaner" + ChatColor.WHITE + ": Entities and dropped items will be cleared in 60 seconds!";
            broadcastToWorlds(message, warningTitle);
        }

        else if (secondsUntilCleanup <= 0) {
            String message = ChatColor.RED + "Cleaner" + ChatColor.WHITE + ": Cleaning entities and dropped items now.....";
            broadcastToWorlds(message, cleanupTitle);

            // Run the actual cleanup (this must run on the main thread, which a BukkitRunnable does)
            int removed = EntityCleanup.performCleanup(plugin);

            String plural = removed == 1 ? "entity" : "entities";
            String doneMessage = ChatColor.RED + "Cleaner§f:" + ChatColor.GREEN + " Total entities removed: " + removed + " " + plural + ".";
            broadcastToWorlds(doneMessage, null);
            secondsUntilCleanup = 3 * 60;
        }
    }

    // --- METHOD UPDATED ---
    private void broadcastToWorlds(String chatMessage, net.kyori.adventure.title.Title title) {
        for (String worldName : EntityCleanup.WORLDS_TO_CLEAN) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    // --- NEW: Check player preference ---
                    if (lagClearConfig.getPlayerToggle(player.getUniqueId())) {
                        if (chatMessage != null && !chatMessage.isEmpty()) {
                            player.sendMessage(chatMessage);
                        }
                        if (title != null) {
                            player.showTitle(title);
                        }
                    }
                    // --- END: Check ---
                }
            }
        }
    }
}