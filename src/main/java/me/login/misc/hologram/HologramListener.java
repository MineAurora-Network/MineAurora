package me.login.misc.hologram;

import io.papermc.paper.event.player.PlayerArmSwingEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.UUID;

public class HologramListener implements Listener {

    private final HologramManager manager;
    private final RTPHologramInteraction rtpInteraction;

    public HologramListener(HologramModule module, RTPHologramInteraction rtpInteraction) {
        this.manager = module.getHologramManager();
        this.rtpInteraction = rtpInteraction;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
        UUID entityUUID = clickedEntity.getUniqueId();

        Hologram hologram = manager.getHologramByEntity(entityUUID);
        if (hologram == null) return;

        // Prevent default interaction (e.g., armor stand GUI)
        event.setCancelled(true);

        // Check which hologram was clicked and delegate
        if (hologram.getName().equalsIgnoreCase("rtp")) {
            rtpInteraction.handleClick(player, hologram, entityUUID);
        }
        // ... add other hologram interactions here if needed
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Clear player page cache
        rtpInteraction.clearPlayerPage(event.getPlayer());
    }

    /**
     * This handler is modified because ChunkUnloadEvent is not cancellable on Spigot
     * and hologram entities are non-persistent by design, so they are *supposed*
     * to be removed when the chunk unloads. They are respawned in onChunkLoad.
     */
    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // The error "cannot find symbol: method setCancelled(boolean)" is because
        // this event is not cancellable on Spigot.
        // We remove the problematic code. The entities will unload with the chunk,
        // which is correct.
        /*
        for (Entity entity : event.getChunk().getEntities()) {
            Hologram hologram = manager.getHologramByEntity(entity.getUniqueId());
            if (hologram != null) {
                // This was the error:
                // event.setCancelled(true);
                return;
            }
        }
        */
    }

    /**
     * Respawn holograms when their chunk loads if they are missing.
     */
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        // Iterate over active holograms
        for (Hologram hologram : manager.getActiveHolograms()) {
            Location holoLoc = hologram.getBaseLocation();
            if (holoLoc.getWorld() == null || !holoLoc.getWorld().equals(event.getWorld())) {
                continue; // Not the right world
            }

            // Check if the hologram's base location is in the chunk that just loaded
            if (holoLoc.getBlockX() >> 4 == event.getChunk().getX() &&
                    holoLoc.getBlockZ() >> 4 == event.getChunk().getZ()) {

                // Hologram is in this chunk. Check if its entities are loaded.
                // We'll check the first entity.
                UUID firstEntityUUID = hologram.getAllEntityUUIDs().stream().findFirst().orElse(null);
                if (firstEntityUUID != null) {
                    Entity entity = Bukkit.getEntity(firstEntityUUID);
                    // If the entity is null or invalid, it needs respawning.
                    if (entity == null || !entity.isValid()) {
                        // Respawn the entire hologram
                        // Need to run this on the next tick to ensure chunk is fully loaded
                        Bukkit.getScheduler().runTask(manager.getModule().getPlugin(), () -> {
                            manager.respawnHologram(hologram);
                        });
                    }
                }
            }
        }
    }
}