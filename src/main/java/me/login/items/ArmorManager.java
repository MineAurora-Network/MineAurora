package me.login.items;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ArmorManager {

    private final Login plugin;
    private final Map<String, ConfigurationSection> armorConfigMap = new HashMap<>();

    public static final NamespacedKey MAX_DURABILITY_KEY = new NamespacedKey("mineaurora", "custom_armor_max_dura");
    public static final NamespacedKey CURRENT_DURABILITY_KEY = new NamespacedKey("mineaurora", "custom_armor_current_dura");
    public static final NamespacedKey CUSTOM_ID_KEY = new NamespacedKey("mineaurora", "custom_armor_id");

    public ArmorManager(Login plugin) {
        this.plugin = plugin;
        loadArmor();
    }

    public void loadArmor() {
        armorConfigMap.clear();
        ConfigurationSection section = plugin.getItems().getConfigurationSection("custom_armor");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                armorConfigMap.put(key, section.getConfigurationSection(key));
            }
        }
    }

    public ItemStack getArmorPiece(String key) {
        if (!armorConfigMap.containsKey(key)) return null;

        ConfigurationSection config = armorConfigMap.get(key);
        Material material = Material.getMaterial(config.getString("material", "LEATHER_HELMET"));
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        // Remove Italics from Name
        String name = config.getString("name", "<red>Unknown Armor");
        meta.displayName(MiniMessage.miniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));

        if (meta instanceof LeatherArmorMeta && config.contains("color")) {
            String hex = config.getString("color");
            if (hex != null && hex.startsWith("#")) {
                try {
                    int r = Integer.valueOf(hex.substring(1, 3), 16);
                    int g = Integer.valueOf(hex.substring(3, 5), 16);
                    int b = Integer.valueOf(hex.substring(5, 7), 16);
                    ((LeatherArmorMeta) meta).setColor(Color.fromRGB(r, g, b));
                } catch (Exception ignored) {}
            }
        }

        int maxDurability = config.getInt("max-durability", 100);
        List<String> loreList = config.getStringList("lore");
        List<Component> finalLore = new ArrayList<>();

        for (String line : loreList) {
            String processed = line.replace("%current%", String.valueOf(maxDurability))
                    .replace("%max%", String.valueOf(maxDurability));
            // Remove Italics from Lore
            finalLore.add(MiniMessage.miniMessage().deserialize(processed).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(finalLore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(CUSTOM_ID_KEY, PersistentDataType.STRING, key);
        pdc.set(MAX_DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);
        pdc.set(CURRENT_DURABILITY_KEY, PersistentDataType.INTEGER, maxDurability);

        if (config.contains("nbt")) {
            ConfigurationSection nbtSection = config.getConfigurationSection("nbt");
            if (nbtSection != null) {
                for (String nbtKey : nbtSection.getKeys(false)) {
                    String nbtValue = nbtSection.getString(nbtKey);
                    pdc.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.STRING, nbtValue);
                }
            }
        }

        // Hide Flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE, ItemFlag.HIDE_UNBREAKABLE);
        meta.setUnbreakable(true);

        item.setItemMeta(meta);
        return item;
    }

    public boolean exists(String key) {
        return armorConfigMap.containsKey(key);
    }

    public Set<String> getArmorNames() {
        return armorConfigMap.keySet();
    }

    public boolean isCustomArmor(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(CUSTOM_ID_KEY, PersistentDataType.STRING);
    }
}