package me.login.misc.firesale.model;

import org.bukkit.inventory.ItemStack;

/**
 * Represents an item template loaded from items.yml.
 */
public class FiresaleItem {

    private final String id;
    private final ItemStack itemStack;

    public FiresaleItem(String id, ItemStack itemStack) {
        this.id = id;
        this.itemStack = itemStack;
    }

    public String getId() {
        return id;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }
}