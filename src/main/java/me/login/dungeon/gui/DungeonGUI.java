package me.login.dungeon.gui;

import me.login.Login;
import me.login.dungeon.manager.DungeonRewardManager;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class DungeonGUI {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    // Static key for item ID storage
    public static final NamespacedKey ITEM_ID_KEY = new NamespacedKey(Login.getPlugin(Login.class), "dungeon_item_id");

    public static void openStartMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Dungeon Start", NamedTextColor.GRAY));
        fillGray(inv);

        String url = "http://textures.minecraft.net/texture/872ec5171c97d7e81ff090cab63c7233f1a4a563780766f8c63908b6f0a6b88c";
        ItemStack icon = TextureToHead.getHead(url);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(mm.deserialize("<gradient:#00aaff:#00ffaa>Start Dungeon Run</gradient>").decoration(TextDecoration.ITALIC, false));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Click to enter an empty dungeon.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        icon.setItemMeta(meta);

        inv.setItem(13, icon);
        player.openInventory(inv);
    }

    public static void openRNGMeter(Player player, DungeonRewardManager rewardManager) {
        Inventory inv = Bukkit.createInventory(null, 36, Component.text("RNG Meter", NamedTextColor.GRAY));
        fillGrayRow(inv, 0);
        fillGrayRow(inv, 3);

        DungeonRewardManager.PlayerStats stats = rewardManager.getPlayerStats(player.getUniqueId());

        String url = "http://textures.minecraft.net/texture/1500ba86771f29706ae73c621a2f9addba3c8278fca2d1da317a30f3482815e4";
        ItemStack head = TextureToHead.getHead(url);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(Component.text("RNG Meter", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        String selectedName = (stats.selected_drop == null) ? "None" : stats.selected_drop;
        lore.add(Component.text("Selected Drop: ", NamedTextColor.GRAY).append(Component.text(selectedName, NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("RNG Meter Progress:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));

        int progress = stats.runs % 50;
        String bar = "||||||||||||||||||||";
        int filled = (int) ((progress / 50.0) * 20);
        String pBar = "<green>" + bar.substring(0, filled) + "<gray>" + bar.substring(filled);
        lore.add(mm.deserialize(pBar).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("  " + stats.runs + " / 50 Runs", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        head.setItemMeta(meta);
        inv.setItem(4, head);

        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25};
        List<DungeonRewardManager.RewardItem> allRewards = rewardManager.getAllRewards();

        for (int i = 0; i < slots.length && i < allRewards.size(); i++) {
            DungeonRewardManager.RewardItem item = allRewards.get(i);
            ItemStack display = item.stack.clone();
            ItemMeta dMeta = display.getItemMeta();

            List<Component> dLore = (dMeta.hasLore()) ? dMeta.lore() : new ArrayList<>();
            dLore.add(Component.empty());
            dLore.add(Component.text("Rarity: ", NamedTextColor.GRAY).append(Component.text(item.rarity.toUpperCase(), NamedTextColor.LIGHT_PURPLE)).decoration(TextDecoration.ITALIC, false));
            dLore.add(Component.text("Click to select!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            dMeta.lore(dLore);

            // Fix: Use PersistentDataContainer instead of LocalizedName
            dMeta.getPersistentDataContainer().set(ITEM_ID_KEY, PersistentDataType.STRING, item.id);

            display.setItemMeta(dMeta);
            inv.setItem(slots[i], display);
        }

        player.openInventory(inv);
    }

    public static void openRewardChest(Player player, List<ItemStack> rewards) {
        Inventory inv = Bukkit.createInventory(null, 27, Component.text("Dungeon Rewards", NamedTextColor.GRAY));
        fillGray(inv);

        int[] slots = {11, 13, 15};
        for (int i = 0; i < 3 && i < rewards.size(); i++) {
            inv.setItem(slots[i], rewards.get(i));
        }

        player.openInventory(inv);
    }

    private static void fillGray(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.empty());
        pane.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) inv.setItem(i, pane);
        }
    }

    private static void fillGrayRow(Inventory inv, int row) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.displayName(Component.empty());
        pane.setItemMeta(m);
        for (int i = row * 9; i < (row + 1) * 9; i++) {
            inv.setItem(i, pane);
        }
    }
}