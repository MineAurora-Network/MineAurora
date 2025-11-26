package me.login.dungeon.gui;

import me.login.dungeon.manager.DungeonRewardManager;
import me.login.dungeon.utils.DungeonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DungeonGUI {

    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey("mineaurora", "dungeon_item_id");

    public static void openStartMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Dungeon Start"));

        ItemStack start = new ItemStack(Material.DARK_OAK_DOOR);
        ItemMeta meta = start.getItemMeta();
        meta.displayName(Component.text("Enter Dungeon", NamedTextColor.GREEN));
        start.setItemMeta(meta);

        inv.setItem(13, start);
        player.openInventory(inv);
    }

    public static void openRNGMeter(Player player, DungeonRewardManager manager) {
        Inventory inv = Bukkit.createInventory(null, 54, Component.text("RNG Meter Selection"));

        List<DungeonRewardManager.RewardItem> items = manager.getAllRewards();
        for (DungeonRewardManager.RewardItem item : items) {
            ItemStack icon = item.stack.clone();
            ItemMeta meta = icon.getItemMeta();
            List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
            lore.add(Component.text(" "));
            lore.add(Component.text("Click to Select", NamedTextColor.YELLOW));
            meta.lore(lore);

            // Store ID
            meta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, item.id);
            icon.setItemMeta(meta);

            inv.addItem(icon);
        }
        player.openInventory(inv);
    }

    public static void openRewardChest(Player player, List<ItemStack> rewards) {
        // 3 Rows (27 Slots)
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Dungeon Rewards"));

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        meta.displayName(Component.text(" "));
        glass.setItemMeta(meta);

        // Fill background
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, glass);
        }

        // Treasure Slots: 11, 13, 15 (Indices 10, 12, 14)
        // Logic to distribute rewards into these specific slots
        int[] slots = {10, 12, 14};

        for (int i = 0; i < rewards.size(); i++) {
            if (i >= slots.length) break; // Max 3 rewards shown in this layout
            inv.setItem(slots[i], rewards.get(i));
        }

        player.openInventory(inv);
    }
}