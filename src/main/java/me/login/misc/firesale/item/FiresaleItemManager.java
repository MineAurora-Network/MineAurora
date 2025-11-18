package me.login.misc.firesale.item;

import me.login.Login;
import me.login.misc.firesale.model.FiresaleItem;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.ArrayList;
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

                    // --- FIX for HOLOGRAM: Load name AS-IS (no decoration) ---
                    // This prevents the <gold>God Apple<!italic> issue.
                    meta.displayName(miniMessage.deserialize(name));

                    // --- LORE HANDLING ---
                    List<String> loreLines = itemSection.getStringList("lore");
                    List<Component> lore = new ArrayList<>();

                    if (!loreLines.isEmpty()) {
                        lore = loreLines.stream()
                                // --- FIX for GUI: Force lore to be non-italic ---
                                // This fixes the <yellow>Very shiny. issue in your screenshot.
                                .map(line -> miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false))
                                .collect(Collectors.toCollection(ArrayList::new));
                    }

                    // --- NEW FUNCTIONALITY: NBT & HEX LOGIC ADDED HERE ---
                    if (itemSection.contains("nbt")) {
                        ConfigurationSection nbtSection = itemSection.getConfigurationSection("nbt");
                        PersistentDataContainer pdc = meta.getPersistentDataContainer();

                        if (nbtSection != null) {
                            for (String nbtKey : nbtSection.getKeys(false)) {
                                String value = nbtSection.getString(nbtKey);
                                if (value != null) {
                                    // 1. Set the NBT data so the Anvil can read it
                                    pdc.set(new NamespacedKey(plugin, nbtKey), PersistentDataType.STRING, value);

                                    // 2. Special Logic: If this is a dye_hex, add it to the lore visually
                                    if (nbtKey.equalsIgnoreCase("dye_hex")) {
                                        lore.add(Component.empty());
                                        // --- FIX for GUI: Ensure this line is also non-italic ---
                                        lore.add(miniMessage.deserialize("<gray>Hex: <white>" + value).decoration(TextDecoration.ITALIC, false));
                                    }
                                }
                            }
                        }
                    }
                    // -----------------------------------------------------

                    meta.lore(lore);
                    // --- FIX: Removed the non-existent HIDE_ITALIC flag ---
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

    // --- RESTORED METHODS (Fixes Compilation Errors) ---

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
        if (item == null) return "Unknown";
        return item.getType().toString().toLowerCase().replace("_", " ");
    }
}