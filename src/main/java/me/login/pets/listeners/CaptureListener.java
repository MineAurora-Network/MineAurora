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
        // FIXED: Check item in the hand that triggered the event
        ItemStack itemInHand = event.getHand() == EquipmentSlot.HAND ?
                event.getPlayer().getInventory().getItemInMainHand() :
                event.getPlayer().getInventory().getItemInOffHand();

        if (itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) {
            return;
        }

        ItemMeta meta = itemInHand.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();
        String captureItemName = null;

        // Check if it's a capture item
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
            return;
        }

        // FIXED: It IS a capture item. Cancel event immediately to prevent Lead attachment.
        event.setCancelled(true);

        // Only proceed if it was the main hand (to prevent double firing)
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        if (!(event.getRightClicked() instanceof LivingEntity)) {
            return;
        }

        if (petManager.isPet(event.getRightClicked())) {
            return;
        }

        Player player = event.getPlayer();
        LivingEntity entity = (LivingEntity) event.getRightClicked();

        boolean itemUsed = petManager.attemptCapture(player, entity, captureItemName);

        if (itemUsed) {
            itemInHand.setAmount(itemInHand.getAmount() - 1);
        }
    }
}