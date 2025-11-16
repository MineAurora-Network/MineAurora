package me.login.pets.gui;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent; // This is the event being used
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PetGuiListener implements Listener {

    private final PetManager petManager;
    private final PetMessageHandler messageHandler;
    private final Map<UUID, EntityType> selectedPet = new HashMap<>();
    private final Map<UUID, EntityType> renamingPet = new HashMap<>();

    public PetGuiListener(PetManager petManager, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.messageHandler = messageHandler;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        if (inv.getHolder() == null || !(inv.getHolder() instanceof PetMenu)) {
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() != inv) {
            return;
        }

        Player p = (Player) event.getWhoClicked();
        ItemStack item = event.getCurrentItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }

        // Check if it's a pet item
        if (meta.getPersistentDataContainer().has(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING)) {
            EntityType type = EntityType.valueOf(meta.getPersistentDataContainer().get(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING));
            selectedPet.put(p.getUniqueId(), type);
            p.playSound(p.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1, 1);
            messageHandler.sendPlayerActionBar(p, "<green>Selected " + type.name() + ".</green>");
            return;
        }

        // Check navigation buttons (by slot)
        int slot = event.getSlot();

        // Summon
        if (slot == 48) {
            EntityType type = selectedPet.get(p.getUniqueId());
            if (type == null) {
                messageHandler.sendPlayerMessage(p, "<red>Select a pet first!</red>");
                return;
            }
            petManager.summonPet(p, type);
            p.closeInventory();
        }

        // Despawn
        else if (slot == 49) {
            petManager.despawnPet(p.getUniqueId(), true);
            p.closeInventory();
        }

        // Rename
        else if (slot == 50) {
            EntityType type = selectedPet.get(p.getUniqueId());
            if (type == null) {
                messageHandler.sendPlayerMessage(p, "<red>Select a pet first!</red>");
                return;
            }
            if (!p.hasPermission(petManager.getPetsConfig().getRenamePermission())) {
                messageHandler.sendPlayerMessage(p, "<red>You do not have permission to rename pets.</red>");
                return;
            }
            renamingPet.put(p.getUniqueId(), type);
            p.closeInventory();
            messageHandler.sendPlayerMessage(p, "<yellow>Enter the new name for your pet in chat. Type 'cancel' to cancel.</yellow>");
        }

        // Toggle Sort
        else if (slot == 51) {
            PetMenu.PetMenuSort currentSort = ((PetMenu) inv.getHolder()).getSortMode();
            PetMenu.PetMenuSort nextSort = (currentSort == PetMenu.PetMenuSort.RARITY) ? PetMenu.PetMenuSort.RANDOM : PetMenu.PetMenuSort.RARITY;
            new PetMenu(p, petManager, nextSort).open();
        }

        // Close
        else if (slot == 53) {
            p.closeInventory();
        }
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        if (!renamingPet.containsKey(p.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        EntityType petType = renamingPet.remove(p.getUniqueId());

        // --- FIXED: Use event.getMessage() for AsyncPlayerChatEvent ---
        String newName = event.getMessage();

        if (newName.equalsIgnoreCase("cancel")) {
            messageHandler.sendPlayerMessage(p, "<red>Rename cancelled.</red>");
            return;
        }

        PetsConfig config = petManager.getPetsConfig();
        if (newName.length() > config.getMaxNameLength()) {
            messageHandler.sendPlayerMessage(p, "<red>Name is too long! Max " + config.getMaxNameLength() + " chars.</red>");
            return;
        }

        Pet pet = petManager.getPet(p.getUniqueId(), petType);
        if (pet != null) {
            // Run on main thread
            p.getServer().getScheduler().runTask(petManager.getPlugin(), () -> {
                String oldName = pet.getDisplayName();
                petManager.updatePetName(p.getUniqueId(), petType, newName);
                messageHandler.sendPlayerMessage(p, "<green>Your " + pet.getDefaultName() + " has been renamed to " + newName + "!</green>");
            });
        }
    }
}