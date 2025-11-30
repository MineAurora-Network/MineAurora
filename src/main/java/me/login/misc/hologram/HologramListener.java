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
    // Removed RTPHologramInteraction field

    public HologramListener(HologramModule module) {
        this.manager = module.getHologramManager();
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

        // Removed logic for checking "rtp" hologram name and delegating to interaction handler
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Removed interaction clearing
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        // Existing logic for chunk unload handling (empty in original to prevent errors)
    }

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