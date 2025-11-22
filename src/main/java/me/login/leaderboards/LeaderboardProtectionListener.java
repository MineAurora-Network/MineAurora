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
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            // event.setCancelled(true); // Uncomment if you want to block interaction entirely
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof TextDisplay && manager.isManagedLeaderboard(entity.getUniqueId())) {
            event.setCancelled(true);
        }
    }
}