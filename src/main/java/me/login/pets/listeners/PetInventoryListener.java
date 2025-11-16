package me.login.pets.listeners;

import me.login.Login;
import me.login.pets.PetInventoryMenu;
import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

// --- FIXED: Added missing import ---
import org.bukkit.Bukkit;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PetInventoryListener implements Listener {

    private final Login plugin;
    private final PetManager petManager;
    private final PetsConfig config;

    private final NamespacedKey armorTypeKey;
    private final Set<Material> allowedWeapons = new HashSet<>(Arrays.asList(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    ));

    public PetInventoryListener(Login plugin, PetManager petManager, PetsConfig config) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.config = config;
        this.armorTypeKey = new NamespacedKey(plugin, "pet_armor_entity");
    }

    /**
     * Helper method to open the pet inventory for a player
     */
    public void openPetInventory(Player player, Pet pet, boolean isAdmin) {
        if (pet == null) return;
        new PetInventoryMenu(player, pet, isAdmin).open(player);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null || !(inv.getHolder() instanceof PetInventoryMenu)) {
            return;
        }

        PetInventoryMenu menu = (PetInventoryMenu) inv.getHolder();
        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        // Handle admin removal
        if (menu.isAdmin() && event.getClick() == ClickType.SHIFT_RIGHT) {
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT) {
                inv.setItem(slot, null);
                event.setCancelled(true);
                return;
            }
        }

        // Allow taking items
        if (event.getClickedInventory() == inv) {
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT) {
                return;
            }
        }

        // Allow placing items
        if (event.getClickedInventory() != null && event.getClickedInventory() != inv) {
            if (event.isShiftClick()) {
                event.setCancelled(true); // Block shift-clicking into the GUI
                return;
            }
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT) {
                // Player is placing an item in
                if (validateItem(menu.getPet(), event.getCurrentItem(), slot)) {
                    return; // Allow the click
                } else {
                    event.setCancelled(true); // Invalid item
                    // --- FIXED: Correct method call ---
                    petManager.getMessageHandler().sendPlayerActionBar(player, "<red>That item cannot go in this slot!</red>");
                }
            }
        }

        // Cancel all other clicks in the GUI
        if (event.getClickedInventory() == inv) {
            event.setCancelled(true);
        }
    }

    private boolean isArmorSlot(int slot) {
        for (int s : PetInventoryMenu.ARMOR_SLOTS) {
            if (s == slot) return true;
        }
        return false;
    }

    private boolean validateItem(Pet pet, ItemStack item, int slot) {
        if (item == null || item.getType() == Material.AIR) return false;

        if (isArmorSlot(slot)) {
            // Armor Slot
            if (!item.hasItemMeta()) return false;
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            if (!pdc.has(armorTypeKey, PersistentDataType.STRING)) return false;

            String requiredType = pdc.get(armorTypeKey, PersistentDataType.STRING);
            return requiredType.equalsIgnoreCase(pet.getPetType().name());

        } else if (slot == PetInventoryMenu.WEAPON_SLOT) {
            // Weapon Slot
            return allowedWeapons.contains(item.getType());
        }
        return false;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null || !(inv.getHolder() instanceof PetInventoryMenu)) {
            return;
        }

        PetInventoryMenu menu = (PetInventoryMenu) inv.getHolder();
        Pet pet = menu.getPet();

        // Save items from GUI to Pet object
        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < PetInventoryMenu.ARMOR_SLOTS.length; i++) {
            armor[i] = inv.getItem(PetInventoryMenu.ARMOR_SLOTS[i]);
        }
        pet.setArmorContents(armor);
        pet.setWeaponContent(inv.getItem(PetInventoryMenu.WEAPON_SLOT));

        // --- FIXED: Correct method call ---
        petManager.savePetInventory(pet);

        // Refresh pet if active
        Player owner = Bukkit.getPlayer(pet.getOwnerUuid());
        if (owner != null && owner.isOnline() && petManager.hasActivePet(owner.getUniqueId())) {
            if (petManager.getActivePet(owner.getUniqueId()).getType() == pet.getPetType()) {
                // Resummon to apply new stats
                petManager.summonPet(owner, pet.getPetType());
            }
        }
    }
}