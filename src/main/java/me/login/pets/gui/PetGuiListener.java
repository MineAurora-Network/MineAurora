package me.login.pets.gui;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.data.Pet;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class PetGuiListener implements Listener {

    private final PetManager petManager;
    private final PetMessageHandler messageHandler;

    public PetGuiListener(PetManager petManager, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.messageHandler = messageHandler;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof PetMenu)) return;

        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();

        // 1. Pet Click
        if (meta.getPersistentDataContainer().has(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING)) {
            String typeStr = meta.getPersistentDataContainer().get(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING);
            try {
                EntityType type = EntityType.valueOf(typeStr);
                if (event.getClick() == ClickType.SHIFT_RIGHT) {
                    Pet pet = petManager.getPet(player.getUniqueId(), type);
                    if (pet != null) new me.login.pets.PetInventoryMenu(player, pet, false).open(player);
                } else {
                    petManager.summonPet(player, type);
                    player.closeInventory();
                }
            } catch (IllegalArgumentException ignored) {}
            return;
        }

        // 2. Buttons
        if (clickedItem.getType() == Material.BARRIER) { // Despawn
            petManager.despawnPet(player.getUniqueId(), true);
            player.closeInventory();
        }
        else if (clickedItem.getType() == Material.COMPARATOR) { // Sort
            PetMenu menu = (PetMenu) inv.getHolder();
            PetMenu.PetMenuSort next = (menu.getSortMode() == PetMenu.PetMenuSort.RARITY) ? PetMenu.PetMenuSort.LEVEL : PetMenu.PetMenuSort.RARITY;
            new PetMenu(player, petManager, next).open(player);
        }
        else if (clickedItem.getType() == Material.RED_BED) { // Close
            player.closeInventory();
        }
    }
}