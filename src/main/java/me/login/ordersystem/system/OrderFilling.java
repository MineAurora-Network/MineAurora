package me.login.ordersystem.system;

import me.login.Login;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.Order; // --- FIX: Correct import ---
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import ---
import me.login.ordersystem.util.OrderLogger;
import me.login.ordersystem.util.OrderMessageHandler;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;

/**
 * Handles the logic for a player filling another player's order.
 * (Refactored for Points 1, 2, 6, 11, 13)
 */
public class OrderFilling {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private static Economy economy = null;
    private final OrderMessageHandler messageHandler;
    private final OrderLogger logger;

    public OrderFilling(Login plugin, OrderSystem orderSystem, OrdersDatabase ordersDatabase, OrderMessageHandler messageHandler, OrderLogger logger) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = ordersDatabase;
        this.messageHandler = messageHandler;
        this.logger = logger;

        // (Point 13) Use the static economy instance from OrderModule
        if (OrderFilling.economy == null) {
            OrderFilling.economy = OrderModule.getEconomy();
        }
    }

    public static Economy getEconomy() {
        return economy;
    }

    public void startFillingProcess(Player filler, Order order, int currentPage) {
        if (economy == null) {
            messageHandler.sendMessage(filler, "<red>Economy system offline.</red>");
            return;
        }
        if (order == null) {
            messageHandler.sendMessage(filler, "<red>Error: Order details missing.</red>");
            return;
        }

        if (order.getStatus() != Order.OrderStatus.ACTIVE || order.isExpired()) {
            messageHandler.sendMessage(filler, "<red>Order is no longer active. Refreshing list...</red>");
            // --- FIX: Use plugin.getOrderMenu() ---
            plugin.getOrderMenu().openMenuListGui(filler, currentPage);
            return;
        }
        if (order.getPlacerUUID().equals(filler.getUniqueId())) {
            messageHandler.sendMessage(filler, "<red>You cannot fill your own order!</red>");
            return;
        }

        int needed = order.getTotalAmount() - order.getAmountDelivered();
        if (needed <= 0) {
            messageHandler.sendMessage(filler, "<red>This order is already filled. Refreshing list...</red>");
            // --- FIX: Use plugin.getOrderMenu() ---
            plugin.getOrderMenu().openMenuListGui(filler, currentPage);
            return;
        }

        ItemStack itemToFill = order.getItem();

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!filler.isOnline()) return;

                int amountPlayerHas = countItems(filler.getInventory(), itemToFill);
                if (amountPlayerHas <= 0) {
                    messageHandler.sendMessage(filler, "<red>You have no " + order.getFormattedItemName() + ".</red>");
                    return;
                }

                int amountToTake = Math.min(needed, amountPlayerHas);
                double payment = amountToTake * order.getPricePerItem();

                messageHandler.sendMessage(filler, "<yellow>Delivering " + amountToTake + " " + order.getFormattedItemName() + "...</yellow>");

                // 1. Remove items
                if (!removeItems(filler.getInventory(), itemToFill, amountToTake)) {
                    messageHandler.sendMessage(filler, "<red>Failed to remove items. Ensure you have them.</red>");
                    return;
                }

                // 2. Pay the filler
                EconomyResponse depositResponse = economy.depositPlayer(filler, payment);
                if (!depositResponse.transactionSuccess()) {
                    messageHandler.sendMessage(filler, "<red>Error giving you payment: " + depositResponse.errorMessage + "</red>");
                    giveItems(filler, itemToFill, amountToTake); // Rollback items
                    logger.logError("CRITICAL PAYMENT FAILURE: Order "+order.getOrderId()+" F:"+filler.getName()+" P:"+order.getPlacerName()+" A:"+payment+".", null, order.getOrderId());
                    return;
                }

                // 3. Store items for placer
                ItemStack deliveredItems = itemToFill.clone();
                deliveredItems.setAmount(amountToTake);
                ordersDatabase.addItemsToStorage(order.getOrderId(), order.getPlacerUUID(), deliveredItems)
                        .whenCompleteAsync((storageSuccess, storageError) -> {
                            if (storageError != null || !storageSuccess) {
                                // Critical error: Rollback payment and give items back
                                economy.withdrawPlayer(filler, payment);
                                Bukkit.getScheduler().runTask(plugin, () -> giveItems(filler, itemToFill, amountToTake));
                                logger.logError("CRITICAL STORAGE FAILURE: Order " + order.getOrderId() + ". Rolled back payment/items for " + filler.getName(), storageError, order.getOrderId());
                                Bukkit.getScheduler().runTask(plugin, () -> messageHandler.sendMessage(filler, "<red>A critical error occurred storing the items. Transaction rolled back.</red>"));
                                return;
                            }

                            // --- Storage succeeded, now update order status ---
                            int newAmountDelivered = order.getAmountDelivered() + amountToTake;
                            order.setAmountDelivered(newAmountDelivered);
                            Order.OrderStatus finalStatus = order.getStatus(); // setAmountDelivered updates status

                            ordersDatabase.updateOrderAmountAndStatus(order.getOrderId(), newAmountDelivered, finalStatus)
                                    .whenCompleteAsync((dbSuccess, dbError) -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (dbError != null || !dbSuccess) {
                                                logger.logError("CRITICAL DB UPDATE FAILED: Order " + order.getOrderId() + " after fill by " + filler.getName(), dbError, order.getOrderId());
                                            }

                                            messageHandler.sendMessage(filler, "<green>Delivered " + amountToTake + "! Received " + economy.format(payment) + ".</green>");

                                            if (finalStatus == Order.OrderStatus.FILLED) {
                                                Player placerOnline = Bukkit.getPlayer(order.getPlacerUUID());
                                                if (placerOnline != null && placerOnline.isOnline()) {
                                                    messageHandler.sendMultiLine(placerOnline, "<aqua>Your order for " + order.getTotalAmount() + " " + order.getFormattedItemName() + " (ID: "+order.getOrderId()+") is now fully filled!%nl%You can claim your items in /order manage.</aqua>");
                                                }
                                            }

                                            // (Point 11) Log to Discord
                                            logger.logFill(filler, order, amountToTake, payment);

                                            // Refresh GUI for the filler
                                            // --- FIX: Use plugin.getOrderMenu() ---
                                            plugin.getOrderMenu().openMenuListGui(filler, 0); // Go back to page 0
                                        });
                                    });
                        });
            }
        }.runTask(plugin);
    }

    // --- Inventory Helpers (Unchanged) ---
    private int countItems(PlayerInventory inventory, ItemStack itemToCount) { int c=0; ItemStack check=itemToCount.clone(); check.setAmount(1); for(ItemStack i:inventory.getStorageContents()){if(i!=null&&i.isSimilar(check)){c+=i.getAmount();}} ItemStack off=inventory.getItemInOffHand(); if(off!=null&&off.isSimilar(check)){c+=off.getAmount();} return c; }

    private boolean removeItems(PlayerInventory inventory, ItemStack itemToRemove, int amount) {
        ItemStack check = itemToRemove.clone();
        check.setAmount(1);
        int remainingToRemove = amount;

        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && offHand.isSimilar(check)) {
            int inOffHand = offHand.getAmount();
            if (inOffHand > remainingToRemove) {
                offHand.setAmount(inOffHand - remainingToRemove);
                return true;
            } else {
                inventory.setItemInOffHand(null);
                remainingToRemove -= inOffHand;
            }
        }
        if (remainingToRemove <= 0) return true;
        ItemStack toRemoveFromStorage = itemToRemove.clone();
        toRemoveFromStorage.setAmount(remainingToRemove);
        HashMap<Integer, ItemStack> failed = inventory.removeItem(toRemoveFromStorage);
        return failed.isEmpty();
    }

    private void giveItems(Player player, ItemStack itemToGive, int amount) {
        ItemStack give=itemToGive.clone();
        give.setAmount(amount);
        HashMap<Integer,ItemStack> failed=player.getInventory().addItem(give);
        failed.values().forEach(item->player.getWorld().dropItemNaturally(player.getLocation(),item));
        if(!failed.isEmpty()) {
            messageHandler.sendMessage(player, "<yellow>Some items couldn't fit and were dropped.</yellow>");
        }
    }
}