package me.login.clearlag;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.type.Dispenser;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Consolidated listener to manage per-chunk limits for various blocks and entities.
 * This class replaces ArmorStandLimit.java and HopperLimit.java.
 * It also handles the dispenser bypass exploit.
 */
public class PlacementLimitListener implements Listener {

    private final Login plugin;
    private final Component serverPrefix;

    // Limits loaded from config.yml
    private final int ARMOR_STAND_LIMIT;
    private final int HOPPER_LIMIT;
    private final int REPEATER_LIMIT;
    private final int DISPENSER_LIMIT;

    // --- MODIFICATION: Constructor now accepts LagClearConfig ---
    public PlacementLimitListener(Login plugin, LagClearConfig lagClearConfig) {
        this.plugin = plugin;

        // Load server prefix using MiniMessage
        // Note: This still comes from the main config.yml, which is fine.
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<#1998FF>Server<gray>] ");
        this.serverPrefix = MiniMessage.miniMessage().deserialize(prefixString + " ");

        // Load limits from main config.yml
        // We pass the main plugin config, not the lagclear.yml
        this.ARMOR_STAND_LIMIT = plugin.getConfig().getInt("clearlag-limits.armor-stand", 3);
        this.HOPPER_LIMIT = plugin.getConfig().getInt("clearlag-limits.hopper", 10);
        this.REPEATER_LIMIT = plugin.getConfig().getInt("clearlag-limits.repeater", 20);
        this.DISPENSER_LIMIT = plugin.getConfig().getInt("clearlag-limits.dispenser", 10);
    }
    // --- END MODIFICATION ---

    /**
     * Counts Tile Entities (Hoppers, Repeaters, Dispensers) in a chunk.
     * This is fast.
     */
    private int countTileEntities(Chunk chunk, Material material) {
        int count = 0;
        try {
            for (BlockState blockState : chunk.getTileEntities()) {
                if (blockState.getType() == material) {
                    count++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error counting tile entities: " + e.getMessage());
        }
        return count;
    }

    /**
     * Counts Entities (Armor Stands) in a chunk.
     * This is also fast.
     */
    private int countEntities(Chunk chunk, EntityType entityType) {
        int count = 0;
        try {
            for (Entity entity : chunk.getEntities()) {
                if (entity.getType() == entityType) {
                    count++;
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Error counting entities: " + e.getMessage());
        }
        return count;
    }

    /**
     * Sends the limit message to the player using the server_prefix.
     */
    private void sendLimitMessage(Player player, String itemName, int limit) {
        Component message = serverPrefix.append(
                MiniMessage.miniMessage().deserialize(
                        "<red>You can't place more than <gold>" + limit + "</gold> <red>" + itemName + "(s) in this chunk.</red>"
                )
        );
        player.sendMessage(message);
    }

    // --- Event Handlers (No changes below this line) ---

    /**
     * Handles placing Tile Entities (Hoppers, Repeaters, Dispensers).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Material type = event.getBlock().getType();
        int limit = -1;
        String name = "";

        switch (type) {
            case HOPPER:
                limit = HOPPER_LIMIT;
                name = "hopper";
                break;
            case REPEATER:
                limit = REPEATER_LIMIT;
                name = "repeater";
                break;
            case DISPENSER:
                limit = DISPENSER_LIMIT;
                name = "dispenser";
                break;
            default:
                return; // Not a block we're limiting
        }

        Chunk chunk = event.getBlock().getChunk();
        if (countTileEntities(chunk, type) >= limit) {
            event.setCancelled(true);
            sendLimitMessage(event.getPlayer(), name, limit);
        }
    }

    /**
     * Handles placing Armor Stands (which is an interaction, not a BlockPlaceEvent).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerPlaceArmorStand(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getItem() == null || event.getItem().getType() != Material.ARMOR_STAND) {
            return;
        }

        // Get the chunk where the armor stand *will* be placed
        Block placedOn = event.getClickedBlock();
        if (placedOn == null) return;

        Block placedAt = placedOn.getRelative(event.getBlockFace());
        Chunk chunk = placedAt.getChunk();

        if (countEntities(chunk, EntityType.ARMOR_STAND) >= ARMOR_STAND_LIMIT) {
            event.setCancelled(true);
            sendLimitMessage(event.getPlayer(), "armor stand", ARMOR_STAND_LIMIT);
        }
    }

    /**
     * Handles the dispenser bypass for all limited items.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        ItemStack item = event.getItem();
        Material itemType = item.getType();

        // Find the block where the item will be placed
        Block dispenserBlock = event.getBlock();
        if (!(dispenserBlock.getState().getBlockData() instanceof Dispenser)) {
            return; // Should not happen, but good to check
        }

        Dispenser dispenserData = (Dispenser) dispenserBlock.getState().getBlockData();
        Block targetBlock = dispenserBlock.getRelative(dispenserData.getFacing());
        Chunk chunk = targetBlock.getChunk();

        // Check against limits based on the item being dispensed
        switch (itemType) {
            case ARMOR_STAND:
                if (countEntities(chunk, EntityType.ARMOR_STAND) >= ARMOR_STAND_LIMIT) {
                    event.setCancelled(true);
                    // We don't know *which* player triggered this, so we can't send a message.
                    // We just silently cancel the event, which prevents the item from being dispensed.
                }
                break;

            case HOPPER:
                if (countTileEntities(chunk, Material.HOPPER) >= HOPPER_LIMIT) {
                    event.setCancelled(true);
                }
                break;

            case REPEATER:
                if (countTileEntities(chunk, Material.REPEATER) >= REPEATER_LIMIT) {
                    event.setCancelled(true);
                }
                break;

            case DISPENSER:
                if (countTileEntities(chunk, Material.DISPENSER) >= DISPENSER_LIMIT) {
                    event.setCancelled(true);
                }
                break;
        }
    }
}