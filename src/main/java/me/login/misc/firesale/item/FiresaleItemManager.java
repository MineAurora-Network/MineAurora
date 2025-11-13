package me.login.misc.firesale.item;

import me.login.Login;
import me.login.misc.firesale.model.FiresaleItem;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FiresaleItemManager {

    private final Login plugin;
    private final MiniMessage miniMessage;
    private final HashMap<String, FiresaleItem> items = new HashMap<>();
    private FileConfiguration itemsConfig;

    public FiresaleItemManager(Login plugin) {
        this.plugin = plugin;
        this.miniMessage = plugin.getComponentSerializer();
        loadItemsConfig();
        // Auto-load on instantiation
        loadItems();
    }

    private void loadItemsConfig() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    public void loadItems() {
        items.clear();
        // FIX: Reload config from disk to ensure fresh edits are read
        loadItemsConfig();

        ConfigurationSection firesaleSection = itemsConfig.getConfigurationSection("firesale");
        if (firesaleSection == null) {
            plugin.getLogger().warning("No 'firesale' section found in items.yml!");
            return;
        }

        for (String key : firesaleSection.getKeys(false)) {
            ConfigurationSection itemSection = firesaleSection.getConfigurationSection(key);
            if (itemSection == null) continue;

            try {
                String materialName = itemSection.getString("material", "STONE").toUpperCase();
                Material material = Material.matchMaterial(materialName);
                if (material == null) {
                    plugin.getLogger().warning("Invalid material '" + materialName + "' for firesale item '" + key + "'.");
                    continue;
                }

                ItemStack item = new ItemStack(material);
                String texture = itemSection.getString("texture");

                if (material == Material.PLAYER_HEAD && texture != null && !texture.isEmpty()) {
                    // Ensure TextureToHead util handles valid URLs
                    item = TextureToHead.applyTexture(item, texture);
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String name = itemSection.getString("name", key);
                    meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));

                    List<String> loreLines = itemSection.getStringList("lore");
                    if (!loreLines.isEmpty()) {
                        List<Component> lore = loreLines.stream()
                                .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                                .collect(Collectors.toList());
                        meta.lore(lore);
                    }

                    meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
                    item.setItemMeta(meta);
                }

                items.put(key.toLowerCase(), new FiresaleItem(key, item));

            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load firesale item '" + key + "': " + e.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + items.size() + " firesale items from items.yml.");
    }

    public FiresaleItem getItem(String id) {
        if (id == null) return null;
        return items.get(id.toLowerCase());
    }

    public Set<String> getAllItemIds() {
        return items.keySet();
    }

    public String getItemName(ItemStack item) {
        if (item != null && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            return miniMessage.serialize(item.getItemMeta().displayName());
        }
        return item.getType().toString().toLowerCase().replace("_", " ");
    }
}