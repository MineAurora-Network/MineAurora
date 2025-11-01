package me.login.leaderboards;

import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class LeaderboardProtectionListener implements Listener {

    private final LeaderboardDisplayManager manager;

    public LeaderboardProtectionListener(LeaderboardDisplayManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        // Check if it's a TextDisplay and if it's managed by our system
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            // Prevent any damage
            event.setCancelled(true);
        }
    }

    // Optional: Prevent players from interacting (e.g., trying to shear/dye - though unlikely for TextDisplay)
    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            // Prevent interaction if it's a managed leaderboard
            // You might want this off if you need interaction for some reason
            // event.setCancelled(true);
        }
    }

    // Optional: Prevent breaking like paintings (unlikely for TextDisplay, but safe)
    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // Note: This does NOT protect against /kill command or plugin-based entity removal.
    // Full protection requires more complex tracking/respawning.
}