package me.login.misc.tokens;

import me.login.Login;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads items from items.yml
 */
public class ItemManager {

    private final Login plugin;
    private final FileConfiguration itemConfig;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ItemManager(Login plugin) {
        this.plugin = plugin;
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.saveResource("items.yml", false);
        }
        this.itemConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    // Get item stack
    public ItemStack getItem(String key) {
        ConfigurationSection section = itemConfig.getConfigurationSection("token-shop." + key);
        if (section == null) {
            plugin.getLogger().warning("Item " + key + " not found in items.yml under token-shop.");
            return null;
        }

        String matName = section.getString("material", "STONE");
        Material material = Material.getMaterial(matName);
        if (material == null) material = Material.STONE;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name", "Unnamed");
            meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));

            List<String> lore = section.getStringList("lore");
            if (lore != null) {
                meta.lore(lore.stream()
                        .map(l -> mm.deserialize(l).decoration(TextDecoration.ITALIC, false))
                        .collect(Collectors.toList()));
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    // Helper to get all shop keys
    public Set<String> getShopKeys() {
        ConfigurationSection section = itemConfig.getConfigurationSection("token-shop");
        if (section == null) return Collections.emptySet();
        return section.getKeys(false);
    }

    // Helper to get price
    public long getPrice(String key) {
        return itemConfig.getLong("token-shop." + key + ".price", 0);
    }
}