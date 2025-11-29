package me.login.level.listener;

import me.login.level.LevelManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class PlayerActivityListener implements Listener {

    private final LevelManager manager;

    public PlayerActivityListener(LevelManager manager) {
        this.manager = manager;
    }

    // Food: 2 XP
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType().isEdible()) {
            manager.addXp(event.getPlayer(), 2, "Eat");
        }
    }

    // Fish: 2 XP
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            manager.addXp(event.getPlayer(), 2, "Fishing");
        }
    }

    // Breeding: 1 XP
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            manager.addXp(player, 1, "Breeding");
        }
    }

    // Brewing: 1 XP (When taking potion from stand)
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionTake(InventoryClickEvent event) {
        if (event.getInventory().getType() == InventoryType.BREWING) {
            if (event.getSlotType() == InventoryType.SlotType.OUTSIDE) return;

            // Slots 0, 1, 2 are output slots
            if (event.getSlot() <= 2) {
                ItemStack item = event.getCurrentItem();
                if (item != null && item.getType() != Material.AIR) {
                    if (event.getWhoClicked() instanceof Player player) {
                        // Only reward if taking it out
                        manager.addXp(player, 1, "Brewing");
                    }
                }
            }
        }
    }
}