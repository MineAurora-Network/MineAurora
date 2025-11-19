package me.login.pets;

import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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
import java.util.stream.Collectors;

public class PetItemManager {

    private final Login plugin;
    private final Map<String, ItemStack> petItemCache = new HashMap<>();
    private final NamespacedKey fruitXpKey;

    public PetItemManager(Login plugin) {
        this.plugin = plugin;
        this.fruitXpKey = new NamespacedKey("mineaurora", "fruit_xp_amount");
        loadPetItems();
    }

    public void loadPetItems() {
        petItemCache.clear();
        ConfigurationSection itemsConfig = plugin.getItems();
        if (itemsConfig == null) {
            plugin.getLogger().severe("Failed to load items.yml!");
            return;
        }

        loadItemsFromSection(itemsConfig, "pet_fruits");
        loadItemsFromSection(itemsConfig, "pet_capture");
        loadItemsFromSection(itemsConfig, "utility_items");
        loadItemsFromSection(itemsConfig, "pet_armor");
        loadItemsFromSection(itemsConfig, "pet_attributes");

        plugin.getLogger().info("[PetItemManager] Total items loaded: " + petItemCache.size());
    }

    private void loadItemsFromSection(ConfigurationSection itemsConfig, String sectionName) {
        ConfigurationSection section = itemsConfig.getConfigurationSection(sectionName);
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemConfig = section.getConfigurationSection(key);
            if (itemConfig != null) {
                ItemStack item = buildItem(itemConfig);
                if (item != null) {
                    petItemCache.put(key, item);
                }
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection config) {
        try {
            String materialName = config.getString("material", "PAPER").toUpperCase();
            Material material = Material.matchMaterial(materialName);

            if (material == null) return null;

            ItemStack item = new ItemStack(material);
            String texture = config.getString("texture");

            if (material == Material.PLAYER_HEAD && texture != null && !texture.isEmpty()) {
                item = TextureToHead.applyTexture(item, texture);
            }

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;

            String name = config.getString("name", "<red>Unknown Item");
            meta.displayName(MiniMessage.miniMessage().deserialize(name)
                    .decoration(TextDecoration.ITALIC, false));

            List<Component> lore = config.getStringList("lore").stream()
                    .map(line -> MiniMessage.miniMessage().deserialize(line)
                            .decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList());
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();

            // --- NEW: Handle xp_give specially ---
            // If items.yml has "xp_give: 50", we save it to NBT so PetManager knows this fruit gives XP.
            if (config.contains("xp_give")) {
                double xpAmount = config.getDouble("xp_give");
                pdc.set(fruitXpKey, PersistentDataType.DOUBLE, xpAmount);
            }

            // Handle generic NBT section
            ConfigurationSection nbtConfig = config.getConfigurationSection("nbt");
            if (nbtConfig != null) {
                if (nbtConfig.contains("plugin_key") && nbtConfig.contains("value")) {
                    NamespacedKey nbtKey = NamespacedKey.fromString(nbtConfig.getString("plugin_key"), plugin);
                    String nbtValue = nbtConfig.getString("value");
                    if (nbtKey != null) {
                        pdc.set(nbtKey, PersistentDataType.STRING, nbtValue);
                    }
                } else {
                    for (String nbtKeyString : nbtConfig.getKeys(false)) {
                        String nbtValue = nbtConfig.getString(nbtKeyString);
                        // Check if it already has namespace
                        NamespacedKey nbtKey;
                        if (nbtKeyString.contains(":")) {
                            nbtKey = NamespacedKey.fromString(nbtKeyString);
                        } else {
                            nbtKey = new NamespacedKey(plugin, nbtKeyString);
                        }
                        if (nbtKey != null) pdc.set(nbtKey, PersistentDataType.STRING, nbtValue);
                    }
                }
            }

            item.setItemMeta(meta);
            return item;

        } catch (Exception e) {
            plugin.getLogger().severe("Error building item from config: " + config.getCurrentPath());
            return null;
        }
    }

    public ItemStack getItem(String key) {
        ItemStack item = petItemCache.get(key);
        return item != null ? item.clone() : null;
    }

    public List<String> getItemKeys() {
        return new ArrayList<>(petItemCache.keySet());
    }
}