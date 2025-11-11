package me.login.misc.holograms;

import org.bukkit.entity.Interaction;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for player interactions with hologram entities.
 */
public class HologramListener implements Listener {

    private final HologramManager hologramManager;

    public HologramListener(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    /**
     * Fired when a player right-clicks an entity.
     * Used to detect clicks on hologram pagination buttons.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEntityEvent event) {
        // Check if the clicked entity is an Interaction entity
        if (event.getRightClicked() instanceof Interaction) {
            hologramManager.onPlayerInteract(event.getPlayer(), event.getRightClicked().getUniqueId());
            event.setCancelled(true); // Prevent any default interaction
        }
    }

    /**
     * Fired when a player joins the server.
     * Used to initialize the player's view of all holograms to page 1.
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        hologramManager.onPlayerJoin(event.getPlayer());
    }

    /**
     * Fired when a player quits.
     * Used to clean up the player's hologram session data.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        hologramManager.onPlayerQuit(event.getPlayer());
    }
}