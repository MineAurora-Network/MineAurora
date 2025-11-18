package me.login.pets.gui;

import me.login.Login;
import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import net.kyori.adventure.text.Component;
// --- BUG FIX: Added missing import ---
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
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.stream.Collectors;

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

    public static NamespacedKey PET_TYPE_KEY;

    public PetMenu(Player player, PetManager petManager, PetMenuSort sortMode) {
        this.player = player;
        this.petManager = petManager;
        this.plugin = petManager.getPlugin();
        this.petsConfig = plugin.getPetsModule().getPetsConfig();
        this.pets = new ArrayList<>(petManager.getPlayerData(player.getUniqueId()));
        this.sortMode = sortMode;

        if (PET_TYPE_KEY == null) {
            PET_TYPE_KEY = new NamespacedKey(plugin, "pet-type");
        }

        Component title = MiniMessage.miniMessage().deserialize("<bold>My Pets (Sort: " + sortMode.name() + ")</bold>");
        this.inventory = Bukkit.createInventory(this, 54, title);

        sortPets();
        initializeItems();
    }

    /**
     * Sorts the internal pet list based on the current sort mode.
     */
    private void sortPets() {
        if (sortMode == PetMenuSort.RARITY) {
            List<String> tierOrder = petsConfig.getTierOrder();
            pets.sort(Comparator.comparingInt(pet -> {
                String tier = petsConfig.getPetTier(pet.getPetType());
                return tierOrder.indexOf(tier);
            }));
        } else {
            Collections.shuffle(pets);
        }
    }

    private void initializeItems() {
        for (int i = 0; i < pets.size(); i++) {
            if (i >= 45) break;
            Pet pet = pets.get(i);
            inventory.setItem(i, createPetItem(pet));
        }

        ItemStack filler = GuiUtils.createGuiItem(plugin, Material.BLACK_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

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

    private ItemStack createPetItem(Pet pet) {
        Material material = getSpawnEggMaterial(pet.getPetType());
        if (material == null) {
            material = Material.PAPER; // Fallback
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // --- BUG FIX: Removed italics ---
            meta.displayName(
                    MiniMessage.miniMessage().deserialize("<light_purple><bold>" + pet.getDisplayName() + "</bold></light_purple>")
                            .decoration(TextDecoration.ITALIC, false)
            );

            List<Component> loreLines = new ArrayList<>();
            // --- BUG FIX: Removed italics from all lore lines ---
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Type: <white>" + pet.getDefaultName() + "</white></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Rarity: <white>" + petsConfig.getPetTier(pet.getPetType()) + "</white></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>Level: <gold>" + pet.getLevel() + "</gold></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            loreLines.add(MiniMessage.miniMessage().deserialize("<gray>XP: <yellow>" + (int)pet.getXp() + " / " + (int)petsConfig.getXpRequired(pet.getLevel()) + "</yellow></gray>")
                    .decoration(TextDecoration.ITALIC, false));
            loreLines.add(Component.empty());

            if (pet.isOnCooldown()) {
                loreLines.add(MiniMessage.miniMessage().deserialize("<red>On Cooldown: " + pet.getRemainingCooldownSeconds() + "s</red>")
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                loreLines.add(MiniMessage.miniMessage().deserialize("<green>Ready to Summon!</green>")
                        .decoration(TextDecoration.ITALIC, false));
            }
            loreLines.add(MiniMessage.miniMessage().deserialize("<yellow>Click to select this pet.</yellow>")
                    .decoration(TextDecoration.ITALIC, false));

            meta.lore(loreLines); // No stream needed, already a List<Component>

            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(GuiUtils.getGuiItemKey(plugin), PersistentDataType.BYTE, (byte) 1);
            data.set(PET_TYPE_KEY, PersistentDataType.STRING, pet.getPetType().name());

            item.setItemMeta(meta);
        }

        return item;
    }

    private Material getSpawnEggMaterial(EntityType type) {
        String materialName = type.name() + "_SPAWN_EGG";
        try {
            return Material.valueOf(materialName);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    // --- NEW: Getter for sortMode ---
    public PetMenuSort getSortMode() {
        return sortMode;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}