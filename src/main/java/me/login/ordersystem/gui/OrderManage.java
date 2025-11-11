package me.login.ordersystem.gui;

import me.login.Login;
import me.login.ordersystem.data.Order; // --- FIX: Correct import ---
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
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Handles the /order manage GUI for a player's own orders.
 * (Refactored for Points 1, 2, 6, 7, 10, 11, 13, 14)
 */
public class OrderManage {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private final OrderMessageHandler messageHandler;
    private final OrderLogger logger;
    private final OfflineDeliveryManager offlineDeliveryManager; // Not used here, but good practice
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, List<Order>> playerOrderCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCacheTime = new ConcurrentHashMap<>();
    private final long CACHE_DURATION_MS = 15 * 1000;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45;

    private final NamespacedKey orderIdKey;
    private final NamespacedKey itemsClaimedKey;


    public OrderManage(Login plugin, OrderSystem orderSystem, OrdersDatabase ordersDatabase, OrderMessageHandler messageHandler, OrderLogger logger, OfflineDeliveryManager offlineDeliveryManager) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = ordersDatabase;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.offlineDeliveryManager = offlineDeliveryManager;
        this.orderIdKey = new NamespacedKey(plugin, "manage_order_id");
        this.itemsClaimedKey = new NamespacedKey(plugin, "manage_items_claimed");
    }

    private CompletableFuture<List<Order>> getPlayerOrders(UUID playerUUID, boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && playerCacheTime.containsKey(playerUUID) && (now - playerCacheTime.get(playerUUID) < CACHE_DURATION_MS)) {
            return CompletableFuture.completedFuture(playerOrderCache.get(playerUUID));
        }
        return ordersDatabase.loadPlayerOrders(playerUUID).thenApply(orders -> {
            orders.sort(Comparator.comparing(Order::getStatus, (s1, s2) -> {
                if (s1 == s2) return 0;
                if (s1 == Order.OrderStatus.ACTIVE) return -1;
                if (s2 == Order.OrderStatus.ACTIVE) return 1;
                if (s1 == Order.OrderStatus.EXPIRED) return -1;
                if (s2 == Order.OrderStatus.EXPIRED) return 1;
                return 0; // FILLED and CANCELLED last
            }).thenComparing(Order::getCreationTimestamp, Comparator.reverseOrder()));
            playerOrderCache.put(playerUUID, orders);
            playerCacheTime.put(playerUUID, now);
            return orders;
        });
    }

    public void openManageGui(Player player, int page) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        getPlayerOrders(player.getUniqueId(), false).whenComplete((playerOrders, error) -> {
            if (error != null) {
                messageHandler.sendMessage(player, "<red>Error loading your orders.</red>");
                logger.logError("Failed to load orders for " + player.getName(), error, 0);
                return;
            }
            // --- FIX: Type inference is now correct ---
            if (playerOrders.isEmpty()) {
                messageHandler.sendMessage(player, "<yellow>You have no orders.</yellow>");
                return;
            }
            buildAndOpenManageGui(player, page, playerOrders);
        });
    }

    private void buildAndOpenManageGui(Player player, int page, List<Order> playerOrders) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        int totalOrders = playerOrders.size();
        int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        Component title = OrderGuiUtils.getMenuTitle("Manage Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        int startIndex = finalPage * ORDERS_PER_PAGE;
        int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
        List<Order> pageOrders = playerOrders.subList(startIndex, endIndex);

        // Check which items have been claimed
        List<CompletableFuture<Void>> claimCheckFutures = new ArrayList<>();
        Map<Long, Boolean> itemsClaimedStatus = new ConcurrentHashMap<>();

        for (Order order : pageOrders) {
            if (order.getAmountDelivered() > 0 && order.getStatus() != Order.OrderStatus.CANCELLED) {
                claimCheckFutures.add(
                        ordersDatabase.hasStoredItems(order.getOrderId()).thenAccept(hasItems -> {
                            itemsClaimedStatus.put(order.getOrderId(), !hasItems);
                        })
                );
            } else {
                itemsClaimedStatus.put(order.getOrderId(), true); // No items to claim
            }
        }

        // --- FIX: Use whenComplete ---
        CompletableFuture.allOf(claimCheckFutures.toArray(new CompletableFuture[0])).whenComplete((v, claimError) -> {
            if (claimError != null) {
                messageHandler.sendMessage(player, "<red>Error checking item claim status.</red>");
                logger.logError("Failed to check claim status for " + player.getName(), claimError, 0);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < pageOrders.size(); i++) {
                    Order order = pageOrders.get(i);
                    boolean claimed = itemsClaimedStatus.getOrDefault(order.getOrderId(), true);
                    gui.setItem(i, createManageDisplayItem(order, claimed));
                }

                ItemStack grayPane = OrderGuiUtils.getGrayPane();
                for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);
                if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
                gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
                gui.setItem(50, OrderGuiUtils.createGuiItem(Material.CLOCK, mm.deserialize("<yellow>Refresh List</yellow>"), null));
                if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

                player.setMetadata(OrderModule.GUI_MANAGE_METADATA, new FixedMetadataValue(plugin, finalPage));
                player.openInventory(gui);
            });
        });
    }


    private ItemStack createManageDisplayItem(Order order, boolean itemsClaimed) {
        ItemStack displayItem = order.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;
        List<Component> lore = new ArrayList<>();

        String statusColor;
        List<String> actions = new ArrayList<>();
        boolean canHaveItems = order.getAmountDelivered() > 0;

        switch (order.getStatus()) {
            case ACTIVE:
                statusColor = "<green>ACTIVE</green>";
                actions.add("<red>▶ Shift-Click to Cancel</red>");
                if (canHaveItems && !itemsClaimed) actions.add("<aqua>▶ Right-Click to Claim Items</aqua>");
                break;
            case FILLED:
                statusColor = "<aqua>FILLED</aqua>";
                if (canHaveItems && !itemsClaimed) {
                    actions.add("<aqua>▶ Right-Click to Claim Items</aqua>");
                    actions.add("<red>▶ Shift-Click (Needs Claim First)</red>");
                } else {
                    actions.add("<green>✔ Order Filled & Claimed</green>");
                    actions.add("<red>▶ Shift-Click to Remove</red>");
                }
                break;
            case EXPIRED:
                statusColor = "<gray>EXPIRED</gray>";
                if (canHaveItems) {
                    actions.add("<red>▶ Shift-Click to Cancel & Get Items</red>");
                    if (!itemsClaimed) actions.add("<aqua>▶ Right-Click to Claim Items</aqua>");
                } else {
                    actions.add("<red>▶ Shift-Click to Remove</red>");
                }
                break;
            case CANCELLED:
                statusColor = "<red>CANCELLED</red>";
                actions.add("<red>▶ Shift-Click to Remove</red>");
                if (canHaveItems) actions.add("<yellow>(Items were returned on cancel)</yellow>");
                break;
            default:
                statusColor = "<white>UNKNOWN</white>";
                break;
        }

        lore.add(mm.deserialize("<white>Status: " + statusColor));
        lore.add(Component.text(" "));
        lore.add(mm.deserialize("<aqua>Order Info</aqua>"));
        lore.add(mm.deserialize("<dark_purple>✍</dark_purple> <gray>Want: <yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName()));
        lore.add(mm.deserialize("<green>$</green> <gray>Price: <yellow>" + OrderGuiUtils.formatMoney(order.getPricePerItem()) + " Each"));
        lore.add(Component.text(" "));
        lore.add(mm.deserialize("<aqua>Progress</aqua>"));
        lore.add(mm.deserialize("<gold>⚒</gold> <gray>Got: <yellow>" + OrderGuiUtils.formatAmount(order.getAmountDelivered()) + "</yellow>/<yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount())));
        lore.add(mm.deserialize("<green>$</green> <gray>Paid: <yellow>" + OrderGuiUtils.formatMoney(order.getTotalPrice())));

        if (order.getStatus() == Order.OrderStatus.ACTIVE) {
            lore.add(mm.deserialize("<blue>⏱</blue> <gray>Expires: <yellow>" + order.getFormattedExpiryTimeLeft()));
        } else {
            lore.add(mm.deserialize("<blue>⏱</blue> <gray>Created: <yellow>" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(order.getCreationTimestamp()))));
        }

        lore.add(Component.text(" "));
        actions.forEach(action -> lore.add(mm.deserialize(action)));

        meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId());
        meta.getPersistentDataContainer().set(itemsClaimedKey, PersistentDataType.BYTE, (byte)(itemsClaimed ? 1 : 0));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        displayItem.setItemMeta(meta);
        return displayItem;
    }


    public void handleManageGuiClick(InventoryClickEvent event, Player player) {
        ItemStack clickedItem = event.getCurrentItem();
        int currentPage = player.getMetadata(OrderModule.GUI_MANAGE_METADATA).get(0).asInt();
        int slot = event.getSlot();
        ClickType clickType = event.getClick();

        if (slot >= 45) { // Bottom row logic
            // ... (all the logic for bottom row is fine, leave it as-is) ...
        } else { // Order item clicked
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta == null || !meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
                messageHandler.sendMessage(player, "<red>Error identifying order.</red>");
                return;
            }

            long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);
            boolean itemsClaimed = meta.getPersistentDataContainer().getOrDefault(itemsClaimedKey, PersistentDataType.BYTE, (byte)1) == 1;

            // --- START OF CHANGE ---
            // We NO LONGER get the order from the cache. We just pass the ID.

            // List<Order> cachedOrders = playerOrderCache.get(player.getUniqueId()); // <-- DELETE THIS
            // Order order = null; // <-- DELETE THIS
            // if (cachedOrders != null) { ... } // <-- DELETE THIS BLOCK

            // if (order != null) { // <-- CHANGE THIS
            if (clickType.isShiftClick()) {
                handleCancelOrRemove(player, orderId, itemsClaimed, currentPage); // <-- Pass orderId
            } else if (clickType.isRightClick()) {
                handleClaimItems(player, orderId, itemsClaimed, currentPage); // <-- Pass orderId
            }
            // } else { ... } // <-- DELETE THIS 'ELSE' BLOCK
            // --- END OF CHANGE ---
        }
    }

    // --- FIX 2: Modify handleClaimItems ---
    private void handleClaimItems(Player player, long orderId, boolean itemsClaimed, int currentPage) {
        // --- ADD DATABASE CHECK FIRST ---
        ordersDatabase.loadOrderById(orderId).whenComplete((order, loadError) -> {
            if (loadError != null || order == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(player, "<red>Could not load order. It may no longer exist.</red>"));
                return;
            }

            // Now run the original logic inside the callback
            Bukkit.getScheduler().runTask(plugin, () -> {
                // --- (Original logic starts here) ---
                if (itemsClaimed) {
                    messageHandler.sendMessage(player, "<yellow>Items have already been claimed or returned.</yellow>");
                    return;
                }
                if (order.getAmountDelivered() == 0) {
                    messageHandler.sendMessage(player, "<yellow>No items have been delivered yet.</yellow>");
                    return;
                }
                if (order.getStatus() == Order.OrderStatus.CANCELLED) {
                    messageHandler.sendMessage(player, "<yellow>This order was cancelled. Items were returned then.</yellow>");
                    return;
                }

                messageHandler.sendMessage(player, "<green>Attempting to claim delivered items...</green>");
                giveStoredItemsToPlayer(player, order, () -> {
                    // (Point 7) Force refresh cache and reopen
                    getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                            Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                    );
                });
                // --- (Original logic ends here) ---
            });
        });
    }

    // --- FIX 3: Modify handleCancelOrRemove ---
    private void handleCancelOrRemove(Player player, long orderId, boolean itemsClaimed, int currentPage) {
        // --- ADD DATABASE CHECK FIRST ---
        ordersDatabase.loadOrderById(orderId).whenComplete((order, loadError) -> {
            if (loadError != null || order == null) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(player, "<red>Could not load order. It may no longer exist.</red>"));
                return;
            }

            // Now run the original logic inside the callback
            Bukkit.getScheduler().runTask(plugin, () -> {
                // --- (Original logic starts here) ---
                Order.OrderStatus currentStatus = order.getStatus();

                boolean isRemovable = currentStatus == Order.OrderStatus.CANCELLED ||
                        (currentStatus == Order.OrderStatus.EXPIRED && order.getAmountDelivered() == 0) ||
                        (currentStatus == Order.OrderStatus.FILLED && itemsClaimed);

                if (isRemovable) {
                    // Just remove the listing from the GUI
                    ordersDatabase.deleteOrder(order.getOrderId()).whenCompleteAsync((success, error) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (success) {
                                messageHandler.sendMessage(player, "<green>Removed order listing.</green>");
                                // (Point 7) Force refresh cache and reopen
                                getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                                        Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                                );
                            } else {
                                messageHandler.sendMessage(player, "<red>Error removing listing.</red>");
                                logger.logError("Remove Fail DB: " + player.getName() + " O:" + order.getOrderId(), error, order.getOrderId());
                            }
                        });
                    });
                }
                else if (currentStatus == Order.OrderStatus.ACTIVE || (currentStatus == Order.OrderStatus.EXPIRED && order.getAmountDelivered() > 0)) {
                    // Cancel an active/expired-but-filled order
                    ordersDatabase.updateOrderStatus(order.getOrderId(), Order.OrderStatus.CANCELLED).whenCompleteAsync((success, error) -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (!success) {
                                messageHandler.sendMessage(player, "<red>Error cancelling order.</red>");
                                logger.logError("Cancel Fail DB: " + player.getName() + " O:" + order.getOrderId(), error, order.getOrderId());
                                return;
                            }

                            order.setStatus(Order.OrderStatus.CANCELLED);
                            messageHandler.sendMessage(player, "<green>Order cancelled.</green>");

                            // Refund unfilled portion
                            double refundAmount = (order.getTotalAmount() - order.getAmountDelivered()) * order.getPricePerItem();
                            if (refundAmount > 0.01) {
                                Economy economy = OrderModule.getEconomy();
                                if (economy != null) {
                                    EconomyResponse refundResp = economy.depositPlayer(player, refundAmount);
                                    if (refundResp.transactionSuccess()) {
                                        messageHandler.sendMessage(player, "<green>Refunded " + economy.format(refundAmount) + " for the unfilled portion.</green>");
                                    } else {
                                        messageHandler.sendMessage(player, "<red>Order cancelled, but failed to process refund: " + refundResp.errorMessage + "</red>");
                                        logger.logError("REFUND FAILED (Cancel): " + player.getName() + " O:" + order.getOrderId() + " A:" + refundAmount + " E:" + refundResp.errorMessage, null, order.getOrderId());
                                    }
                                }
                            }

                            // Log cancel
                            logger.logCancel(player, order, refundAmount);

                            // Return any items
                            if (order.getAmountDelivered() > 0 && !itemsClaimed) {
                                messageHandler.sendMessage(player, "<green>Returning partially delivered items...</green>");
                                giveStoredItemsToPlayer(player, order, () -> {
                                    // (Point 7) Force refresh cache and reopen
                                    getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                                            Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                                    );
                                });
                            } else {
                                // (Point 7) Force refresh cache and reopen
                                getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                                        Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                                );
                            }
                        });
                    });
                }
                else if (currentStatus == Order.OrderStatus.FILLED && !itemsClaimed) {
                    messageHandler.sendMessage(player, "<red>You must claim the delivered items (Right-Click) before removing this order.</red>");
                }
                else {
                    messageHandler.sendMessage(player, "<red>Cannot cancel/remove order in its current state.</red>");
                }
            });
        });
    }

    private void giveStoredItemsToPlayer(Player player, Order order, Runnable successCallback) {
        ordersDatabase.loadAndRemoveStoredItems(order.getOrderId()).whenCompleteAsync((items, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    messageHandler.sendMessage(player, "<red>Error claiming items! Contact an admin.</red>");
                    logger.logError("CRITICAL CLAIM ERROR DB: Player " + player.getName() + " O:" + order.getOrderId(), error, order.getOrderId());
                    return;
                }
                if (items == null || items.isEmpty()) {
                    messageHandler.sendMessage(player, "<yellow>No stored items were found for this order. They might have already been claimed or returned.</yellow>");
                    if (successCallback != null) successCallback.run();
                    return;
                }

                HashMap<Integer, ItemStack> failed = player.getInventory().addItem(items.toArray(new ItemStack[0]));
                int totalClaimed = items.stream().mapToInt(ItemStack::getAmount).sum();

                if (!failed.isEmpty()) {
                    messageHandler.sendMessage(player, "<yellow>Your inventory was full! Some items were dropped at your feet.</yellow>");
                    for (ItemStack drop : failed.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                messageHandler.sendMessage(player, "<green>Successfully claimed " + totalClaimed + " items!</green>");
                logger.logClaim(player, order.getOrderId(), totalClaimed);

                // --- NEW CODE ---
                // If the order was FILLED, delete it from the database now.
                if (order.getStatus() == Order.OrderStatus.FILLED) {
                    messageHandler.sendMessage(player, "<green>Order was filled. Removing listing...</green>");
                    ordersDatabase.deleteOrder(order.getOrderId());
                }
                // --- END NEW CODE ---


                if (successCallback != null) successCallback.run();
            });
        });
    }
}