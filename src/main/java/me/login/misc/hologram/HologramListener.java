package me.login.misc.hologram;

import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent; // <-- ADDED IMPORT
import org.bukkit.event.world.ChunkLoadEvent;

public class HologramListener implements Listener {

    private final HologramManager manager;
    private final RTPHologramInteraction rtpInteraction;

    public HologramListener(HologramModule module, RTPHologramInteraction rtpInteraction) {
        this.manager = module.getHologramManager();
        this.rtpInteraction = rtpInteraction;
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof Interaction)) {
            return;
        }

        Hologram hologram = manager.getHologramByEntity(clicked.getUniqueId());
        if (hologram == null) {
            return;
        }

        // We found a hologram interaction!
        event.setCancelled(true);
        Player player = event.getPlayer();

        // Handle specific hologram logic
        if (hologram.getName().equals("rtp")) {
            rtpInteraction.handleClick(player, hologram, clicked.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (entity.getType() != EntityType.TEXT_DISPLAY && entity.getType() != EntityType.INTERACTION) {
            return;
        }

        Hologram hologram = manager.getHologramByEntity(entity.getUniqueId());
        if (hologram != null) {
            event.setCancelled(true); // Protect our entities

            // If it was "killed" (e.g., /kill command), respawn it
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID || event.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
                if (entity.isDead()) {
                    manager.respawnHologram(hologram);
                }
            }
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // This is a simple check. For servers with many holograms, this could be optimized.
        // We just check if any entities are dead and respawn them.
        for (Entity entity : event.getChunk().getEntities()) {
            if (entity.getType() != EntityType.TEXT_DISPLAY && entity.getType() != EntityType.INTERACTION) {
                continue;
            }

            if (entity.isDead()) {
                Hologram hologram = manager.getHologramByEntity(entity.getUniqueId());
                if (hologram != null) {
                    manager.respawnHologram(hologram);
                }
            }
        }
    }

    /**
     * Clears player page data on quit to prevent memory leaks.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        rtpInteraction.clearPlayerPage(event.getPlayer());
    }
}