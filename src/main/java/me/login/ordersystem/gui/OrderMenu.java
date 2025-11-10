package me.login.ordersystem.gui;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import me.login.ordersystem.data.Order;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.OrdersDatabase;
import me.login.ordersystem.system.OrderFilling;
import me.login.ordersystem.system.OrderSystem;
import me.login.ordersystem.util.OrderMessageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles the main /order menu for viewing and filling active orders.
 * (Refactored for Points 1, 2, 7, 10, 11, 13, 14)
 */
public class OrderMenu {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private final OrderFilling orderFilling;
    private final OrderMessageHandler messageHandler;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private Map<Long, Order> activeOrdersCache = new ConcurrentHashMap<>();
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 30 * 1000;
    private boolean isRefreshingCache = false;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45;

    private final NamespacedKey orderIdKey;

    private static class SearchData {
        final int page;
        final String searchTerm;
        SearchData(int page, String searchTerm) { this.page = page; this.searchTerm = searchTerm; }
    }

    public OrderMenu(Login plugin, OrderSystem orderSystem, OrdersDatabase ordersDatabase, OrderFilling orderFilling, OrderMessageHandler messageHandler) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = ordersDatabase;
        this.orderFilling = orderFilling;
        this.messageHandler = messageHandler;
        this.orderIdKey = new NamespacedKey(plugin, "menu_order_id");
        refreshActiveOrdersCache(true);
    }

    // --- Cache Handling ---
    private CompletableFuture<List<Order>> refreshActiveOrdersCache(boolean force) {
        if (isRefreshingCache || (!force && (System.currentTimeMillis() - lastCacheUpdateTime <= CACHE_DURATION_MS))) {
            return CompletableFuture.completedFuture(getActiveOrdersFromCache());
        }
        isRefreshingCache = true;

        return ordersDatabase.loadActiveOrders().whenCompleteAsync((orders, error) -> {
            if (error != null) {
                plugin.getLogger().severe("[OrderSystem] Failed to load active orders: " + error.getMessage());
            } else {
                Map<Long, Order> newCache = new ConcurrentHashMap<>();
                if (orders != null) orders.forEach(order -> newCache.put(order.getOrderId(), order));
                this.activeOrdersCache = newCache;
                this.lastCacheUpdateTime = System.currentTimeMillis();
                plugin.getLogger().info("[OrderSystem] Loaded " + newCache.size() + " active orders.");
            }
            isRefreshingCache = false;
        }).thenApply(orders -> getActiveOrdersFromCache());
    }

    private List<Order> getActiveOrdersFromCache() {
        return activeOrdersCache.values().stream()
                .sorted(Comparator.comparingLong(Order::getCreationTimestamp).reversed())
                .collect(Collectors.toList());
    }

    // --- GUI Opening ---
    public void openMenuListGui(Player player, int page) {
        // (Point 7) Refresh cache if it's stale
        if (System.currentTimeMillis() - lastCacheUpdateTime > CACHE_DURATION_MS) {
            refreshActiveOrdersCache(true).thenAccept(orders ->
                    buildAndOpenGui(player, page, orders)
            );
        } else {
            buildAndOpenGui(player, page, getActiveOrdersFromCache());
        }
    }

    private void buildAndOpenGui(Player player, int page, List<Order> activeOrders) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            int totalOrders = activeOrders.size();
            int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
            int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

            Component title = OrderGuiUtils.getMenuTitle("Active Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
            Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

            int startIndex = finalPage * ORDERS_PER_PAGE;
            int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
            List<Order> pageOrders = activeOrders.subList(startIndex, endIndex);
            for (int i = 0; i < pageOrders.size(); i++) {
                gui.setItem(i, createOrderDisplayItem(pageOrders.get(i)));
            }

            ItemStack grayPane = OrderGuiUtils.getGrayPane();
            for (int i = ORDERS_PER_PAGE; i < GUI_SIZE; i++) gui.setItem(i, grayPane);

            if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
            gui.setItem(47, OrderGuiUtils.createGuiItem(Material.DIAMOND, mm.deserialize("<aqua>Create New Order</aqua>"), Arrays.asList(mm.deserialize("<gray>Click to open the"), mm.deserialize("<gray>order creation menu."))));
            gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
            gui.setItem(49, OrderGuiUtils.createGuiItem(Material.OAK_SIGN, mm.deserialize("<aqua>Search For Orders</aqua>"), Arrays.asList(mm.deserialize("<gray>Click to search for"), mm.deserialize("<gray>a specific item."))));
            gui.setItem(50, OrderGuiUtils.createGuiItem(Material.CLOCK, mm.deserialize("<yellow>Refresh List</yellow>"), null));
            if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

            player.setMetadata(OrderModule.GUI_MENU_METADATA, new FixedMetadataValue(plugin, finalPage));
            player.openInventory(gui);
        });
    }

    public void openMenuSearchResultsGui(Player player, int page, String searchTerm) {
        List<Order> filteredOrders = getActiveOrdersFromCache().stream()
                .filter(order -> order.getFormattedItemName().toLowerCase().contains(searchTerm.toLowerCase()))
                .collect(Collectors.toList());

        if (filteredOrders.isEmpty()) {
            messageHandler.sendMessage(player, "<red>No active orders found for '" + searchTerm + "'.</red>");
            openMenuListGui(player, 0); // Go back to main menu
            return;
        }

        int totalOrders = filteredOrders.size();
        int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        Component title = OrderGuiUtils.getMenuTitle("Search: '" + searchTerm + "' (" + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        int startIndex = finalPage * ORDERS_PER_PAGE;
        int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
        List<Order> pageOrders = filteredOrders.subList(startIndex, endIndex);
        for (int i = 0; i < pageOrders.size(); i++) {
            gui.setItem(i, createOrderDisplayItem(pageOrders.get(i)));
        }

        ItemStack grayPane = OrderGuiUtils.getGrayPane();
        for (int i = ORDERS_PER_PAGE; i < GUI_SIZE; i++) gui.setItem(i, grayPane);
        if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
        gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
        gui.setItem(50, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<aqua>Back to Main Menu</aqua>"), null));
        if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

        player.setMetadata(OrderModule.GUI_MENU_SEARCH_METADATA, new FixedMetadataValue(plugin, new SearchData(finalPage, searchTerm)));
        player.openInventory(gui);
    }

    private void openMenuSearchSignInput(Player player) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Item Name", "or 'C' to cancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                openMenuListGui(p, 0);
                            } else {
                                openMenuSearchResultsGui(p, 0, input);
                            }
                        });
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Order Menu Search SignGUI", e);
            messageHandler.sendMessage(player, "<red>Error opening search prompt.</red>");
        }
    }

    private ItemStack createOrderDisplayItem(Order order) {
        ItemStack displayItem = order.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<dark_aqua><bold>" + order.getPlacerName() + "'S Order</bold>"));
        lore.add(mm.deserialize("<dark_gray>---------------</dark_gray>"));
        lore.add(mm.deserialize("<aqua>Order Info</aqua>"));
        lore.add(mm.deserialize("<dark_purple>✍</dark_purple> <gray>Requested: <yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName()));
        lore.add(mm.deserialize("<green>$</green> <gray>Price: <yellow>" + OrderGuiUtils.formatMoney(order.getPricePerItem()) + " Each"));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<aqua>Order Status</aqua>"));
        lore.add(mm.deserialize("<gold>⚒</gold> <gray>Delivered: <yellow>" + OrderGuiUtils.formatAmount(order.getAmountDelivered()) + "</yellow>/<yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount())));
        lore.add(mm.deserialize("<green>$</green> <gray>Paid Value: <yellow>" + OrderGuiUtils.formatMoney(order.getAmountPaidByFillers()) + "</yellow>/<yellow>" + OrderGuiUtils.formatMoney(order.getTotalPrice())));
        lore.add(mm.deserialize("<blue>⏱</blue> <gray>Expiry: <yellow>" + order.getFormattedExpiryTimeLeft()));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<green>► Click to Fill Order</green>"));

        meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId());
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    // --- Click Handlers (Called by OrderGuiListener) ---

    public void handleMainMenuClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        int currentPage = player.getMetadata(OrderModule.GUI_MENU_METADATA).get(0).asInt();
        int slot = event.getSlot();

        if (slot >= ORDERS_PER_PAGE) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
            else if (type == Material.DIAMOND && slot == 47) { player.performCommand("order create"); }
            else if (type == Material.OAK_SIGN && slot == 49) { player.closeInventory(); openMenuSearchSignInput(player); }
            else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) openMenuListGui(player, currentPage - 1);
                else if (slot == 53) openMenuListGui(player, currentPage + 1);
            } else if (type == Material.CLOCK && slot == 50) {
                // (Point 7) Force refresh
                messageHandler.sendMessage(player, "<green>Refreshing orders...</green>");
                refreshActiveOrdersCache(true).thenAccept(orders ->
                        buildAndOpenGui(player, currentPage, orders)
                );
            }
        } else { // Order item
            processFillClick(player, clickedItem, currentPage);
        }
    }

    public void handleSearchMenuClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        SearchData searchData = (SearchData) player.getMetadata(OrderModule.GUI_MENU_SEARCH_METADATA).get(0).value();
        int slot = event.getSlot();

        if (slot >= ORDERS_PER_PAGE) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
            else if (type == Material.ARROW) {
                if (slot == 45 && searchData.page > 0) { openMenuSearchResultsGui(player, searchData.page - 1, searchData.searchTerm); }
                else if (slot == 53) { openMenuSearchResultsGui(player, searchData.page + 1, searchData.searchTerm); }
                else if (slot == 50) { openMenuListGui(player, 0); } // Back button
            }
        } else { // Order item
            processFillClick(player, clickedItem, searchData.page);
        }
    }

    private void processFillClick(Player player, ItemStack clickedItem, int currentPage) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
            long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);
            Order order = activeOrdersCache.get(orderId);

            if (order != null && order.getStatus() == Order.OrderStatus.ACTIVE && !order.isExpired()) {
                orderFilling.startFillingProcess(player, order, currentPage);
            } else {
                messageHandler.sendMessage(player, "<red>Order unavailable or changed. Refreshing...</red>");
                refreshActiveOrdersCache(true).thenAccept(orders ->
                        buildAndOpenGui(player, 0, orders) // Go back to main menu
                );
            }
        } else {
            messageHandler.sendMessage(player, "<red>Error retrieving order ID.</red>");
        }
    }
}