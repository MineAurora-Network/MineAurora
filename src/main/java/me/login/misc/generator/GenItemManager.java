package me.login.misc.generator;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GenItemManager {

    private final Login plugin;
    private final MiniMessage miniMessage;

    // Maps tierID -> Generator Info (stats + itemstack)
    private final Map<String, GenInfo> generatorMap = new HashMap<>();
    // Maps tierID -> Drop Item Stack
    private final Map<String, ItemStack> dropMap = new HashMap<>();

    public static final NamespacedKey GEN_TIER_KEY = new NamespacedKey("mineaurora", "gen_tier");
    public static final NamespacedKey GEN_DROP_VALUE_KEY = new NamespacedKey("mineaurora", "gen_drop_value");

    public GenItemManager(Login plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadItems();
    }

    public void loadItems() {
        generatorMap.clear();
        dropMap.clear();

        ConfigurationSection genSection = plugin.getItems().getConfigurationSection("generators");
        if (genSection == null) return;

        for (String key : genSection.getKeys(false)) {
            ConfigurationSection section = genSection.getConfigurationSection(key);
            if (section == null) continue;

            // --- Load Generator Stats ---
            GenInfo info = new GenInfo();
            info.id = key;
            info.tier = section.getInt("stats.tier");
            info.speed = section.getInt("stats.speed", 20);
            info.price = section.getDouble("stats.price");
            info.upgradeCost = section.getDouble("stats.upgrade_cost");
            info.itemValue = section.getDouble("stats.item_value");
            info.nextGenId = section.getString("stats.next_gen");

            // --- Load Generator Item ---
            String matName = section.getString("item.material", "STONE");
            Material mat = Material.getMaterial(matName);
            if (mat == null) mat = Material.STONE;

            ItemStack genItem = new ItemStack(mat);
            ItemMeta meta = genItem.getItemMeta();
            String name = section.getString("item.name", "Generator");
            meta.displayName(miniMessage.deserialize(name));

            List<Component> lore = new ArrayList<>();
            for (String line : section.getStringList("item.lore")) {
                lore.add(miniMessage.deserialize(line));
            }
            meta.lore(lore);

            // Store Tier ID in PDC for safe identification
            meta.getPersistentDataContainer().set(GEN_TIER_KEY, PersistentDataType.STRING, key);
            genItem.setItemMeta(meta);

            info.genItem = genItem;
            info.displayName = name;

            generatorMap.put(key, info);

            // --- Load Drop Item ---
            // Drop items are defined in the same section for simplicity based on user prompt
            String dropMatName = section.getString("stats.spawn_item_mat", "WHEAT"); // Fallback
            Material dropMat = Material.getMaterial(dropMatName);
            if (dropMat == null) dropMat = Material.BEDROCK; // Error item

            ItemStack dropItem = new ItemStack(dropMat);
            ItemMeta dropMeta = dropItem.getItemMeta();
            String dropName = section.getString("drop.name", "Drop");
            dropMeta.displayName(miniMessage.deserialize(dropName));

            List<Component> dropLore = new ArrayList<>();
            for (String line : section.getStringList("drop.lore")) {
                dropLore.add(miniMessage.deserialize(line));
            }
            dropMeta.lore(dropLore);

            // Store Value in PDC for /sellgendrop
            dropMeta.getPersistentDataContainer().set(GEN_DROP_VALUE_KEY, PersistentDataType.DOUBLE, info.itemValue);
            dropItem.setItemMeta(dropMeta);

            dropMap.put(key, dropItem);
        }
    }

    public ItemStack getGeneratorItem(String tierId) {
        if (!generatorMap.containsKey(tierId)) return null;
        return generatorMap.get(tierId).genItem.clone();
    }

    public ItemStack getDropItem(String tierId) {
        if (!dropMap.containsKey(tierId)) return null;
        return dropMap.get(tierId).clone();
    }

    public GenInfo getGenInfo(String tierId) {
        return generatorMap.get(tierId);
    }

    public String getTierFromItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(GEN_TIER_KEY, PersistentDataType.STRING);
    }

    public Double getDropValue(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(GEN_DROP_VALUE_KEY, PersistentDataType.DOUBLE);
    }

    public static class GenInfo {
        public String id;
        public int tier;
        public int speed; // Ticks
        public double price;
        public double upgradeCost;
        public double itemValue;
        public String nextGenId;
        public ItemStack genItem;
        public String displayName;
    }
}