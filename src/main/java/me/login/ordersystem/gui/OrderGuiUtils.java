package me.login.ordersystem.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class for creating GUI items and titles
 * using Kyori Components.
 * (Points 1, 13, 14)
 */
public class OrderGuiUtils {

    private static final MiniMessage mm = MiniMessage.miniMessage();
    private static final ItemStack GRAY_PANE = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, mm.deserialize("<gray> "), null);

    private static final DecimalFormat largeNumFormat = new DecimalFormat("#,###");
    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0.00");


    /**
     * (Point 14) Creates a standard menu title.
     */
    public static Component getMenuTitle(String title) {
        return mm.deserialize("<dark_gray>" + title + "</dark_gray>");
    }

    /**
     * (Point 14) Creates an admin menu title.
     */
    public static Component getAdminMenuTitle(String title) {
        return mm.deserialize("<dark_red>" + title + "</dark_red>");
    }

    /**
     * Creates a GUI item with Kyori Components.
     * Lore is automatically made non-italic.
     */
    public static ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            if (lore != null) {
                meta.lore(lore.stream()
                        .map(line -> line.decoration(TextDecoration.ITALIC, false))
                        .collect(Collectors.toList()));
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    public static ItemStack getGrayPane() {
        return GRAY_PANE.clone();
    }

    // --- Formatting Helpers ---

    public static String formatAmount(int amount) {
        if (amount >= 1_000_000) return String.format("%.1fM", amount / 1_000_000.0).replace(".0", "");
        if (amount >= 1_000) return String.format("%.1fK", amount / 1_000.0).replace(".0", "");
        return largeNumFormat.format(amount);
    }

    public static String formatMoney(double amount) {
        if (amount >= 1_000_000) return String.format("$%.1fM", amount / 1_000_000.0).replace(".0", "");
        if (amount >= 1_000) return String.format("$%.1fK", amount / 1_000.0).replace(".0", "");
        return "$" + moneyFormat.format(amount);
    }
}