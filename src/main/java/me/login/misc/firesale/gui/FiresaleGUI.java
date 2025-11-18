package me.login.misc.firesale.gui;

import me.login.Login;
import me.login.misc.firesale.FiresaleListener;
import me.login.misc.firesale.FiresaleManager;
import me.login.misc.firesale.database.FiresaleDatabase;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FiresaleGUI {

    private final Login plugin; // Added plugin instance for NamespacedKey
    private final FiresaleManager manager;
    private final FiresaleDatabase database;
    private final MiniMessage miniMessage;

    private final ItemStack BORDER_PANE;

    public static final NamespacedKey NBT_ACTION_KEY = new NamespacedKey("login", "firesale_action");
    public static final NamespacedKey NBT_SALE_ID_KEY = new NamespacedKey("login", "firesale_id");
    public static final NamespacedKey NBT_PAGE_KEY = new NamespacedKey("login", "firesale_page");

    // Key to check for dye preview
    public static final NamespacedKey NBT_DYE_HEX_KEY = new NamespacedKey("login", "dye_hex");


    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    // Slot mappings for active sales
    private static final int[] SLOTS_1_SALE = { 22 };
    private static final int[] SLOTS_2_SALE = { 21, 23 };
    private static final int[] SLOTS_3_SALE = { 20, 22, 24 };
    private static final int[] SLOTS_4_SALE = { 19, 21, 23, 25 };

    public FiresaleGUI(Login plugin, FiresaleManager manager, FiresaleDatabase database) {
        this.plugin = plugin; // Store plugin instance
        this.manager = manager;
        this.database = database;
        this.miniMessage = plugin.getComponentSerializer();

        this.BORDER_PANE = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null, null);
    }

    /**
     * Opens the main navigation menu.
     */
    public void openMainMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, miniMessage.deserialize("<dark_gray>Firesale Menu"));

        // Fill border
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, BORDER_PANE);
        }

        // Active Sales Menu
        ItemStack activeSalesItem = createGuiItem(Material.EMERALD_BLOCK,
                "<green><bold>Active Firesales</bold>",
                List.of("<gray>Click to view all items", "<gray>currently on sale!"),
                "open_sales_menu");
        inv.setItem(11, activeSalesItem); // 2nd row, 3rd slot (index 11)

        // Sales History Menu
        ItemStack historyItem = createGuiItem(Material.BOOKSHELF,
                "<gold><bold>Sale History</bold>",
                List.of("<gray>Click to view all", "<gray>past firesales."),
                "open_history_menu");
        inv.setItem(15, historyItem); // 2nd row, 7th slot (index 15)

        player.setMetadata(FiresaleListener.METADATA_KEY, new FixedMetadataValue(plugin, "MAIN_MENU"));
        player.openInventory(inv);
    }

    /**
     * Opens the menu showing all currently active sales.
     */
    public void openActiveSalesMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 36, miniMessage.deserialize("<dark_gray>Active Firesales"));
        List<Firesale> sales = manager.getActiveSales();

        // Fill border
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, BORDER_PANE);
        }

        int[] slots;
        switch (sales.size()) {
            case 1: slots = SLOTS_1_SALE; break;
            case 2: slots = SLOTS_2_SALE; break;
            case 3: slots = SLOTS_3_SALE; break;
            case 4: slots = SLOTS_4_SALE; break;
            default: slots = new int[0]; break;
        }

        for (int i = 0; i < sales.size() && i < slots.length; i++) {
            Firesale sale = sales.get(i);
            inv.setItem(slots[i], createSaleItem(sale));
        }

        if (sales.isEmpty()) {
            ItemStack noSales = createGuiItem(Material.BARRIER, "<red><bold>No Active Sales</bold>", List.of("<gray>Check back later!"), null);
            inv.setItem(22, noSales);
        }

        player.setMetadata(FiresaleListener.METADATA_KEY, new FixedMetadataValue(plugin, "ACTIVE_SALES"));
        player.openInventory(inv);
    }

    /**
     * Creates the ItemStack representation for an active sale.
     */
    private ItemStack createSaleItem(Firesale sale) {
        ItemStack item = sale.getItem().clone();
        ItemMeta meta = item.getItemMeta();

        List<Component> newLore = new ArrayList<>();

        // --- FIX: Remove the broken italic-stripping logic ---
        // The lore is now correctly formatted by FiresaleItemManager.
        if (meta.hasLore()) {
            newLore.addAll(meta.lore());
        }
        // --- END FIX ---

        newLore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<gray>--------------------").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Price: <gold><price> Credits", Placeholder.component("price", Component.text(sale.getPrice()))).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Stock: <yellow><stock>", Placeholder.component("stock", Component.text(sale.getRemainingQuantity()))).decoration(TextDecoration.ITALIC, false));
        // --- FIX: Corrected parenthesis error. .decoration() is now INSIDE the .add() call ---
        newLore.add(miniMessage.deserialize("<white>Time Left: <aqua><time>", Placeholder.component("time", Component.text(manager.formatDuration(sale.getTimeRemainingMillis())))).decoration(TextDecoration.ITALIC, false));
        newLore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<green><bold>CLICK TO BUY</bold>").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<gray>--------------------").decoration(TextDecoration.ITALIC, false));

        // --- NEW FEATURE: Add preview lore if item has dye_hex NBT ---
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(NBT_DYE_HEX_KEY, PersistentDataType.STRING)) {
            newLore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
            newLore.add(miniMessage.deserialize("<gray>Shift + Right-click to preview</gray>").decoration(TextDecoration.ITALIC, false));
        }
        // --- END NEW FEATURE ---

        meta.lore(newLore);
        meta.getPersistentDataContainer().set(NBT_ACTION_KEY, PersistentDataType.STRING, "buy_sale_item");
        meta.getPersistentDataContainer().set(NBT_SALE_ID_KEY, PersistentDataType.INTEGER, sale.getSaleId());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Opens the paginated menu for sale history.
     */
    public void openHistoryMenu(Player player, int page) {
        int itemsPerPage = 27; // 3 rows
        List<Firesale> history = database.loadSalesHistory(page, itemsPerPage);
        int totalPages = database.getHistoryPageCount(itemsPerPage);

        Inventory inv = Bukkit.createInventory(null, 36, miniMessage.deserialize("<dark_gray>Sale History (Page <page>/<total>)",
                Placeholder.component("page", Component.text(page + 1)),
                Placeholder.component("total", Component.text(Math.max(1, totalPages)))
        ));

        // Fill bottom row
        for (int i = 27; i < 36; i++) {
            inv.setItem(i, BORDER_PANE);
        }

        // Add items
        for (int i = 0; i < history.size(); i++) {
            inv.setItem(i, createHistoryItem(history.get(i)));
        }

        // Pagination controls
        if (page > 0) {
            ItemStack prev = createGuiItem(Material.ARROW, "<aqua><bold>Previous Page</bold>", null, "history_prev_page");
            prev.getItemMeta().getPersistentDataContainer().set(NBT_PAGE_KEY, PersistentDataType.INTEGER, page - 1);
            inv.setItem(27, prev);
        }
        if (page < totalPages - 1) {
            ItemStack next = createGuiItem(Material.ARROW, "<aqua><bold>Next Page</bold>", null, "history_next_page");
            next.getItemMeta().getPersistentDataContainer().set(NBT_PAGE_KEY, PersistentDataType.INTEGER, page + 1);
            inv.setItem(35, next);
        }

        player.setMetadata(FiresaleListener.METADATA_KEY, new FixedMetadataValue(plugin, "HISTORY_MENU"));
        player.openInventory(inv);
    }

    /**
     * Creates the ItemStack representation for a historical sale.
     */
    private ItemStack createHistoryItem(Firesale sale) {
        ItemStack item = sale.getItem().clone();
        ItemMeta meta = item.getItemMeta();

        List<Component> newLore = new ArrayList<>();

        // --- FIX: Remove the broken italic-stripping logic ---
        // The lore is now correctly formatted by FiresaleItemManager.
        if (meta.hasLore()) {
            newLore.addAll(meta.lore());
        }
        // --- END FIX ---

        String statusColor = sale.getStatus() == SaleStatus.COMPLETED ? "<green>" :
                sale.getStatus() == SaleStatus.EXPIRED ? "<gray>" : "<red>";

        newLore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<gray>--------------------").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Sale ID: <yellow>" + sale.getSaleId()).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize(statusColor + "Status: " + sale.getStatus().toString()).decoration(TextDecoration.ITALIC, false));
        newLore.add(Component.text("").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Sold: <yellow><sold>/<total>",
                Placeholder.component("sold", Component.text(sale.getTotalSold())),
                Placeholder.component("total", Component.text(sale.getInitialQuantity()))).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Price: <gold>" + sale.getPrice() + " Credits").decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Created by: <aqua>" + sale.getCreatorName()).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Started: <gray>" + DATE_FORMATTER.format(sale.getStartTime())).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<white>Ended: <gray>" + DATE_FORMATTER.format(sale.getEndTime())).decoration(TextDecoration.ITALIC, false));
        newLore.add(miniMessage.deserialize("<gray>--------------------").decoration(TextDecoration.ITALIC, false));

        meta.lore(newLore);
        item.setItemMeta(meta);
        return item;
    }


    // --- GUI Item Util ---

    private ItemStack createGuiItem(Material material, String name, List<String> lore, String nbtAction) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(miniMessage.deserialize(name).decoration(TextDecoration.ITALIC, false));

        if (lore != null && !lore.isEmpty()) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(miniMessage.deserialize(line).decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreComponents);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        if (nbtAction != null) {
            meta.getPersistentDataContainer().set(NBT_ACTION_KEY, PersistentDataType.STRING, nbtAction);
        }

        item.setItemMeta(meta);
        return item;
    }
}