package me.login.misc.tokens;

// --- NO MORE 'authlib' or 'GameProfile' IMPORTS! ---
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
// --- REMOVED SkullMeta ---

import java.io.File;
// --- REMOVED Field, UUID ---
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads and constructs custom items from the items.yml file.
 * This is now simplified and no longer supports custom player heads.
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

    /**
     * Gets a fully constructed ItemStack from the token-shop section in items.yml.
     * @param key The item key (e.g., "sell-wand-1.5")
     * @return The constructed ItemStack, or null if not found.
     */
    public ItemStack getItem(String key) {
        ConfigurationSection section = itemConfig.getConfigurationSection("token-shop." + key);
        if (section == null) {
            plugin.getLogger().warning("Failed to load item from items.yml: token-shop." + key + " not found!");
            return null;
        }

        String materialName = section.getString("material", "STONE").toUpperCase();
        Material material = Material.getMaterial(materialName);
        if (material == null) {
            plugin.getLogger().warning("Invalid material '" + materialName + "' for item " + key);
            material = Material.STONE;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item; // Should not happen with valid materials
        }

        // Name
        String nameString = section.getString("name", "<red>Unnamed Item</red>");
        meta.displayName(mm.deserialize(nameString).decoration(TextDecoration.ITALIC, false));

        // Lore
        List<String> loreStrings = section.getStringList("lore");
        if (loreStrings != null && !loreStrings.isEmpty()) {
            List<Component> lore = loreStrings.stream()
                    .map(line -> mm.deserialize(line).decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList());
            meta.lore(lore);
        }

        // --- REMOVED Player Head / Texture section ---
        // Since no items in token-shop use PLAYER_HEAD, this is no longer needed.
        if (material == Material.PLAYER_HEAD) {
            plugin.getLogger().warning("Item '" + key + "' is a PLAYER_HEAD, but custom textures are no longer supported in this manager.");
        }

        // Flags
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

        item.setItemMeta(meta);
        return item;
    }
}