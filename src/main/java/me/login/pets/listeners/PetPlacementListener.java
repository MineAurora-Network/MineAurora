package me.login.pets.listeners;

import me.login.Login;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;

public class PetPlacementListener implements Listener {

    private final List<NamespacedKey> forbiddenKeys;

    public PetPlacementListener(Login plugin) {
        // Register all keys that identify items that should NOT be placed
        this.forbiddenKeys = Arrays.asList(
                new NamespacedKey("mineaurora", "pet_fruit_id"),
                new NamespacedKey("mineaurora", "pet_fruit"),
                new NamespacedKey("mineaurora", "pet_utility_id"), // Amethyst Shard
                new NamespacedKey("mineaurora", "pet_attribute_id"), // Attribute Shards
                new NamespacedKey("mineaurora", "entity_armor_type"), // Pet Armor
                new NamespacedKey("mineaurora", "custom_tier") // Generic Pet Armor
        );
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        for (NamespacedKey key : forbiddenKeys) {
            if (pdc.has(key, PersistentDataType.STRING)) {
                event.setCancelled(true);
                return;
            }
        }
    }
}