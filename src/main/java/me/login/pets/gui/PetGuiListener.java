package me.login.pets.gui;

import me.login.pets.PetFruitShop;
import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

        // --- NEW: Handle Fruit Shop ---
        if (inv.getHolder() instanceof PetFruitShop) {
            if (!(event.getWhoClicked() instanceof Player)) return;
            Player player = (Player) event.getWhoClicked();

            // FIX: Cancel events ONLY in top inventory (The Shop) to prevent stealing
            if (event.getClickedInventory() == inv) {
                event.setCancelled(true);
            } else if (event.isShiftClick()) {
                // Cancel shift-clicking from player inventory INTO shop
                event.setCancelled(true);
                return;
            } else {
                // Allow managing own inventory
                return;
            }

            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() == Material.AIR) return;

            PetFruitShop shop = (PetFruitShop) inv.getHolder();
            int slot = event.getSlot();

            if (shop.getType() == PetFruitShop.ShopType.MAIN) {
                if (slot == 11) {
                    petManager.getFruitShop().openXpShop(player);
                } else if (slot == 15) {
                    petManager.getFruitShop().openHungerShop(player);
                }
            } else {
                if (slot == 31 && clicked.getType() == Material.BARRIER) {
                    petManager.getFruitShop().openMainMenu(player);
                    return;
                }

                if (event.getClick().isLeftClick()) {
                    petManager.getFruitShop().processBuy(player, clicked, 1);
                } else if (event.getClick().isShiftClick() && event.getClick().isRightClick()) {
                    player.closeInventory();
                    petManager.getFruitShop().openSignInput(player, clicked);
                }
            }
            return;
        }

        // --- Pet Menu Logic ---
        if (!(inv.getHolder() instanceof PetMenu)) return;

        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // FIX: Cancel click in Top Inventory (Pet Menu)
        if (event.getClickedInventory() == inv) {
            event.setCancelled(true);
        } else if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        } else {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR || !clickedItem.hasItemMeta()) return;

        ItemMeta meta = clickedItem.getItemMeta();

        if (meta.getPersistentDataContainer().has(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING)) {
            String typeStr = meta.getPersistentDataContainer().get(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING);
            try {
                EntityType type = EntityType.valueOf(typeStr);
                if (event.getClick().isLeftClick()) {
                    petManager.summonPet(player, type);
                    player.closeInventory();
                }
            } catch (IllegalArgumentException ignored) {}
            return;
        }

        if (clickedItem.getType() == Material.BARRIER) {
            petManager.despawnPet(player.getUniqueId(), true);
            player.closeInventory();
        }
        else if (clickedItem.getType() == Material.COMPARATOR) {
            PetMenu menu = (PetMenu) inv.getHolder();
            PetMenu.PetMenuSort next = (menu.getSortMode() == PetMenu.PetMenuSort.RARITY) ? PetMenu.PetMenuSort.LEVEL : PetMenu.PetMenuSort.RARITY;
            new PetMenu(player, petManager, next).open(player);
        }
        else if (clickedItem.getType() == Material.RED_BED) {
            player.closeInventory();
        }
    }
}