package me.login.pets.listeners;

import me.login.Login;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class PetPlacementListener implements Listener {

    private final NamespacedKey fruitKey;

    public PetPlacementListener(Login plugin) {
        // --- BUG FIX: Hard-coded namespace to match item creation ---
        // The old key (new NamespacedKey(plugin, "pet_fruit_id"))
        // might not resolve to "mineaurora" if the plugin name in plugin.yml is different.
        // This is safer and matches PetManager and PetInteractListener.
        this.fruitKey = new NamespacedKey("mineaurora", "pet_fruit_id");
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) {
            return;
        }

        if (item.getItemMeta().getPersistentDataContainer().has(fruitKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            // Optionally send a message
            // event.getPlayer().sendActionBar(MiniMessage.miniMessage().deserialize("<red>You cannot place this item!</red>"));
        }
    }
}