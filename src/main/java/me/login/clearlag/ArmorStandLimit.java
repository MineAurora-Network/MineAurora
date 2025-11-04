package me.login.clearlag;

import me.login.Login; // <-- ADDED
// import org.bukkit.ChatColor; // <-- REMOVED
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ArmorStandLimit implements Listener {
    private final int ARMOR_STAND_LIMIT_PER_CHUNK = 3;
    private final Login plugin; // <-- ADDED

    // --- ADDED CONSTRUCTOR ---
    public ArmorStandLimit(Login plugin) {
        this.plugin = plugin;
    }
    // --- END ---

    @EventHandler
    public void onPlayerPlaceArmorStand(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        if (event.getItem() == null || event.getItem().getType() != Material.ARMOR_STAND) {
            return;
        }

        Chunk chunk = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().getChunk();
        int armorStandCount = 0;

        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                armorStandCount++;
            }
        }

        if (armorStandCount >= ARMOR_STAND_LIMIT_PER_CHUNK) {
            event.setCancelled(true);
            // --- CHANGED ---
            event.getPlayer().sendMessage(plugin.getLagClearConfig().formatMessage(
                    "<red>You can't place more than <gold>" + ARMOR_STAND_LIMIT_PER_CHUNK + "</gold> <red>armour stands in this chunk.</red>"
            ));
            // --- END ---
        }
    }
}