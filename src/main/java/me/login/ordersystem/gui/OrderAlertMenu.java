package me.login.ordersystem.gui;

import me.login.Login;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.Order; // --- FIX: Correct import ---
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import ---
import me.login.ordersystem.data.OfflineDeliveryManager;
import me.login.ordersystem.util.OrderLogger;
import me.login.ordersystem.util.OrderMessageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap; // --- FIX: Added import ---
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture; // --- FIX: Added import ---
import java.util.logging.Level;

/**
 * Handles the 3-row staff alert menu for force-cancelling orders.
 * (Point 4)
 */
public class OrderAlertMenu {

    private final Login plugin;
    private final OrdersDatabase ordersDatabase;
    private final OrderMessageHandler messageHandler;
    private final OrderLogger logger;
    private final OfflineDeliveryManager offlineDeliveryManager;
    private final MiniMessage mm = MiniMessage.miniMessage();

    // Key to store toggles
    private final NamespacedKey toggleKey;
    public static final String ALERT_ORDER_KEY = "OrderAlertMenu_Order";

    private static final int SLOT_ORDER_ITEM = 13;
    private static final int SLOT_TOGGLE_REFUND = 10;
    private static final int SLOT_TOGGLE_ITEMS = 16;
    private static final int SLOT_CLOSE = 26;

    public OrderAlertMenu(Login plugin, OrdersDatabase ordersDatabase, OrderMessageHandler messageHandler, OrderLogger logger, OfflineDeliveryManager offlineDeliveryManager) {
        this.plugin = plugin;
        this.ordersDatabase = ordersDatabase;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.offlineDeliveryManager = offlineDeliveryManager;
        this.toggleKey = new NamespacedKey(plugin, "order_alert_toggles");
    }

    public void openAlertGui(Player staff, long orderId) {
        // --- FIX: Use whenComplete ---
        ordersDatabase.loadOrderById(orderId).whenComplete((order, error) -> {
            if (error != null) {
                messageHandler.sendMessage(staff, "<red>Error loading order " + orderId + ".</red>");
                logger.logError("Failed to load order for alert menu", error, orderId);
                return;
            }
            if (order == null) {
                messageHandler.sendMessage(staff, "<yellow>Order " + orderId + " no longer exists.</yellow>");
                return;
            }
            // --- FIX: Type inference is now correct ---
            if (order.getStatus() != Order.OrderStatus.ACTIVE) {
                messageHandler.sendMessage(staff, "<yellow>Order " + orderId + " is no longer active.</yellow>");
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory gui = Bukkit.createInventory(null, 27, OrderGuiUtils.getAdminMenuTitle("Inspect Order: " + orderId));

                // Store order data in player metadata
                staff.setMetadata(OrderModule.GUI_ALERT_METADATA, new FixedMetadataValue(plugin, true));
                staff.setMetadata(ALERT_ORDER_KEY, new FixedMetadataValue(plugin, order));

                // Default toggles: ON
                boolean refund = true;
                boolean items = true;

                fillBackground(gui);
                // --- FIX: Type inference is now correct ---
                gui.setItem(SLOT_ORDER_ITEM, createDisplayItem(order));
                gui.setItem(SLOT_TOGGLE_REFUND, createToggleItem(refund, true));
                gui.setItem(SLOT_TOGGLE_ITEMS, createToggleItem(items, false));
                gui.setItem(SLOT_CLOSE, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));

                staff.openInventory(gui);
            });
        });
    }

    private void fillBackground(Inventory gui) {
        for (int i = 0; i < gui.getSize(); i++) {
            if (gui.getItem(i) == null) {
                gui.setItem(i, OrderGuiUtils.getGrayPane());
            }
        }
    }

    public void handleAlertMenuClick(InventoryClickEvent event, Player staff) {
        int slot = event.getSlot();

        // Retrieve order from metadata
        if (!staff.hasMetadata(ALERT_ORDER_KEY)) {
            staff.closeInventory();
            return;
        }
        Order order = (Order) staff.getMetadata(ALERT_ORDER_KEY).get(0).value();

        // Toggles
        boolean refund = getToggleState(event.getInventory().getItem(SLOT_TOGGLE_REFUND), true);
        boolean items = getToggleState(event.getInventory().getItem(SLOT_TOGGLE_ITEMS), false);

        switch (slot) {
            case SLOT_TOGGLE_REFUND:
                event.getInventory().setItem(SLOT_TOGGLE_REFUND, createToggleItem(!refund, true));
                break;
            case SLOT_TOGGLE_ITEMS:
                event.getInventory().setItem(SLOT_TOGGLE_ITEMS, createToggleItem(!items, false));
                break;
            case SLOT_CLOSE:
                staff.closeInventory();
                break;
            case SLOT_ORDER_ITEM:
                if (event.getClick() == ClickType.SHIFT_LEFT) {
                    staff.closeInventory();
                    handleAdminCancel(staff, order, refund, items);
                } else {
                    messageHandler.sendMessage(staff, "<red>You must Shift-Click the item to cancel the order.</red>");
                }
                break;
        }
    }

    private void handleAdminCancel(Player admin, Order order, boolean doRefund, boolean doReturnItems) {
        // 1. Calculate refund and items to return
        double refundAmount = (order.getTotalAmount() - order.getAmountDelivered()) * order.getPricePerItem();
        int itemsToReturn = order.getAmountDelivered();

        // 2. Mark order as cancelled in DB
        // --- FIX: Use whenComplete ---
        ordersDatabase.updateOrderStatus(order.getOrderId(), Order.OrderStatus.CANCELLED).whenComplete((success, error) -> {
            // --- FIX: Type inference is now correct ---
            if (error != null || !success) {
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Failed to update order status in database. Aborting.</red>"));
                logger.logError("AdminCancel failed to update DB status", error, order.getOrderId());
                return;
            }

            // 3. Load stored items IF we need to return them
            CompletableFuture<List<ItemStack>> itemFuture; // --- FIX: Class now found ---
            if (doReturnItems && itemsToReturn > 0) {
                itemFuture = ordersDatabase.loadAndRemoveStoredItems(order.getOrderId());
            } else {
                // If not returning items, just delete the order (which cascades to storage)
                ordersDatabase.deleteOrder(order.getOrderId());
                itemFuture = CompletableFuture.completedFuture(new ArrayList<>()); // --- FIX: Class now found ---
            }

            // 4. When items are loaded (or not), process payment/delivery
            itemFuture.whenCompleteAsync((loadedItems, itemError) -> {
                if (itemError != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<red>Failed to load stored items. Refund not processed.</red>"));
                    logger.logError("AdminCancel failed to load items", itemError, order.getOrderId());
                    return;
                }

                // Final values
                double finalRefund = doRefund ? refundAmount : 0.0;
                List<ItemStack> finalItems = doReturnItems ? loadedItems : new ArrayList<>();

                // 5. Log the action
                logger.logAdminCancel(admin, order, finalRefund, finalItems.stream().mapToInt(ItemStack::getAmount).sum());

                // 6. Check if player is online
                OfflinePlayer placer = Bukkit.getOfflinePlayer(order.getPlacerUUID());
                if (placer.isOnline()) {
                    Player onlinePlacer = placer.getPlayer();
                    // Process online
                    if (finalRefund > 0.01) {
                        OrderModule.getEconomy().depositPlayer(onlinePlacer, finalRefund);
                    }
                    if (!finalItems.isEmpty()) {
                        HashMap<Integer, ItemStack> failed = onlinePlacer.getInventory().addItem(finalItems.toArray(new ItemStack[0])); // --- FIX: Class now found ---
                        failed.values().forEach(item -> onlinePlacer.getWorld().dropItemNaturally(onlinePlacer.getLocation(), item));
                    }

                    // Notify placer
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        messageHandler.sendMessage(onlinePlacer, "<red>Your order (ID: " + order.getOrderId() + ") was forcibly cancelled by an administrator.</red>");
                        if (doRefund) messageHandler.sendMessage(onlinePlacer, "<green>The remaining funds (" + OrderModule.getEconomy().format(finalRefund) + ") have been refunded.</green>");
                        if (doReturnItems) messageHandler.sendMessage(onlinePlacer, "<green>The partially filled items have been returned to your inventory.</green>");
                    });
                } else {
                    // (Point 5) Process offline
                    offlineDeliveryManager.scheduleDelivery(order.getPlacerUUID(), finalRefund, finalItems);
                }

                // 7. Notify admin
                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(admin, "<green>Successfully cancelled order " + order.getOrderId() + ".</green>"));

            });

        });
    }

    // --- GUI Item Helpers ---

    private ItemStack createDisplayItem(Order order) {
        ItemStack item = order.getItem();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = new ArrayList<>();

        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<red><bold>PLACER: " + order.getPlacerName() + "</bold>"));
        lore.add(mm.deserialize("<gray>---------------</gray>"));
        lore.add(mm.deserialize("<aqua>Order Info</aqua>"));
        lore.add(mm.deserialize("<dark_purple>✍</dark_purple> <gray>Want: <yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName()));
        lore.add(mm.deserialize("<green>$</green> <gray>Price: <yellow>" + OrderGuiUtils.formatMoney(order.getPricePerItem()) + " Each"));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<aqua>Order Status</aqua>"));
        lore.add(mm.deserialize("<gold>⚒</gold> <gray>Got: <yellow>" + OrderGuiUtils.formatAmount(order.getAmountDelivered()) + "</yellow>/<yellow>" + OrderGuiUtils.formatAmount(order.getTotalAmount())));
        lore.add(mm.deserialize("<green>$</green> <gray>Total Value: <yellow>" + OrderGuiUtils.formatMoney(order.getTotalPrice())));
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<red><bold>► Shift-Click to Force Cancel</bold>"));

        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createToggleItem(boolean state, boolean isRefund) {
        Material material = state ? Material.LIME_DYE : Material.GRAY_DYE;
        Component name = isRefund ?
                mm.deserialize(state ? "<green><bold>Refund Money: ON</bold>" : "<red><bold>Refund Money: OFF</bold>") :
                mm.deserialize(state ? "<green><bold>Return Items: ON</bold>" : "<red><bold>Return Items: OFF</bold>");

        List<Component> lore = new ArrayList<>();
        if (isRefund) {
            lore.add(mm.deserialize("<gray>If ON, the placer will be refunded"));
            lore.add(mm.deserialize("<gray>the value of the <bold>unfilled</bold> portion."));
        } else {
            lore.add(mm.deserialize("<gray>If ON, the placer will receive"));
            lore.add(mm.deserialize("<gray>all <bold>partially filled</bold> items."));
        }
        lore.add(mm.deserialize(" "));
        lore.add(mm.deserialize("<yellow>Click to toggle.</yellow>"));

        ItemStack item = OrderGuiUtils.createGuiItem(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(toggleKey, PersistentDataType.BYTE, (byte) (isRefund ? (state ? 1 : 0) : (state ? 2 : 0)));
        item.setItemMeta(meta);
        return item;
    }

    private boolean getToggleState(ItemStack item, boolean isRefund) {
        if (item == null || item.getItemMeta() == null) return true; // Default to on
        byte val = item.getItemMeta().getPersistentDataContainer().getOrDefault(toggleKey, PersistentDataType.BYTE, (byte) (isRefund ? 1 : 2));
        return isRefund ? val == 1 : val == 2;
    }
}