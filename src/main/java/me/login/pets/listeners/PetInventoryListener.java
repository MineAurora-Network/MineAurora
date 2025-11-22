package me.login.pets.listeners;

import me.login.Login;
import me.login.pets.PetInventoryMenu;
import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.Bukkit;
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

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class PetInventoryListener implements Listener {

    private final Login plugin;
    private final PetManager petManager;
    private final PetsConfig config;
    private final Set<Material> allowedWeapons = new HashSet<>(Arrays.asList(
            Material.WOODEN_SWORD, Material.STONE_SWORD, Material.IRON_SWORD, Material.GOLDEN_SWORD, Material.DIAMOND_SWORD, Material.NETHERITE_SWORD,
            Material.WOODEN_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.GOLDEN_AXE, Material.DIAMOND_AXE, Material.NETHERITE_AXE
    ));

    public PetInventoryListener(Login plugin, PetManager petManager, PetsConfig config) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.config = config;
    }

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

        // FIX: Prevent other players from modifying inventory
        if (!menu.getPet().getOwnerUuid().equals(player.getUniqueId()) && !menu.isAdmin()) {
            event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();

        if (menu.isAdmin() && event.getClick() == ClickType.SHIFT_RIGHT) {
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT || slot == PetInventoryMenu.ATTRIBUTE_SLOT) {
                inv.setItem(slot, null);
                event.setCancelled(true);
                return;
            }
        }

        if (event.getClickedInventory() == inv) {
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT || slot == PetInventoryMenu.ATTRIBUTE_SLOT) {
                return; // Allow placing items here
            }
        }

        if (event.getClickedInventory() != null && event.getClickedInventory() != inv) {
            if (event.isShiftClick()) {
                event.setCancelled(true);
                return;
            }
            if (isArmorSlot(slot) || slot == PetInventoryMenu.WEAPON_SLOT || slot == PetInventoryMenu.ATTRIBUTE_SLOT) {
                if (validateItem(menu.getPet(), event.getCurrentItem(), slot)) {
                    return;
                } else {
                    event.setCancelled(true);
                    petManager.getMessageHandler().sendPlayerActionBar(player, "<red>That item cannot go in this slot!</red>");
                }
            }
        }

        if (event.getClickedInventory() == inv) {
            // Cancel clicks on filler items
            if (!isArmorSlot(slot) && slot != PetInventoryMenu.WEAPON_SLOT && slot != PetInventoryMenu.ATTRIBUTE_SLOT) {
                event.setCancelled(true);
            }
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
        if (!item.hasItemMeta()) return false;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();

        if (isArmorSlot(slot)) {
            NamespacedKey typeKey = new NamespacedKey("mineaurora", "entity_armor_type");
            NamespacedKey tierKey = new NamespacedKey("mineaurora", "custom_tier");

            if (pdc.has(typeKey, PersistentDataType.STRING)) {
                String type = pdc.get(typeKey, PersistentDataType.STRING);
                return type != null && type.equalsIgnoreCase(pet.getPetType().name());
            }

            if (pdc.has(tierKey, PersistentDataType.STRING)) {
                return true;
            }
            return false;

        } else if (slot == PetInventoryMenu.WEAPON_SLOT) {
            return allowedWeapons.contains(item.getType());

        } else if (slot == PetInventoryMenu.ATTRIBUTE_SLOT) {
            NamespacedKey attrKey = new NamespacedKey("mineaurora", "pet_attribute_id");
            return pdc.has(attrKey, PersistentDataType.STRING);
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

        // Only owner can save changes
        if (!event.getPlayer().getUniqueId().equals(pet.getOwnerUuid()) && !menu.isAdmin()) return;

        ItemStack[] armor = new ItemStack[4];
        for (int i = 0; i < PetInventoryMenu.ARMOR_SLOTS.length; i++) {
            armor[i] = inv.getItem(PetInventoryMenu.ARMOR_SLOTS[i]);
        }
        pet.setArmorContents(armor);
        pet.setWeaponContent(inv.getItem(PetInventoryMenu.WEAPON_SLOT));
        pet.setAttributeContent(inv.getItem(PetInventoryMenu.ATTRIBUTE_SLOT));

        petManager.savePetInventory(pet);

        Player owner = Bukkit.getPlayer(pet.getOwnerUuid());
        // If pet is active, re-summon to apply new stats
        if (owner != null && owner.isOnline() && petManager.hasActivePet(owner.getUniqueId())) {
            if (petManager.getActivePet(owner.getUniqueId()).getType() == pet.getPetType()) {
                petManager.summonPet(owner, pet.getPetType());
            }
        }
    }
}