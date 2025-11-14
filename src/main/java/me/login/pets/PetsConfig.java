package me.login.pets;

import me.login.Login;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class PetsConfig {

    private final Login plugin;
    private FileConfiguration config;
    private FileConfiguration itemsConfig;

    private int petCooldownSeconds;
    private String renamePermission;
    private int maxNameLength;

    // Leveling
    private double baseXpReq;
    private double xpMultiplier;
    private double damageMultiplierPerLevel;
    private double healthMultiplierPerLevel;
    private int maxLevel;
    private Map<String, Double> fruitXpMap = new HashMap<>(); // Fruit Name -> XP

    private Map<EntityType, String> petTiers = new HashMap<>();
    private List<String> tierOrder;
    private Map<String, Map<String, Double>> captureChances = new HashMap<>();
    private Map<String, Double> petDamages = new HashMap<>();
    private Map<String, ItemStack> captureItems = new HashMap<>();
    private Map<String, ItemStack> fruitItems = new HashMap<>();
    private Map<String, ItemStack> utilityItems = new HashMap<>(); // --- NEW ---
    private Set<EntityType> allCapturablePets;

    // --- NEW KEYS ---
    private NamespacedKey fruitKey;
    private NamespacedKey utilityKey;

    public PetsConfig(Login plugin) {
        this.plugin = plugin;
        this.fruitKey = new NamespacedKey(plugin, "pet_fruit_id");
        this.utilityKey = new NamespacedKey(plugin, "pet_utility_id");
    }

    public void loadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        reloadItemsConfig();

        petCooldownSeconds = config.getInt("pet_capture.pet-cooldown-seconds", 300);
        renamePermission = config.getString("pet_capture.rename-permission", "mineaurora.pets.rename");
        maxNameLength = config.getInt("pet_capture.max-name-length", 20);

        // Leveling Settings
        baseXpReq = config.getDouble("pet_leveling.base-xp-req", 100.0);
        xpMultiplier = config.getDouble("pet_leveling.xp-multiplier", 1.5);
        damageMultiplierPerLevel = config.getDouble("pet_leveling.damage-multiplier", 0.5);
        healthMultiplierPerLevel = config.getDouble("pet_leveling.health-multiplier", 2.0);
        maxLevel = config.getInt("pet_leveling.max-level", 100);

        loadPetTiers();
        loadCaptureChances();
        loadPetDamages();
        loadCaptureItems();
        loadFruitItems();
        loadUtilityItems(); // --- NEW ---
    }

    public void reloadItemsConfig() {
        this.itemsConfig = plugin.getItems();
    }

    private void loadFruitItems() {
        fruitItems.clear();
        fruitXpMap.clear();
        if (itemsConfig == null) return;

        ConfigurationSection fruitSection = itemsConfig.getConfigurationSection("pet_fruits");
        if (fruitSection == null) return;

        for (String fruitName : fruitSection.getKeys(false)) {
            String path = "pet_fruits." + fruitName;
            ItemStack item = loadItemFromConfig(path, fruitName, fruitKey); // Use key
            if (item != null) {
                fruitItems.put(fruitName, item);
                fruitXpMap.put(fruitName, itemsConfig.getDouble(path + ".xp_give", 50.0));
            }
        }
    }

    // --- NEW ---
    private void loadUtilityItems() {
        utilityItems.clear();
        if (itemsConfig == null) return;

        ConfigurationSection utilitySection = itemsConfig.getConfigurationSection("utility_items");
        if (utilitySection == null) return;

        for (String itemName : utilitySection.getKeys(false)) {
            String path = "utility_items." + itemName;
            ItemStack item = loadItemFromConfig(path, itemName, utilityKey); // Use key
            if (item != null) {
                utilityItems.put(itemName, item);
            }
        }
    }

    // --- UPDATED to accept a key ---
    private ItemStack loadItemFromConfig(String path, String internalName, NamespacedKey nbtKey) {
        try {
            String mat = itemsConfig.getString(path + ".material");
            String name = itemsConfig.getString(path + ".name");
            List<String> lore = itemsConfig.getStringList(path + ".lore");

            if (mat == null || name == null) return null;

            ItemStack item = new ItemStack(Material.valueOf(mat.toUpperCase()));
            ItemMeta meta = item.getItemMeta();

            meta.getPersistentDataContainer().set(nbtKey, PersistentDataType.STRING, internalName);

            meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
            meta.lore(lore.stream().map(l -> MiniMessage.miniMessage().deserialize(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).collect(Collectors.toList()));
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load item: " + internalName);
            return null;
        }
    }

    private void loadCaptureItems() {
        captureItems.clear();
        if (itemsConfig == null) return;
        ConfigurationSection section = itemsConfig.getConfigurationSection("pet_capture");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            String path = "pet_capture." + key;
            String nbtKeyString = itemsConfig.getString(path + ".nbt.plugin_key");
            try {
                String mat = itemsConfig.getString(path + ".material");
                String name = itemsConfig.getString(path + ".name");
                List<String> lore = itemsConfig.getStringList(path + ".lore");

                if (mat == null || name == null || nbtKeyString == null) continue;

                ItemStack item = new ItemStack(Material.valueOf(mat.toUpperCase()));
                ItemMeta meta = item.getItemMeta();

                String[] parts = nbtKeyString.split(":");
                if(parts.length == 2) {
                    meta.getPersistentDataContainer().set(new NamespacedKey(parts[0], parts[1]), PersistentDataType.STRING, key);
                }

                meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
                meta.lore(lore.stream().map(l -> MiniMessage.miniMessage().deserialize(l).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).collect(Collectors.toList()));
                item.setItemMeta(meta);
                captureItems.put(key, item);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load capture item: " + key);
            }
        }
    }

    private void loadPetTiers() {
        petTiers.clear();
        tierOrder = config.getStringList("pet_capture.tiers.tier-order");
        ConfigurationSection tiersSection = config.getConfigurationSection("pet_capture.tiers");
        if (tiersSection == null) return;
        for (String tier : tierOrder) {
            List<String> mobNames = tiersSection.getStringList(tier);
            for (String mobName : mobNames) {
                try {
                    petTiers.put(EntityType.valueOf(mobName.toUpperCase()), tier);
                } catch (Exception e) {}
            }
        }
        allCapturablePets = Collections.unmodifiableSet(petTiers.keySet());
    }

    private void loadCaptureChances() {
        captureChances.clear();
        ConfigurationSection chancesSection = config.getConfigurationSection("pet_capture.chances");
        if (chancesSection == null) return;
        for (String tier : chancesSection.getKeys(false)) {
            ConfigurationSection tierChances = chancesSection.getConfigurationSection(tier);
            if (tierChances == null) continue;
            Map<String, Double> itemChances = new HashMap<>();
            for (String itemName : tierChances.getKeys(false)) {
                itemChances.put(itemName, tierChances.getDouble(itemName));
            }
            captureChances.put(tier, itemChances);
        }
    }

    private void loadPetDamages() {
        petDamages.clear();
        ConfigurationSection damageSection = config.getConfigurationSection("pet_capture.damage");
        if (damageSection == null) return;
        for (String tier : damageSection.getKeys(false)) {
            petDamages.put(tier, damageSection.getDouble(tier));
        }
    }

    public boolean isCapturable(EntityType type) { return petTiers.containsKey(type); }

    public double getCaptureChance(EntityType petType, String itemName) {
        String tier = petTiers.get(petType);
        if (tier == null || itemName == null) return 0.0;
        if (captureChances.containsKey(tier)) {
            return captureChances.get(tier).getOrDefault(itemName, 0.0);
        }
        return 0.0;
    }

    public String getCaptureItemName(String nbtValue) {
        if (captureItems.containsKey(nbtValue)) return nbtValue;
        return null;
    }

    public double getXpRequired(int level) {
        return baseXpReq * Math.pow(xpMultiplier, level - 1);
    }

    public double getDamage(EntityType type, int level) {
        String tier = petTiers.get(type);
        double base = petDamages.getOrDefault(tier, 1.0);
        // --- ADDED: Ensure passive mobs have a damage value in config! ---
        if (base <= 0) base = 1.0; // Default to 1 if not set
        return base + ((level - 1) * damageMultiplierPerLevel);
    }

    public double getHealthBonus(int level) {
        return (level - 1) * healthMultiplierPerLevel;
    }

    public int getMaxLevel() { return maxLevel; }

    public ItemStack getFruit(String fruitName) {
        return fruitItems.get(fruitName) == null ? null : fruitItems.get(fruitName).clone();
    }
    public Set<String> getFruitNames() { return fruitItems.keySet(); }
    public double getFruitXp(String fruitName) {
        return fruitXpMap.getOrDefault(fruitName, 0.0);
    }
    public ItemStack getCaptureItem(String itemName) {
        return captureItems.get(itemName) != null ? captureItems.get(itemName).clone() : null;
    }
    public Set<String> getCaptureItemNames() {
        return captureItems.keySet();
    }

    // --- NEW Utility Item Methods ---
    public ItemStack getUtilityItem(String itemName) {
        return utilityItems.get(itemName) != null ? utilityItems.get(itemName).clone() : null;
    }
    public Set<String> getUtilityItemNames() {
        return utilityItems.keySet();
    }
    public String getUtilityId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(utilityKey, PersistentDataType.STRING);
    }

    public Set<EntityType> getAllCapturablePetTypes() {
        return allCapturablePets;
    }
    public int getPetCooldownSeconds() { return petCooldownSeconds; }
    public String getRenamePermission() { return renamePermission; }
    public int getMaxNameLength() { return maxNameLength; }
    public List<String> getTierOrder() { return tierOrder; }
    public String getPetTier(EntityType type) { return petTiers.getOrDefault(type, "unknown"); }
}