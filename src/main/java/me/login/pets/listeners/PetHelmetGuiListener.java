package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.gui.PetHelmetMenu;
import me.login.pets.data.Pet;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.List;

public class PetHelmetGuiListener implements Listener {

    private final PetManager petManager;
    // List of helmets to allow (optional simple check)
    private final List<String> HELMET_TYPES = Arrays.asList("_HELMET", "SKULL", "HEAD", "PUMPKIN");

    public PetHelmetGuiListener(PetManager petManager) {
        this.petManager = petManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null || !(inv.getHolder() instanceof PetHelmetMenu)) {
            return;
        }

        PetHelmetMenu menu = (PetHelmetMenu) inv.getHolder();
        int slot = event.getSlot();

        // Prevent clicking anything except the helmet slot and their own inventory
        if (event.getClickedInventory() == inv) {
            if (slot != PetHelmetMenu.HELMET_SLOT) {
                event.setCancelled(true);
                return;
            }
        } else if (event.isShiftClick()) {
            // If shift clicking from player inv, make sure it goes to slot 13
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && isHelmet(item) && inv.getItem(PetHelmetMenu.HELMET_SLOT) == null) {
                inv.setItem(PetHelmetMenu.HELMET_SLOT, item);
                event.getClickedInventory().setItem(event.getSlot(), null); // Remove from player
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null || !(inv.getHolder() instanceof PetHelmetMenu)) {
            return;
        }

        Player player = (Player) event.getPlayer();
        PetHelmetMenu menu = (PetHelmetMenu) inv.getHolder();
        Pet pet = menu.getPet();

        // Get the item from the slot
        ItemStack helmet = inv.getItem(PetHelmetMenu.HELMET_SLOT);

        // Update the pet's armor array
        ItemStack[] currentArmor = pet.getArmorContents();
        if (currentArmor == null || currentArmor.length < 4) {
            currentArmor = new ItemStack[4];
        }

        // Set the helmet (Index 3 is standard helmet slot)
        currentArmor[3] = helmet;
        pet.setArmorContents(currentArmor);

        // Save to database
        petManager.savePetInventory(pet);

        if (helmet != null && helmet.getType() != Material.AIR) {
            petManager.getMessageHandler().sendPlayerMessage(player, "<green>Helmet equipped! You can now summon your " + pet.getPetType().name() + ".</green>");
            // Optional: Automatically summon?
            // petManager.summonPet(player, pet.getPetType());
        } else {
            petManager.getMessageHandler().sendPlayerMessage(player, "<red>No helmet equipped. You cannot summon this pet in sunlight.</red>");
        }
    }

    private boolean isHelmet(ItemStack item) {
        if (item == null) return false;
        String type = item.getType().name();
        for (String suffix : HELMET_TYPES) {
            if (type.endsWith(suffix)) return true;
        }
        return false;
    }
}