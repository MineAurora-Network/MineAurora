package me.login.pets;

import me.login.Login;
import me.login.utility.TextureToHead; // Your provided utility class
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
import java.util.stream.Collectors;

public class PetItemManager {

    private final Login plugin;
    private final Map<String, ItemStack> petItemCache = new HashMap<>();

    public PetItemManager(Login plugin) {
        this.plugin = plugin;
        loadPetItems();
    }

    public void loadPetItems() {
        petItemCache.clear();
        ConfigurationSection itemsConfig = plugin.getItems(); // Get items.yml
        if (itemsConfig == null) {
            plugin.getLogger().severe("Failed to load items.yml!");
            return;
        }

        loadItemsFromSection(itemsConfig, "pet_fruits");
        loadItemsFromSection(itemsConfig, "pet_capture");
        loadItemsFromSection(itemsConfig, "utility_items");
        // We will also load pet_armor from here
        loadItemsFromSection(itemsConfig, "pet_armor");
    }

    private void loadItemsFromSection(ConfigurationSection itemsConfig, String sectionName) {
        ConfigurationSection section = itemsConfig.getConfigurationSection(sectionName);
        if (section == null) {
            plugin.getLogger().warning("'" + sectionName + "' section not found in items.yml.");
            return;
        }

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemConfig = section.getConfigurationSection(key);
            if (itemConfig != null) {
                ItemStack item = buildItem(itemConfig);
                if (item != null) {
                    petItemCache.put(key, item);
                } else {
                    plugin.getLogger().warning("Failed to build item: " + sectionName + "." + key);
                }
            }
        }
    }

    private ItemStack buildItem(ConfigurationSection config) {
        try {
            String materialName = config.getString("material", "PAPER").toUpperCase();
            Material material = Material.matchMaterial(materialName);

            if (material == null) {
                plugin.getLogger().warning("Invalid material '" + materialName + "' for item: " + config.getCurrentPath());
                return null;
            }

            // --- FIXED LOGIC ---
            // 1. Create the ItemStack first.
            ItemStack item = new ItemStack(material);

            // 2. Get the texture string.
            String texture = config.getString("texture");

            // 3. Apply texture if it's a PLAYER_HEAD and texture exists
            //    This now matches your FiresaleItemManager logic.
            if (material == Material.PLAYER_HEAD && texture != null && !texture.isEmpty()) {
                item = TextureToHead.applyTexture(item, texture);
            }
            // --- END OF FIX ---

            ItemMeta meta = item.getItemMeta();
            if (meta == null) return null;

            // Set Name (No italics)
            String name = config.getString("name", "<red>Unknown Item");
            meta.displayName(MiniMessage.miniMessage().deserialize(name));

            // Set Lore (No italics)
            List<Component> lore = config.getStringList("lore").stream()
                    .map(line -> MiniMessage.miniMessage().deserialize(line))
                    .collect(Collectors.toList());
            meta.lore(lore);

            // Set NBT
            ConfigurationSection nbtConfig = config.getConfigurationSection("nbt");
            if (nbtConfig != null) {
                PersistentDataContainer pdc = meta.getPersistentDataContainer();

                if (nbtConfig.contains("plugin_key") && nbtConfig.contains("value")) {
                    // Single NBT (fruits, leads, utility)
                    NamespacedKey nbtKey = NamespacedKey.fromString(nbtConfig.getString("plugin_key"), plugin);
                    String nbtValue = nbtConfig.getString("value");
                    if (nbtKey != null) {
                        pdc.set(nbtKey, PersistentDataType.STRING, nbtValue);
                    }
                } else {
                    // Multiple NBT (pet armor)
                    for (String nbtKeyString : nbtConfig.getKeys(false)) {
                        String nbtValue = nbtConfig.getString(nbtKeyString);
                        NamespacedKey nbtKey = new NamespacedKey(plugin, nbtKeyString);
                        pdc.set(nbtKey, PersistentDataType.STRING, nbtValue);
                    }
                }
            }

            item.setItemMeta(meta);
            return item;

        } catch (Exception e) {
            plugin.getLogger().severe("Error building item from config: " + config.getCurrentPath());
            e.printStackTrace();
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