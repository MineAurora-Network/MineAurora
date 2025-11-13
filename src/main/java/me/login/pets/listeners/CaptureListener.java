package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.PetsLogger;
import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Listener for handling the pet capture attempt (right-clicking a mob with a capture lead).
 */
public class CaptureListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;
    private final PetsLogger logger;

    public CaptureListener(PetManager petManager, PetsConfig config, PetMessageHandler messageHandler, PetsLogger logger) {
        this.petManager = petManager;
        this.config = config;
        this.messageHandler = messageHandler;
        this.logger = logger;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Only fire for main hand
        }

        if (!(event.getRightClicked() instanceof LivingEntity)) {
            return; // Not a capturable entity
        }

        // Prevent capturing other players' pets
        if (petManager.isPet(event.getRightClicked())) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return; // Not a custom item
        }

        ItemMeta meta = itemInHand.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        String captureItemName = null;

        // Iterate over *all* NBT keys on the item
        for (org.bukkit.NamespacedKey key : data.getKeys()) {
            String nbtValue = data.get(key, PersistentDataType.STRING);
            if (nbtValue != null) {
                String internalName = config.getCaptureItemName(nbtValue);
                if (internalName != null) {
                    captureItemName = internalName;
                    break;
                }
            }
        }

        if (captureItemName == null) {
            // This item doesn't have the NBT key for a capture item
            return;
        }

        // We have a valid capture item, cancel the default lead behavior
        event.setCancelled(true);

        // Handle the capture attempt
        LivingEntity entity = (LivingEntity) event.getRightClicked();

        // --- FIXED: Only consume item if the attempt actually proceeded ---
        boolean itemUsed = petManager.attemptCapture(player, entity, captureItemName);

        if (itemUsed) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }
    }
}