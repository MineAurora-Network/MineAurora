package me.login.pets;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Handles loading and providing access to all pet-related configurations
 * from config.yml and items.yml.
 */
public class PetsConfig {

    private final Login plugin;
    private FileConfiguration config;
    private FileConfiguration itemsConfig; // --- FIXED: Added ---

    // Config values
    private int petCooldownSeconds;
    private String renamePermission;
    private int maxNameLength;

    private Map<EntityType, String> petTiers = new HashMap<>();
    private List<String> tierOrder;
    private Map<String, Map<String, Double>> captureChances = new HashMap<>();
    private Map<String, Double> petDamages = new HashMap<>();
    private Map<String, ItemStack> captureItems = new HashMap<>();
    private Set<EntityType> allCapturablePets;

    public PetsConfig(Login plugin) {
        this.plugin = plugin;
    }

    // --- FIXED: Added loadConfig() ---
    public void loadConfig() {
        plugin.reloadConfig();
        this.config = plugin.getConfig();
        reloadItemsConfig(); // Load items as well

        petCooldownSeconds = config.getInt("pet_capture.pet-cooldown-seconds", 300);
        renamePermission = config.getString("pet_capture.rename-permission", "mineaurora.pets.rename");
        maxNameLength = config.getInt("pet_capture.max-name-length", 20);

        loadPetTiers();
        loadCaptureChances();
        loadPetDamages();
        loadCaptureItems();
    }

    // --- FIXED: Added reloadItemsConfig() ---
    public void reloadItemsConfig() {
        plugin.reloadItems();
        this.itemsConfig = plugin.getItems();
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
                    EntityType type = EntityType.valueOf(mobName.toUpperCase());
                    petTiers.put(type, tier);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("[PetsConfig] Invalid mob type in tier '" + tier + "': " + mobName);
                }
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

    private void loadCaptureItems() {
        captureItems.clear();
        if (itemsConfig == null) {
            plugin.getLogger().severe("[PetsConfig] items.yml is not loaded! Cannot load capture items.");
            return;
        }

        ConfigurationSection itemsSection = itemsConfig.getConfigurationSection("pet_capture");
        if (itemsSection == null) {
            plugin.getLogger().warning("[PetsConfig] No 'pet_capture' section found in items.yml. No capture items will work.");
            return;
        }

        for (String itemName : itemsSection.getKeys(false)) {
            String path = "pet_capture." + itemName;
            String materialName = itemsSection.getString(path + ".material");
            String displayName = itemsSection.getString(path + ".name");
            List<String> lore = itemsSection.getStringList(path + ".lore");
            String nbtKey = itemsSection.getString(path + ".nbt.plugin_key"); // e.g., "mineaurora:pet_lead_1"

            if (materialName == null || displayName == null || nbtKey == null) {
                plugin.getLogger().warning("[PetsConfig] Skipping item '" + itemName + "' due to missing material, name, or nbt.plugin_key.");
                continue;
            }

            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                ItemStack item = new ItemStack(material);
                ItemMeta meta = item.getItemMeta();

                // Apply NBT tag
                String[] keyParts = nbtKey.split(":");
                if (keyParts.length != 2) {
                    plugin.getLogger().warning("[PetsConfig] Skipping item '" + itemName + "'. Invalid NBT key format. Must be 'namespace:key'.");
                    continue;
                }
                NamespacedKey key = new NamespacedKey(keyParts[0], keyParts[1]);
                meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, itemName); // Store internal name

                // Apply name
                meta.displayName(MiniMessage.miniMessage().deserialize(displayName).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));

                // Apply lore
                meta.lore(lore.stream()
                        .map(line -> MiniMessage.miniMessage().deserialize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false))
                        .collect(Collectors.toList()));

                item.setItemMeta(meta);
                captureItems.put(itemName, item);

            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("[PetsConfig] Skipping item '" + itemName + "'. Invalid material: " + materialName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "[PetsConfig] Error loading item '" + itemName + "'", e);
            }
        }
    }


    public boolean isCapturable(EntityType type) {
        return petTiers.containsKey(type);
    }

    public double getCaptureChance(EntityType petType, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0.0;

        String tier = petTiers.get(petType);
        if (tier == null) return 0.0; // Not a capturable pet

        PersistentDataContainer data = item.getItemMeta().getPersistentDataContainer();
        for (NamespacedKey key : data.getKeys()) {
            if (key.getNamespace().equals("mineaurora") && key.getKey().startsWith("pet_lead_")) {
                String itemName = data.get(key, PersistentDataType.STRING);
                if (itemName != null && captureChances.containsKey(tier)) {
                    return captureChances.get(tier).getOrDefault(itemName, 0.0);
                }
            }
        }
        return 0.0;
    }

    public double getPetDamage(EntityType petType) {
        String tier = petTiers.get(petType);
        if (tier == null) return 1.0; // Default damage
        return petDamages.getOrDefault(tier, 1.0);
    }

    public int getPetCooldownSeconds() {
        return petCooldownSeconds;
    }

    public String getRenamePermission() {
        return renamePermission;
    }

    public int getMaxNameLength() {
        return maxNameLength;
    }

    public Set<EntityType> getAllCapturablePetTypes() {
        return allCapturablePets;
    }

    // --- FIXED: Added missing getter ---
    public List<String> getTierOrder() {
        return tierOrder;
    }

    // --- FIXED: Added missing getter ---
    public String getPetTier(EntityType type) {
        return petTiers.getOrDefault(type, "unknown");
    }

    // --- FIXED: Added missing getter ---
    public ItemStack getCaptureItem(String itemName) {
        return captureItems.get(itemName) != null ? captureItems.get(itemName).clone() : null;
    }

    // --- FIXED: Added missing getter ---
    public Set<String> getCaptureItemNames() {
        return captureItems.keySet();
    }
}