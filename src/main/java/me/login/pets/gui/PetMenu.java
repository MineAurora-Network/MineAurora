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
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PetMenu implements InventoryHolder {

    public enum PetMenuSort {
        RARITY,
        LEVEL // Changed from RANDOM to LEVEL for better UX
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

        Component title = MiniMessage.miniMessage().deserialize("<bold>My Pets</bold>");
        this.inventory = Bukkit.createInventory(this, 54, title);

        sortPets();
        initializeItems();
    }

    private void sortPets() {
        if (sortMode == PetMenuSort.RARITY) {
            // Assuming tier order exists, if not fall back to Name
            try {
                List<String> tierOrder = petsConfig.getTierOrder();
                if (tierOrder != null && !tierOrder.isEmpty()) {
                    pets.sort(Comparator.comparingInt(pet -> {
                        String tier = petsConfig.getPetTier(pet.getPetType());
                        return tierOrder.indexOf(tier);
                    }));
                    return;
                }
            } catch (Exception ignored) {}
            // Fallback sort
            pets.sort(Comparator.comparing(Pet::getDefaultName));
        } else {
            // Sort by Level High -> Low
            pets.sort(Comparator.comparingInt(Pet::getLevel).reversed());
        }
    }

    private void initializeItems() {
        // 1. Add Pets
        for (int i = 0; i < pets.size(); i++) {
            if (i >= 45) break;
            Pet pet = pets.get(i);
            inventory.setItem(i, createPetItem(pet));
        }

        // 2. Fill Glass
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillMeta = filler.getItemMeta();
        fillMeta.displayName(Component.empty());
        filler.setItemMeta(fillMeta);

        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, filler);
        }

        // 3. Add Buttons
        // Slot 48: Summon Info (Visual only, actual summon is clicking pet)
        ItemStack infoItem = new ItemStack(Material.LEAD);
        ItemMeta infoMeta = infoItem.getItemMeta();
        infoMeta.displayName(MiniMessage.miniMessage().deserialize("<green><bold>Summon Pet</bold>").decoration(TextDecoration.ITALIC, false));
        infoMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<gray>Click a pet icon above").decoration(TextDecoration.ITALIC, false),
                MiniMessage.miniMessage().deserialize("<gray>to summon it!").decoration(TextDecoration.ITALIC, false)));
        infoItem.setItemMeta(infoMeta);
        inventory.setItem(48, infoItem);

        // Slot 49: Despawn
        ItemStack despawnItem = new ItemStack(Material.BARRIER);
        ItemMeta despawnMeta = despawnItem.getItemMeta();
        despawnMeta.displayName(MiniMessage.miniMessage().deserialize("<red><bold>Despawn Active Pet</bold>").decoration(TextDecoration.ITALIC, false));
        despawnMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<gray>Send your pet home.").decoration(TextDecoration.ITALIC, false)));
        despawnItem.setItemMeta(despawnMeta);
        inventory.setItem(49, despawnItem);

        // Slot 50: Rename
        ItemStack renameItem = new ItemStack(Material.NAME_TAG);
        ItemMeta renameMeta = renameItem.getItemMeta();
        renameMeta.displayName(MiniMessage.miniMessage().deserialize("<yellow><bold>Rename Pet</bold>").decoration(TextDecoration.ITALIC, false));
        renameMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<gray>Click a pet with Shift+Right").decoration(TextDecoration.ITALIC, false),
                MiniMessage.miniMessage().deserialize("<gray>to open its inventory/settings.").decoration(TextDecoration.ITALIC, false)));
        renameItem.setItemMeta(renameMeta);
        inventory.setItem(50, renameItem);

        // Slot 51: Sort
        ItemStack sortItem = new ItemStack(Material.COMPARATOR);
        ItemMeta sortMeta = sortItem.getItemMeta();
        sortMeta.displayName(MiniMessage.miniMessage().deserialize("<aqua><bold>Sort: " + sortMode.name() + "</bold>").decoration(TextDecoration.ITALIC, false));
        sortMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<yellow>Click to switch mode.").decoration(TextDecoration.ITALIC, false)));
        sortItem.setItemMeta(sortMeta);
        inventory.setItem(51, sortItem);

        // Slot 53: Close
        ItemStack closeItem = new ItemStack(Material.RED_BED);
        ItemMeta closeMeta = closeItem.getItemMeta();
        closeMeta.displayName(MiniMessage.miniMessage().deserialize("<red><bold>Close Menu</bold>").decoration(TextDecoration.ITALIC, false));
        closeItem.setItemMeta(closeMeta);
        inventory.setItem(53, closeItem);
    }

    private ItemStack createPetItem(Pet pet) {
        Material material = Material.getMaterial(pet.getPetType().name() + "_SPAWN_EGG");
        if (material == null) material = Material.PAPER;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.displayName(MiniMessage.miniMessage().deserialize("<light_purple><bold>" + pet.getDisplayName() + "</bold>")
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Type: <white>" + pet.getDefaultName()).decoration(TextDecoration.ITALIC, false));
            // Stats Bars
            lore.add(Component.empty());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Health: " + getProgressBar(pet.getHealth(), 20, 10, "|", "<red>", "<gray>") + " <white>(" + (int)pet.getHealth() + "/20)").decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Hunger: " + getProgressBar(pet.getHunger(), 20, 10, "|", "<gold>", "<gray>") + " <white>(" + (int)pet.getHunger() + "/20)").decoration(TextDecoration.ITALIC, false));

            double maxXp = petsConfig.getXpRequired(pet.getLevel());
            lore.add(MiniMessage.miniMessage().deserialize("<gray>XP:     " + getProgressBar(pet.getXp(), maxXp, 10, "|", "<green>", "<gray>")).decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Level: <gold>" + pet.getLevel()).decoration(TextDecoration.ITALIC, false));

            lore.add(Component.empty());

            if (pet.isOnCooldown()) {
                lore.add(MiniMessage.miniMessage().deserialize("<red>On Cooldown: " + pet.getRemainingCooldownSeconds() + "s").decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(MiniMessage.miniMessage().deserialize("<green>● Ready to Summon").decoration(TextDecoration.ITALIC, false));
                lore.add(MiniMessage.miniMessage().deserialize("<yellow>Left-Click to Summon").decoration(TextDecoration.ITALIC, false));
                lore.add(MiniMessage.miniMessage().deserialize("<yellow>Shift+Right-Click for Settings").decoration(TextDecoration.ITALIC, false));
            }

            meta.lore(lore);
            meta.getPersistentDataContainer().set(PET_TYPE_KEY, PersistentDataType.STRING, pet.getPetType().name());
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getProgressBar(double current, double max, int totalBars, String symbol, String colorCompleted, String colorNotCompleted) {
        if (max <= 0) max = 1;
        float percent = (float) (current / max);
        if (percent > 1) percent = 1;
        int progressBars = (int) (totalBars * percent);
        StringBuilder sb = new StringBuilder();
        sb.append(colorCompleted);
        for (int i = 0; i < progressBars; i++) sb.append(symbol);
        sb.append(colorNotCompleted);
        for (int i = progressBars; i < totalBars; i++) sb.append(symbol);
        return sb.toString();
    }

    public void open() { player.openInventory(inventory); }
    public void open(Player p) { p.openInventory(inventory); }
    public PetMenuSort getSortMode() { return sortMode; }
    @NotNull @Override public Inventory getInventory() { return inventory; }
}