package me.login.clearlag;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;


public class HopperLimit implements Listener {
    private final int HOPPER_LIMIT_PER_CHUNK = 10;

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.HOPPER) {
            return;
        }

        Chunk chunk = event.getBlock().getChunk();
        int hopperCount = 0;

        // Get all tile entities (BlockEntities) in the chunk
        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState instanceof Hopper) {
                hopperCount++;
            }
        }

        // Check if the count (which includes the one about to be placed) exceeds the limit
        // We check >= because the 'hopperCount' is before the new one is added,
        // but the event happens *before* placement, so we count existing ones.
        // If 10 are already there, hopperCount will be 10. The new one is the 11th.
        if (hopperCount >= HOPPER_LIMIT_PER_CHUNK) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(ChatColor.RED + "You can't place more than §6" + HOPPER_LIMIT_PER_CHUNK + " §choppers in this chunk.");
        }
    }
}
