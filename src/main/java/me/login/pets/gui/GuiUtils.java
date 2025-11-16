package me.login.pets.gui;

import me.login.Login;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for creating standardized GUI items.
 */
public class GuiUtils {

    // --- UPDATED: Made non-static ---
    private static NamespacedKey GUI_ITEM_KEY;

    // --- UPDATED: Initialize the key with the plugin instance ---
    private static NamespacedKey initializeKey(Login plugin) {
        if (GUI_ITEM_KEY == null) {
            GUI_ITEM_KEY = new NamespacedKey(plugin, "pet-gui-item");
        }
        return GUI_ITEM_KEY;
    }

    /**
     * Creates a simple GUI item (like a button or filler pane) with an NBT tag.
     * @param plugin The main plugin instance.
     * @param material The material of the item.
     * @param miniMessageName The MiniMessage formatted name.
     * @param miniMessageLore The MiniMessage formatted lore.
     * @return The created ItemStack.
     */
    public static ItemStack createGuiItem(Login plugin, Material material, String miniMessageName, List<String> miniMessageLore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // Set name
            meta.displayName(
                    MiniMessage.miniMessage().deserialize(miniMessageName)
                            .decoration(TextDecoration.ITALIC, false)
            );

            // Set lore
            meta.lore(
                    miniMessageLore.stream()
                            .map(line -> MiniMessage.miniMessage().deserialize(line)
                                    .decoration(TextDecoration.ITALIC, false))
                            .collect(Collectors.toList())
            );

            // Add NBT tag
            PersistentDataContainer data = meta.getPersistentDataContainer();
            data.set(initializeKey(plugin), PersistentDataType.BYTE, (byte) 1);

            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Checks if an ItemStack is a GUI item created by this utility.
     * @param plugin The main plugin instance.
     * @param item The item to check.
     * @return true if it is a GUI item, false otherwise.
     */
    public static boolean isGuiItem(Login plugin, ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(initializeKey(plugin), PersistentDataType.BYTE);
    }

    /**
     * Gets the NamespacedKey used for GUI items.
     * @param plugin The main plugin instance.
     * @return The key.
     */
    public static NamespacedKey getGuiItemKey(Login plugin) {
        return initializeKey(plugin);
    }
}