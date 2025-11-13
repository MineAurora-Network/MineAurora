package me.login.pets.gui;

import me.login.Login;
import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Represents the 6-row Pet Menu GUI.
 * Handles the creation and display of the inventory, pet items, and navigation controls.
 */
public class PetMenu implements InventoryHolder {

    public enum PetMenuSort {
        RARITY,
        RANDOM
    }

    private final Inventory inventory;
    private final Player player;
    private final PetManager petManager;
    private List<Pet> pets;
    private final Login plugin;
    private final PetsConfig petsConfig;
    private final PetMenuSort sortMode;

    // NamespacedKey to store the EntityType on the item stack
    public static NamespacedKey PET_TYPE_KEY;

    public PetMenu(Player player, PetManager petManager, PetMenuSort sortMode) {
        this.player = player;
        this.petManager = petManager;
        this.plugin = petManager.getPlugin();
        this.petsConfig = plugin.getPetsModule().getPetsConfig();
        this.pets = new ArrayList<>(petManager.getPlayerData(player.getUniqueId())); // Make a mutable copy
        this.sortMode = sortMode;

        if (PET_TYPE_KEY == null) {
            PET_TYPE_KEY = new NamespacedKey(plugin, "pet-type");
        }

        Component title = MiniMessage.miniMessage().deserialize("<bold>My Pets (Sort: " + sortMode.name() + ")</bold>");
        this.inventory = Bukkit.createInventory(this, 54, title); // 6 rows * 9 slots

        sortPets();
        initializeItems();
    }

    /**
     * Sorts the internal pet list based on the current sort mode.
     */
    private void sortPets() {
        if (sortMode == PetMenuSort.RARITY) {
            // Use the tier list from config to sort
            List<String> tierOrder = petsConfig.getTierOrder();
            pets.sort(Comparator.comparingInt(pet -> {
                String tier = petsConfig.getPetTier(pet.getPetType());
                return tierOrder.indexOf(tier); // Lower index (easier) = first
            }));
        } else {
            // Random
            Collections.shuffle(pets);
        }
    }

    /**
     * Fills the inventory with pet items and navigation controls.
     */
    private void initializeItems() {
        // Fill rows 0-4 with pets
        for (int i = 0; i < pets.size(); i++) {
            if (i >= 45) break; // Max 45 pets (5 rows)
            Pet pet = pets.get(i);
            inventory.setItem(i, createPetItem(pet));
        }

        // Fill 6th row (slots 45-53) with navigation
        ItemStack filler = GuiUtils.createGuiItem(plugin, Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // Navigation buttons
        inventory.setItem(48, GuiUtils.createGuiItem(plugin, Material.LEAD,
                "<green><bold>Summon Pet</bold></green>",
                Arrays.asList("<gray>Click a pet above, then", "<gray>click here to summon it!</gray>")));

        inventory.setItem(49, GuiUtils.createGuiItem(plugin, Material.BARRIER,
                "<red><bold>Despawn Active Pet</bold></red>",
                Arrays.asList("<gray>Click to send your", "<gray>active pet home.</gray>")));

        inventory.setItem(50, GuiUtils.createGuiItem(plugin, Material.NAME_TAG,
                "<yellow><bold>Rename Pet</bold></yellow>",
                Arrays.asList("<gray>Click a pet above, then", "<gray>click here to rename it.</gray>",
                        (player.hasPermission(petsConfig.getRenamePermission()) ? "<green>Click to rename!</green>" : "<dark_gray>(Requires Permission)</dark_gray>"))));

        inventory.setItem(51, GuiUtils.createGuiItem(plugin, Material.COMPARATOR,
                "<aqua><bold>Toggle Sort</bold></aqua>",
                Arrays.asList("<gray>Current: " + sortMode.name() + "</gray>", "<yellow>Click to change sort!</yellow>")));

        inventory.setItem(53, GuiUtils.createGuiItem(plugin, Material.RED_BED,
                "<red><bold>Close Menu</bold></red>",
                Collections.singletonList("<gray>Click to close this menu.</gray>")));
    }

    /**
     * Creates an ItemStack representing a captured pet.
     * Uses a Spawn Egg if possible, otherwise a generic item.
     * @param pet The pet to represent.
     * @return The ItemStack for the GUI.
     */
    private ItemStack createPetItem(Pet pet) {
        Material material = getSpawnEggMaterial(pet.getPetType());
        if (material == null) {
            material = Material.PAPER; // Fallback
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // --- FIXED: Removed setSpawnedType call to prevent UnsupportedOperationException ---
            // The material itself (e.g. SKELETON_SPAWN_EGG) is sufficient for the texture.

            // Set name (MiniMessage)
            meta.displayName(
                    MiniMessage.miniMessage().deserialize("<light_purple><bold>" + pet.getDisplayName() + "</bold></light_purple>")
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> loreLines = new ArrayList<>();
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Type: <white>" + pet.getDefaultName() + "</white></gray>"));
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Rarity: <white>" + petsConfig.getPetTier(pet.getPetType()) + "</white></gray>"));

            // --- NEW: Level Info ---
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Level: <gold>" + pet.getLevel() + "</gold></gray>"));
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>XP: <yellow>" + (int)pet.getXp() + " / " + (int)petsConfig.getXpRequired(pet.getLevel()) + "</yellow></gray>"));

            loreLines.add(Component.empty());

            if (pet.isOnCooldown()) {
                loreLines.add(MiniMessage.miniMessage().deserialize("<red>On Cooldown: " + pet.getRemainingCooldownSeconds() + "s</red>"));
            } else {
                loreLines.add(MiniMessage.miniMessage().deserialize("<green>Ready to Summon!</green>"));
            }
            loreLines.add(MiniMessage.miniMessage().deserialize("<yellow>Click to select this pet.</yellow>"));

            meta.lore(loreLines.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).collect(Collectors.toList()));
            // Add persistent data
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(GuiUtils.getGuiItemKey(plugin), PersistentDataType.BYTE, (byte) 1);
            data.set(PET_TYPE_KEY, PersistentDataType.STRING, pet.getPetType().name());

            item.setItemMeta(meta);
        }

        return item;
    }

    /**
     * Finds the corresponding Spawn Egg material for an EntityType.
     * @param type The EntityType.
     * @return The Material, or null if no egg exists.
     */
    private Material getSpawnEggMaterial(EntityType type) {
        String materialName = type.name() + "_SPAWN_EGG";
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null; // No spawn egg for this entity (e.g., Wither, Ender Dragon)
        }
    }

    /**
     * Opens the inventory for the player.
     */
    public void open() {
        player.openInventory(inventory);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}