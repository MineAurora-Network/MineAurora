package me.login.ordersystem;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.logging.Level;

public class OrderFilling {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private static Economy economy = null;

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    public OrderFilling(Login plugin, OrderSystem orderSystem) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = plugin.getOrdersDatabase();

        // --- CORRECTED LINE (Solution 2) ---
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        if (!setupEconomy()) {
            plugin.getLogger().severe("Order Filling disabled due to missing Vault/Economy plugin.");
        }
    }

    private boolean setupEconomy() { if (economy != null) return true; if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) { plugin.getLogger().severe("Vault not found!"); return false; } RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class); if (rsp == null) { plugin.getLogger().severe("No Economy plugin found by Vault!"); return false; } economy = rsp.getProvider(); plugin.getLogger().info("Hooked Vault Economy: " + economy.getName()); return true; }

    public static Economy getEconomy() {
        return economy;
    }

    // --- MODIFIED: Use Kyori prefix ---
    public void startFillingProcess(Player filler, Order order, int currentPage) {
        if (economy == null) {
            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Economy system offline.</red>")));
            return;
        }
        if (order == null) {
            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error: Order details missing.</red>")));
            return;
        }

        if (order.getStatus() != Order.OrderStatus.ACTIVE || order.isExpired()) {
            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order is no longer active. Refreshing list...</red>")));
            // Refresh main menu, not search menu, if fill fails
            plugin.getOrderMenu().openMenuListGui(filler, currentPage);
            return;
        }
        if (order.getPlacerUUID().equals(filler.getUniqueId())) {
            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Cannot fill own order!</red>")));
            return;
        }

        int needed = order.getTotalAmount() - order.getAmountDelivered();
        if (needed <= 0) {
            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order already filled. Refreshing list...</red>")));
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
                    filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You have no " + order.getFormattedItemName() + ".</red>")));
                    return;
                }

                int amountToTake = Math.min(needed, amountPlayerHas);
                double payment = amountToTake * order.getPricePerItem();

                filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>Delivering " + amountToTake + " " + order.getFormattedItemName() + "...</yellow>")));

                // 1. Remove items
                if (!removeItems(filler.getInventory(), itemToFill, amountToTake)) {
                    filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Failed to remove items. Ensure you have them.</red>")));
                    return;
                }

                // 2. Pay the filler
                EconomyResponse depositResponse = economy.depositPlayer(filler, payment);
                if (!depositResponse.transactionSuccess()) {
                    filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error giving you payment: " + depositResponse.errorMessage + "</red>")));
                    giveItems(filler, itemToFill, amountToTake); // Rollback items
                    String logMsg = "CRITICAL PAYMENT FAILURE: Order "+order.getOrderId()+" F:"+filler.getName()+" P:"+order.getPlacerName()+" A:"+payment+".";
                    plugin.getLogger().severe(logMsg); orderSystem.sendLog(logMsg);
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
                                String logMsg = "CRITICAL STORAGE FAILURE: Order " + order.getOrderId() + ". Rolled back payment/items for " + filler.getName() + ". Error: " + (storageError != null ? storageError.getMessage() : "N/A");
                                plugin.getLogger().severe(logMsg); orderSystem.sendLog(logMsg);
                                Bukkit.getScheduler().runTask(plugin, () -> filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>A critical error occurred storing the items. Transaction rolled back.</red>"))));
                                return;
                            }

                            // --- Storage succeeded, now update order status ---
                            int newAmountDelivered = order.getAmountDelivered() + amountToTake;
                            order.setAmountDelivered(newAmountDelivered);
                            Order.OrderStatus finalStatus = order.getStatus();

                            ordersDatabase.updateOrderAmountAndStatus(order.getOrderId(), newAmountDelivered, finalStatus)
                                    .whenCompleteAsync((dbSuccess, dbError) -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (dbError != null || !dbSuccess) {
                                                String logMsg = "CRITICAL DB UPDATE FAILED: Order " + order.getOrderId() + " after fill by " + filler.getName() + "!";
                                                plugin.getLogger().severe(logMsg); orderSystem.sendLog(logMsg);
                                            } else {
                                                plugin.getLogger().info("Order " + order.getOrderId() + " DB updated after fill.");
                                            }

                                            filler.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Delivered " + amountToTake + "! Received " + economy.format(payment) + ".</green>")));

                                            if (finalStatus == Order.OrderStatus.FILLED) {
                                                Player placerOnline = Bukkit.getPlayer(order.getPlacerUUID());
                                                if (placerOnline != null && placerOnline.isOnline()) {
                                                    placerOnline.sendMessage(serverPrefix.append(miniMessage.deserialize("<aqua>Your order for " + order.getTotalAmount() + " " + order.getFormattedItemName() + " (ID: "+order.getOrderId()+") is now fully filled!</aqua>")));
                                                    placerOnline.sendMessage(serverPrefix.append(miniMessage.deserialize("<aqua>You can claim your items in /order manage.</aqua>")));
                                                }
                                            }

                                            orderSystem.sendLog("Fill: " + filler.getName() + " -> " + amountToTake + "x " + itemToFill.getType() + " for " + order.getPlacerName() + " (" + order.getOrderId() + ") Payout: " + economy.format(payment));

                                            // Refresh GUI for the filler
                                            // This is tricky. If they were in the search menu, we can't easily reopen it.
                                            // For simplicity, we just reopen the main menu.
                                            plugin.getOrderMenu().openMenuListGui(filler, 0); // Go back to page 0
                                        });
                                    });

                        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));

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

        if (remainingToRemove <= 0) {
            return true;
        }

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
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>Some items couldn't fit and were dropped.</yellow>")));
        }
        plugin.getLogger().warning("Rolled back item removal for " + player.getName() + ", some items dropped.");
    }
}