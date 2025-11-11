package me.login.ordersystem.gui;

import me.login.Login;
import me.login.ordersystem.data.Order;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import ---
import me.login.ordersystem.data.OfflineDeliveryManager;
import me.login.ordersystem.system.OrderSystem;
import me.login.ordersystem.util.OrderLogger;
import me.login.ordersystem.util.OrderMessageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Handles the /order adminmenu for staff.
 * (Refactored for Points 1, 2, 5, 7, 9, 10, 11, 13, 14)
 */
public class OrderAdminMenu {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private final OrderMessageHandler messageHandler;
    private final OrderLogger logger;
    private final OfflineDeliveryManager offlineDeliveryManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Use a cache of *all* active orders for this menu
    private final Map<Long, Order> activeOrdersCache = new ConcurrentHashMap<>();
    private final long CACHE_DURATION_MS = 30 * 1000; // 30 seconds
    private long lastCacheRefresh = 0;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45;

    private final NamespacedKey orderIdKey;
    private final NamespacedKey toggleKey; // (Point 9)

    public OrderAdminMenu(Login plugin, OrderSystem orderSystem, OrdersDatabase ordersDatabase, OrderMessageHandler messageHandler, OrderLogger logger, OfflineDeliveryManager offlineDeliveryManager) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = ordersDatabase;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.offlineDeliveryManager = offlineDeliveryManager;

        this.orderIdKey = new NamespacedKey(plugin, "admin_order_id");
        this.toggleKey = new NamespacedKey(plugin, "admin_toggle");
    }

    // --- Cache Handling ---
    private CompletableFuture<List<Order>> getActiveOrders(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && (now - lastCacheRefresh < CACHE_DURATION_MS) && !activeOrdersCache.isEmpty()) {
            return CompletableFuture.completedFuture(getSortedCacheList());
        }

        return ordersDatabase.loadActiveOrders().thenApply(orders -> {
            activeOrdersCache.clear();
            if (orders != null) {
                orders.forEach(order -> activeOrdersCache.put(order.getOrderId(), order));
            }
            lastCacheRefresh = System.currentTimeMillis();
            plugin.getLogger().info("[OrderSystem] Refreshed admin cache, loaded " + activeOrdersCache.size() + " active orders.");
            return getSortedCacheList();
        });
    }

    private List<Order> getSortedCacheList() {
        return activeOrdersCache.values().stream()
                .sorted(Comparator.comparingLong(Order::getCreationTimestamp).reversed())
                .collect(Collectors.toList());
    }


    // --- GUI Opening ---
    public void openAdminGui(Player player, int page) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        getActiveOrders(false).whenComplete((activeOrders, error) -> {
            if (error != null) {
                messageHandler.sendMessage(player, "<red>Error loading active orders for admin menu.</red>");
                logger.logError("Failed to load orders for admin " + player.getName(), error, 0);
                return;
            }
            // --- FIX: Type inference is now correct ---
            if (activeOrders.isEmpty()) {
                messageHandler.sendMessage(player, "<yellow>There are currently no active orders.</yellow>");
                return;
            }

            // (Point 7) Rebuild GUI on main thread
            Bukkit.getScheduler().runTask(plugin, () -> buildAndOpenAdminGui(player, page, activeOrders));

        });
    }

    private void buildAndOpenAdminGui(Player player, int page, List<Order> activeOrders) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        int totalOrders = activeOrders.size();
        int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        // (Point 14) Use admin title
        Component title = OrderGuiUtils.getAdminMenuTitle("Admin: Active Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        int startIndex = finalPage * ORDERS_PER_PAGE;
        int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
        List<Order> pageOrders = activeOrders.subList(startIndex, endIndex);

        for (int i = 0; i < pageOrders.size(); i++) {
            gui.setItem(i, createAdminDisplayItem(pageOrders.get(i)));
        }

        ItemStack grayPane = OrderGuiUtils.getGrayPane();
        for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

        if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
        gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
        gui.setItem(50, OrderGuiUtils.createGuiItem(Material.CLOCK, mm.deserialize("<yellow>Refresh List (Force)</yellow>"), null));

        // (Point 9) Add toggles
        // Default to ON
        gui.setItem(51, createToggleItem(true, true));  // Refund
        gui.setItem(52, createToggleItem(true, false)); // Items

        if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

        player.setMetadata(OrderModule.GUI_ADMIN_METADATA, new FixedMetadataValue(plugin, finalPage));
        player.openInventory(gui);
    }

    private ItemStack createAdminDisplayItem(Order order) {
        ItemStack displayItem = order.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;

        List<Component> lore = new ArrayList<>();
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<red><bold>PLACER: " + order.getPlacerName() + "</bold>"));
        lore.add(mm.deserialize("<dark_gray>---------------</dark_gray>"));
        lore.add(mm.deserialize("<aqua>Order Info</aqua>"));
        lore.add(mm.deserialize("<dark_purple>✍</dark_purple> <gray>Want: <yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName()));
        lore.add(mm.deserialize("<green>$</green> <gray>Price: <yellow>" + OrderGuiUtils.formatMoney(order.getPricePerItem()) + " Each"));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<aqua>Order Status</aqua>"));
        lore.add(mm.deserialize("<gold>⚒</gold> <gray>Got: <yellow>" + OrderGuiUtils.formatAmount(order.getAmountDelivered()) + "</yellow>/<yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount())));
        lore.add(mm.deserialize("<green>$</green> <gray>Total Value: <yellow>" + OrderGuiUtils.formatMoney(order.getTotalPrice())));
        lore.add(mm.deserialize("<blue>⏱</blue> <gray>Expires: <yellow>" + order.getFormattedExpiryTimeLeft()));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<red><bold>► Shift-Click to Force Cancel</bold>"));

        meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId());
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    // --- Click Handler (Called by OrderGuiListener) ---
    public void handleAdminMenuClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();
        ClickType clickType = event.getClick();

        // --- FIX: Moved currentPage retrieval to where it's needed ---
        // int currentPage = player.getMetadata(OrderModule.GUI_ADMIN_METADATA).get(0).asInt();

        if (slot >= 45) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) {
                player.closeInventory();
            } else if (type == Material.ARROW) {
                // Retrieve current page only when needed
                int currentPage = player.getMetadata(OrderModule.GUI_ADMIN_METADATA).get(0).asInt();
                if (slot == 45 && currentPage > 0) openAdminGui(player, currentPage - 1);
                else if (slot == 53) openAdminGui(player, currentPage + 1);
            } else if (type == Material.CLOCK && slot == 50) {
                // (Point 7) Force refresh
                int currentPage = player.getMetadata(OrderModule.GUI_ADMIN_METADATA).get(0).asInt();
                messageHandler.sendMessage(player, "<green>Forcing cache refresh...</green>");
                getActiveOrders(true).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> openAdminGui(player, currentPage))
                );
            } else if (slot == 51) {
                // (Point 9) Toggle Refund
                boolean currentState = getToggleState(clickedItem, true);
                event.getInventory().setItem(slot, createToggleItem(!currentState, true));
            } else if (slot == 52) {
                // (Point 9) Toggle Items
                boolean currentState = getToggleState(clickedItem, false);
                event.getInventory().setItem(slot, createToggleItem(!currentState, false));
            }
        } else { // Order item clicked
            if (clickType.isShiftClick()) {
                // --- START OF FIX for the error line ---

                // 1. Get the Order ID from the item's metadata
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta == null || !meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
                    messageHandler.sendMessage(player, "<red>Error reading order ID from item.</red>");
                    return;
                }
                long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);

                // 2. Get the "stale" Order object from your cache
                Order staleOrder = activeOrdersCache.get(orderId);

                if (staleOrder == null) {
                    messageHandler.sendMessage(player, "<red>Error finding order in cache. Try refreshing.</red>");
                    return;
                }

                // 3. Get the toggle states
                boolean refund = getToggleState(event.getInventory().getItem(51), true);
                boolean items = getToggleState(event.getInventory().getItem(52), false);

                // 4. Get the current page
                int currentPage = player.getMetadata(OrderModule.GUI_ADMIN_METADATA).get(0).asInt();

                // 5. Call the new, corrected 5-argument method
                // We pass the 'staleOrder' object, NOT the 'clickedItem'
                handleAdminCancel(player, staleOrder, refund, items, currentPage);

                // --- END OF FIX ---
            } else {
                messageHandler.sendMessage(player, "<red>You must Shift-Click to cancel an order.</red>");
            }
        }
    }

    // --- Admin Cancel Logic (Points 5, 7, 9, 11) ---
    private void handleAdminCancel(Player admin, Order staleOrder, boolean doRefund, boolean doReturnItems, int currentPage) {
        // Get the ID from the stale object. This is all we'll use it for.
        long orderId = staleOrder.getOrderId();

        // --- DUPE FIX: Re-load the order from the database ---
        ordersDatabase.loadOrderById(orderId).whenComplete((freshOrder, loadError) -> {
            if (loadError != null) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Failed to load order from database. Aborting.</red>"));
                logger.logError("AdminCancel failed to re-load order " + orderId, loadError, orderId);
                return;
            }

            // Check if the order is still eligible
            if (freshOrder == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<yellow>Order " + orderId + " no longer exists.</yellow>"));
                return;
            }

            // THIS IS THE LINE THAT STOPS THE DUPE:
            if (freshOrder.getStatus() != Order.OrderStatus.ACTIVE) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Cannot cancel: This order is no longer ACTIVE (it was " + freshOrder.getStatus().name() + ").</red>"));
                return;
            }
            // --- END DUPE FIX ---


            // Now we can proceed using the FRESH order data.
            double refundAmount = (freshOrder.getTotalAmount() - freshOrder.getAmountDelivered()) * freshOrder.getPricePerItem();
            int itemsToReturn = freshOrder.getAmountDelivered();

            // Mark order as cancelled in DB
            ordersDatabase.updateOrderStatus(freshOrder.getOrderId(), Order.OrderStatus.CANCELLED).whenComplete((success, error) -> {
                if (error != null || !success) {
                    Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Failed to update order status in database. Aborting.</red>"));
                    logger.logError("AdminCancel failed to update DB status", error, freshOrder.getOrderId());
                    return;
                }

                // (Point 7) Remove from local cache immediately
                activeOrdersCache.remove(freshOrder.getOrderId());

                CompletableFuture<List<ItemStack>> itemFuture;
                if (doReturnItems && itemsToReturn > 0) {
                    itemFuture = ordersDatabase.loadAndRemoveStoredItems(freshOrder.getOrderId());
                } else {
                    ordersDatabase.deleteOrder(freshOrder.getOrderId());
                    itemFuture = CompletableFuture.completedFuture(new ArrayList<>());
                }

                itemFuture.whenCompleteAsync((loadedItems, itemError) -> {
                    if (itemError != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Failed to load stored items. Refund not processed.</red>"));
                        logger.logError("AdminCancel failed to load items", itemError, freshOrder.getOrderId());
                        return;
                    }

                    double finalRefund = doRefund ? refundAmount : 0.0;
                    List<ItemStack> finalItems = doReturnItems ? loadedItems : new ArrayList<>();

                    logger.logAdminCancel(admin, freshOrder, finalRefund, finalItems.stream().mapToInt(ItemStack::getAmount).sum());

                    OfflinePlayer placer = Bukkit.getOfflinePlayer(freshOrder.getPlacerUUID());
                    if (placer.isOnline()) {
                        Player onlinePlacer = placer.getPlayer();
                        if (finalRefund > 0.01) {
                            OrderModule.getEconomy().depositPlayer(onlinePlacer, finalRefund);
                        }
                        if (!finalItems.isEmpty()) {
                            HashMap<Integer, ItemStack> failed = onlinePlacer.getInventory().addItem(finalItems.toArray(new ItemStack[0]));
                            failed.values().forEach(item -> onlinePlacer.getWorld().dropItemNaturally(onlinePlacer.getLocation(), item));
                        }

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            messageHandler.sendMessage(onlinePlacer, "<red>Your order (ID: " + freshOrder.getOrderId() + ") was forcibly cancelled by an administrator.</red>");
                            if (doRefund) messageHandler.sendMessage(onlinePlacer, "<green>The remaining funds (" + OrderModule.getEconomy().format(finalRefund) + ") have been refunded.</green>");
                            if (doReturnItems) messageHandler.sendMessage(onlinePlacer, "<green>The partially filled items have been returned to your inventory.</green>");
                        });
                    } else {
                        offlineDeliveryManager.scheduleDelivery(freshOrder.getPlacerUUID(), finalRefund, finalItems);
                    }

                    // Notify admin AND REFRESH MENU
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageHandler.sendMessage(admin, "<green>Successfully cancelled order " + freshOrder.getOrderId() + ".</green>");

                        // --- THIS IS THE CORRECTED REFRESH LINE ---
                        // It uses the correct method name from your file
                        getActiveOrders(true).thenRun(() ->
                                Bukkit.getScheduler().runTask(plugin, () -> openAdminGui(admin, currentPage))
                        );
                        // --- END REFRESH ---
                    });
                });
            });
        });
    }

    // --- (Point 9) Toggle Item Helpers ---

    private ItemStack createToggleItem(boolean state, boolean isRefund) {
        Material material = state ? Material.LIME_DYE : Material.GRAY_DYE;
        Component name = isRefund ?
                mm.deserialize(state ? "<green>Refund Money: ON" : "<red>Refund Money: OFF") :
                mm.deserialize(state ? "<green>Return Items: ON" : "<red>Return Items: OFF");

        List<Component> lore = new ArrayList<>();
        if (isRefund) {
            lore.add(mm.deserialize("<gray>If ON, placer gets money back"));
            lore.add(mm.deserialize("<gray>for the <bold>unfilled</bold> portion."));
        } else {
            lore.add(mm.deserialize("<gray>If ON, placer gets items back"));
            lore.add(mm.deserialize("<gray>from the <bold>filled</bold> portion."));
        }
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<yellow>Click to toggle.</yellow>"));

        ItemStack item = OrderGuiUtils.createGuiItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        // Store 1 for refund_ON, 0 for refund_OFF, 2 for items_ON, 3 for items_OFF
        byte value = (byte) (isRefund ? (state ? 1 : 0) : (state ? 2 : 3));
        meta.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, value);
        item.setItemMeta(meta);
        return item;
    }

    private boolean getToggleState(ItemStack item, boolean isRefund) {
        if (item == null || item.getItemMeta() == null) return true; // Default to on
        byte val = item.getItemMeta().getPersistentDataContainer().getOrDefault(toggleKey, PersistentDataType.BYTE, (byte) (isRefund ? 1 : 2));
        return isRefund ? val == 1 : val == 2;
    }
}