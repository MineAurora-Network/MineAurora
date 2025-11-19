package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
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

import java.util.UUID;

public class PetInteractListener implements Listener {

    private final PetManager petManager;
    private final PetMessageHandler messageHandler;
    private final PetInventoryListener inventoryListener;

    public PetInteractListener(PetManager petManager, PetMessageHandler messageHandler, PetInventoryListener inventoryListener) {
        this.petManager = petManager;
        this.messageHandler = messageHandler;
        this.inventoryListener = inventoryListener;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();
        Entity clickedEntity = event.getRightClicked();
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        if (!(clickedEntity instanceof LivingEntity)) return;
        LivingEntity livingEntity = (LivingEntity) clickedEntity;

        // --- 1. Handle Pet Interactions ---
        if (petManager.isPet(livingEntity)) {
            UUID ownerUuid = petManager.getPetOwner(livingEntity);
            if (ownerUuid == null || !ownerUuid.equals(player.getUniqueId())) return;

            event.setCancelled(true);

            // Inventory
            if (player.isSneaking() && (itemInHand == null || itemInHand.getType() == Material.AIR)) {
                inventoryListener.openPetInventory(player, petManager.getPet(ownerUuid, livingEntity.getType()), false);
                return;
            }

            // Feed Pet (Any pet fruit)
            if (itemInHand != null && itemInHand.hasItemMeta()) {
                NamespacedKey fruitKey = new NamespacedKey("mineaurora", "pet_fruit_id");
                NamespacedKey fruitKeyAlt = new NamespacedKey("mineaurora", "pet_fruit");
                PersistentDataContainer pdc = itemInHand.getItemMeta().getPersistentDataContainer();

                if (pdc.has(fruitKey, PersistentDataType.STRING) || pdc.has(fruitKeyAlt, PersistentDataType.STRING)) {
                    petManager.feedPet(player, itemInHand);
                    return;
                }
            }

            // Pet Particles
            if (!player.isSneaking() && (itemInHand == null || itemInHand.getType() == Material.AIR)) {
                petManager.showPetParticles(player, livingEntity);
                return;
            }
            return;
        }

        // --- 2. Handle Non-Pet Interactions ---
        if (itemInHand == null || itemInHand.getType() == Material.AIR || !itemInHand.hasItemMeta()) return;
        ItemMeta meta = itemInHand.getItemMeta();
        PersistentDataContainer data = meta.getPersistentDataContainer();

        // Capture
        String captureItemName = getCaptureItemName(data);
        if (captureItemName != null) {
            event.setCancelled(true);
            boolean itemUsed = petManager.attemptCapture(player, livingEntity, captureItemName);
            if (itemUsed) {
                itemInHand.setAmount(itemInHand.getAmount() - 1);
            }
            return;
        }

        // Targeting Shard
        NamespacedKey shardKey = new NamespacedKey("mineaurora", "pet_utility_id");
        if (data.has(shardKey, PersistentDataType.STRING) && "amethyst_shard".equals(data.get(shardKey, PersistentDataType.STRING))) {
            event.setCancelled(true);
            LivingEntity activePet = petManager.getActivePet(player.getUniqueId());
            if (activePet == null) {
                messageHandler.sendPlayerMessage(player, "<red>You do not have an active pet to command!</red>");
                return;
            }
            petManager.getTargetSelection().setPetTarget(player, livingEntity);
            messageHandler.sendPlayerActionBar(player, "<light_purple>Pet targeting " + livingEntity.getType().name() + "!</light_purple>");
            itemInHand.setAmount(itemInHand.getAmount() - 1); // Consume shard
            return;
        }
    }

    private String getCaptureItemName(PersistentDataContainer data) {
        for (org.bukkit.NamespacedKey key : data.getKeys()) {
            if (key.getNamespace().equals("mineaurora") && key.getKey().startsWith("pet_lead_")) {
                return data.get(key, PersistentDataType.STRING);
            }
        }
        return null;
    }
}