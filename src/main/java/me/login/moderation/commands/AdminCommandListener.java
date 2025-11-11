package me.login.moderation.commands;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public class AdminCommandListener implements Listener {

    private final MaintenanceManager maintenanceManager;
    private final WorldManager worldManager;

    public AdminCommandListener(MaintenanceManager maintenanceManager, WorldManager worldManager) {
        this.maintenanceManager = maintenanceManager;
        this.worldManager = worldManager;
    }

    /**
     * Prevents login during maintenance.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        if (maintenanceManager.isMaintenanceActive()) {
            maintenanceManager.checkLogin(event);
        }
    }

    /**
     * Prevents teleporting to locked worlds.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // Only check if the world is different
        if (event.getFrom().getWorld() != event.getTo().getWorld()) {
            worldManager.checkTeleport(event);
        }
    }
}