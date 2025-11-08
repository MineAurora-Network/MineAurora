package me.login.misc.playtimerewards;

// Removed all com.mojang.authlib imports
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility class to build custom items.
 * This is a simplified builder for use in the GUI.
 */
public class ItemBuilder {

    private final ItemStack item;
    private final ItemMeta meta;

    public ItemBuilder(Material material) {
        this.item = new ItemStack(material);
        this.meta = item.getItemMeta();
    }

    public ItemBuilder displayName(Component name) {
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        return this;
    }

    public ItemBuilder lore(List<Component> lore) {
        List<Component> finalLore = new ArrayList<>();
        for (Component line : lore) {
            finalLore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(finalLore);
        return this;
    }

    public ItemBuilder lore(Component... loreLines) {
        return lore(Arrays.asList(loreLines));
    }

    // Removed texture() method

    // Removed owner() method

    public ItemBuilder glow() {
        meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, false);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        return this;
    }

    public <T, Z> ItemBuilder pdc(NamespacedKey key, PersistentDataType<T, Z> type, Z value) {
        meta.getPersistentDataContainer().set(key, type, value);
        return this;
    }

    public ItemStack build() {
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}