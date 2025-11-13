package me.login.pets.gui;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.PetsLogger;
import me.login.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Listener for handling clicks within the PetMenu GUI.
 */
public class PetGuiListener implements Listener {

    private final PetManager petManager;
    private final PetMessageHandler messageHandler;
    private final Login plugin;
    private final PetsConfig petsConfig;
    private final PetsLogger petsLogger;

    // Stores which pet a player has selected
    private final Map<UUID, EntityType> selectedPet = new HashMap<>();
    // Stores the current sort mode for each player
    private final Map<UUID, PetMenu.PetMenuSort> sortMode = new HashMap<>();

    public PetGuiListener(PetManager petManager, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.messageHandler = messageHandler;
        this.plugin = petManager.getPlugin();
        // --- FIXED: Get config/logger from plugin instance ---
        this.petsConfig = petManager.getPlugin().getPetsModule().getPetsConfig();
        this.petsLogger = petManager.getPlugin().getPetsModule().getPetsLogger();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof PetMenu)) {
            return; // Not our GUI
        }

        // Prevent any and all item moving
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();

        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // Check if it's a GUI item (it should be)
        if (!GuiUtils.isGuiItem(plugin, clickedItem)) {
            return;
        }

        PersistentDataContainer data = clickedItem.getItemMeta().getPersistentDataContainer();

        // --- Handle Pet Selection ---
        if (data.has(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING)) {
            EntityType petType = EntityType.valueOf(data.get(PetMenu.PET_TYPE_KEY, PersistentDataType.STRING));
            selectedPet.put(player.getUniqueId(), petType);
            messageHandler.sendPlayerMessage(player, "<green>You selected your " + petType.name() + ".</green>");
            return;
        }

        // --- Handle Navigation Clicks ---
        switch (event.getSlot()) {
            case 48: // Summon Pet
                summonPet(player);
                break;
            case 49: // Despawn Pet
                despawnPet(player);
                break;
            case 50: // Rename Pet
                handleRename(player);
                break;
            case 51: // Sort Pets
                toggleSort(player);
                break;
            case 53: // Close Menu
                player.closeInventory();
                break;
        }
    }

    private void summonPet(Player player) {
        EntityType petType = selectedPet.get(player.getUniqueId());
        if (petType == null) {
            messageHandler.sendPlayerMessage(player, "<red>You need to click a pet first!</red>");
            return;
        }

        if (petManager.isPetOnCooldown(player.getUniqueId(), petType)) {
            long remaining = petManager.getPetCooldownRemaining(player.getUniqueId(), petType);
            messageHandler.sendPlayerMessage(player, "<red>Your " + petType.name() + " is on cooldown for " + remaining + "s.</red>");
            return;
        }

        // Despawn old pet if one is active
        if (petManager.hasActivePet(player.getUniqueId())) {
            petManager.despawnPet(player.getUniqueId(), false);
        }

        // Summon the new pet
        petManager.summonPet(player, petType);
        player.closeInventory();
        messageHandler.sendPlayerMessage(player, "<green>Your " + petType.name() + " has been summoned!</green>");
    }

    private void despawnPet(Player player) {
        if (petManager.hasActivePet(player.getUniqueId())) {
            petManager.despawnPet(player.getUniqueId(), true);
        } else {
            messageHandler.sendPlayerMessage(player, "<red>You don't have an active pet summoned.</red>");
        }
    }

    private void handleRename(Player player) {
        if (!player.hasPermission(petsConfig.getRenamePermission())) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission to rename pets.</red>");
            return;
        }

        EntityType petType = selectedPet.get(player.getUniqueId());
        if (petType == null) {
            messageHandler.sendPlayerMessage(player, "<red>You must select a pet to rename first.</red>");
            return;
        }

        Pet pet = petManager.getPet(player.getUniqueId(), petType);
        if (pet == null) return; // Should not happen

        try {
            // Open the Sign GUI
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^", "Enter new name:", "Max " + petsConfig.getMaxNameLength() + " chars")
                    .setHandler((p, lines) -> {
                        String newName = lines.getLine(0).trim();

                        // Run the logic back on the main thread
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!p.hasPermission(petsConfig.getRenamePermission())) {
                                messageHandler.sendPlayerMessage(p, "<red>Permission error.</red>");
                                return;
                            }

                            if (newName.isEmpty()) {
                                messageHandler.sendPlayerMessage(p, "<yellow>Rename cancelled. Name cannot be empty.</yellow>");
                                return;
                            }

                            if (newName.length() > petsConfig.getMaxNameLength()) {
                                messageHandler.sendPlayerMessage(p, "<red>Name is too long! Max " + petsConfig.getMaxNameLength() + " characters.</red>");
                                return;
                            }

                            String oldName = pet.getDisplayName();
                            petManager.updatePetName(p.getUniqueId(), petType, newName);
                            petsLogger.logRename(p.getName(), petType.name(), oldName, newName);
                            messageHandler.sendPlayerMessage(p, "<green>Your " + pet.getDefaultName() + " has been renamed to " + newName + "!</green>");

                            // Re-open the menu to show the change
                            new PetMenu(p, petManager, sortMode.getOrDefault(p.getUniqueId(), PetMenu.PetMenuSort.RARITY)).open();
                        });
                        return null; // Close the GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Pet Rename SignGUI", e);
            messageHandler.sendPlayerMessage(player, "<red>Error opening rename prompt.</red>");
        }
    }

    private void toggleSort(Player player) {
        PetMenu.PetMenuSort currentSort = sortMode.getOrDefault(player.getUniqueId(), PetMenu.PetMenuSort.RARITY);
        PetMenu.PetMenuSort newSort = (currentSort == PetMenu.PetMenuSort.RARITY) ? PetMenu.PetMenuSort.RANDOM : PetMenu.PetMenuSort.RARITY;
        sortMode.put(player.getUniqueId(), newSort);

        messageHandler.sendPlayerMessage(player, "<yellow>Sorting by: " + newSort.name() + "</yellow>");

        // Re-open the menu with the new sort
        new PetMenu(player, petManager, newSort).open();
    }
}