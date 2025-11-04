package me.login.clearlag;

import me.login.Login; // <-- ADDED
// import org.bukkit.ChatColor; // <-- REMOVED
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.Hopper;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class HopperLimit implements Listener {
    private final int HOPPER_LIMIT_PER_CHUNK = 10;
    private final Login plugin; // <-- ADDED

    // --- ADDED CONSTRUCTOR ---
    public HopperLimit(Login plugin) {
        this.plugin = plugin;
    }
    // --- END ---

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (event.getBlock().getType() != Material.HOPPER) {
            return;
        }

        Chunk chunk = event.getBlock().getChunk();
        int hopperCount = 0;

        for (BlockState blockState : chunk.getTileEntities()) {
            if (blockState instanceof Hopper) {
                hopperCount++;
            }
        }

        if (hopperCount >= HOPPER_LIMIT_PER_CHUNK) {
            event.setCancelled(true);
            // --- CHANGED ---
            event.getPlayer().sendMessage(plugin.getLagClearConfig().formatMessage(
                    "<red>You can't place more than <gold>" + HOPPER_LIMIT_PER_CHUNK + "</gold> <red>hoppers in this chunk.</red>"
            ));
            // --- END ---
        }
    }
}