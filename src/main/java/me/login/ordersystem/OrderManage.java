package me.login.ordersystem;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class OrderManage implements Listener {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private static Economy economy = null;

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    private final Map<UUID, List<Order>> playerOrderCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> playerCacheTime = new ConcurrentHashMap<>();
    private final long CACHE_DURATION_MS = 15 * 1000;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45;

    private static final DecimalFormat largeNumFormat = new DecimalFormat("#,###");
    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0");

    private final NamespacedKey orderIdKey;
    private final NamespacedKey itemsClaimedKey;


    public OrderManage(Login plugin, OrderSystem orderSystem) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = plugin.getOrdersDatabase();

        // --- CORRECTED LINE (Solution 2) ---
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        this.orderIdKey = new NamespacedKey(plugin, "manage_order_id");
        this.itemsClaimedKey = new NamespacedKey(plugin, "manage_items_claimed");
        if (!setupEconomy()) {
            plugin.getLogger().warning("OrderManage failed to link Vault/Economy.");
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean setupEconomy() {
        if (economy != null) return true;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found for OrderManage!");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("No Economy plugin found by Vault for OrderManage!");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
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
        UUID playerUUID = player.getUniqueId();
        getPlayerOrders(playerUUID, false).whenCompleteAsync((playerOrders, error) -> {
            if (error != null) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error loading your orders.</red>")));
                plugin.getLogger().log(Level.SEVERE, "Failed to load orders for " + player.getName(), error);
                return;
            }
            if (playerOrders.isEmpty()) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>You have no orders.</yellow>")));
                return;
            }

            int totalOrders = playerOrders.size(); int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE); int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

            Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.GRAY + "Manage Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");

            int startIndex = finalPage * ORDERS_PER_PAGE; int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
            List<Order> pageOrders = playerOrders.subList(startIndex, endIndex);

            List<CompletableFuture<Boolean>> claimCheckFutures = new ArrayList<>();
            Map<Long, Boolean> itemsClaimedStatus = new ConcurrentHashMap<>();

            for (Order order : pageOrders) {
                if (order.getAmountDelivered() > 0 && order.getStatus() != Order.OrderStatus.CANCELLED) {
                    claimCheckFutures.add(
                            ordersDatabase.hasStoredItems(order.getOrderId()).thenApply(hasItems -> {
                                itemsClaimedStatus.put(order.getOrderId(), !hasItems);
                                return true;
                            })
                    );
                } else {
                    itemsClaimedStatus.put(order.getOrderId(), true);
                }
            }

            CompletableFuture.allOf(claimCheckFutures.toArray(new CompletableFuture[0])).whenCompleteAsync((v, claimError) -> {
                if (claimError != null) {
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error checking item claim status.</red>")));
                    plugin.getLogger().log(Level.SEVERE, "Failed to check claim status for " + player.getName(), claimError);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (int i = 0; i < pageOrders.size(); i++) {
                        Order order = pageOrders.get(i);
                        boolean claimed = itemsClaimedStatus.getOrDefault(order.getOrderId(), true);
                        gui.setItem(i, createManageDisplayItem(order, claimed));
                    }

                    ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null); for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);
                    if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null));
                    gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null)); gui.setItem(50, OrderSystem.createGuiItem(Material.CLOCK, "§bRefresh List", null));
                    if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null));

                    player.setMetadata(OrderSystem.GUI_MANAGE_METADATA, new FixedMetadataValue(plugin, finalPage));
                    player.openInventory(gui);
                });
            }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));

        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- Create Item (Unchanged) ---
    private ItemStack createManageDisplayItem(Order order, boolean itemsClaimed) {
        ItemStack displayItem = order.getItem().clone(); ItemMeta meta = displayItem.getItemMeta(); if (meta == null) return displayItem; List<String> lore = new ArrayList<>(); lore.add(" "); String statusColor; List<String> actions = new ArrayList<>();

        boolean canHaveItems = order.getAmountDelivered() > 0;

        switch (order.getStatus()) {
            case ACTIVE:
                statusColor = "§aACTIVE";
                actions.add("§c▶ Shift-Click to Cancel");
                if (canHaveItems && !itemsClaimed) actions.add("§a▶ Right-Click to Claim Items");
                break;
            case FILLED:
                statusColor = "§bFILLED";
                if (canHaveItems && !itemsClaimed) {
                    actions.add("§a▶ Right-Click to Claim Items");
                    actions.add("§c▶ Shift-Click (Needs Claim First)");
                } else {
                    actions.add("§a✔ Order Filled & Claimed");
                    actions.add("§c▶ Shift-Click to Remove");
                }
                break;
            case EXPIRED:
                statusColor = "§7EXPIRED";
                if (canHaveItems) {
                    actions.add("§c▶ Shift-Click to Cancel & Get Items");
                    if (!itemsClaimed) actions.add("§a▶ Right-Click to Claim Items");
                } else {
                    actions.add("§c▶ Shift-Click to Remove");
                }
                break;
            case CANCELLED:
                statusColor = "§cCANCELLED";
                actions.add("§c▶ Shift-Click to Remove");
                if (canHaveItems) actions.add("§e(Items were returned on cancel)");
                break;
            default:
                statusColor = "§fUNKNOWN";
                break;
        }
        lore.add("§fStatus: " + statusColor); lore.add(" "); lore.add("§bOrder Info");
        lore.add("§d✍ §7Want: §e" + formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName());
        lore.add("§a$ §7Price: §e" + formatMoney(order.getPricePerItem()) + " Each"); lore.add(" "); lore.add("§bProgress"); lore.add("§6⚒ §7Got: §e" + formatAmount(order.getAmountDelivered()) + "§7/§e" + formatAmount(order.getTotalAmount()));
        lore.add("§a$ §7Paid: §e" + formatMoney(order.getTotalPrice()));
        if (order.getStatus() == Order.OrderStatus.ACTIVE) lore.add("§9⏱ §7Expires: §e" + order.getFormattedExpiryTimeLeft()); else lore.add("§9⏱ §7Created: §e" + new SimpleDateFormat("yyyy-MM-dd").format(new Date(order.getCreationTimestamp())));
        lore.add(" "); lore.addAll(actions);
        meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId());
        meta.getPersistentDataContainer().set(itemsClaimedKey, PersistentDataType.BYTE, (byte)(itemsClaimed ? 1 : 0));
        meta.setLore(lore); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); displayItem.setItemMeta(meta); return displayItem;
    }


    // --- Click Handler (Unchanged) ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(OrderSystem.GUI_MANAGE_METADATA)) return;
        if (!event.getView().getTitle().startsWith(ChatColor.GRAY + "Manage Orders")) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem(); if (clickedItem == null || clickedItem.getType() == Material.AIR) return;
        int currentPage = player.getMetadata(OrderSystem.GUI_MANAGE_METADATA).get(0).asInt(); int slot = event.getSlot(); ClickType clickType = event.getClick();

        if (slot >= 45) { // Bottom row logic
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) player.closeInventory();
            else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) openManageGui(player, currentPage - 1);
                else if (slot == 53) {
                    getPlayerOrders(player.getUniqueId(), false).whenCompleteAsync((orders, error) -> {
                        if (error == null) {
                            int totalPages = (int) Math.ceil((double) orders.size() / ORDERS_PER_PAGE);
                            if (currentPage < totalPages - 1) {
                                Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage + 1));
                            }
                        }
                    });
                }
            }
            else if (type == Material.CLOCK && slot == 50) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Refreshing...</green>")));
                getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                );
            }
        } else { // Order item clicked
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
                long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);
                boolean itemsClaimed = meta.getPersistentDataContainer().getOrDefault(itemsClaimedKey, PersistentDataType.BYTE, (byte)1) == 1;

                List<Order> cachedOrders = playerOrderCache.get(player.getUniqueId());
                Order order = null;
                if (cachedOrders != null) {
                    order = cachedOrders.stream().filter(o -> o.getOrderId() == orderId).findFirst().orElse(null);
                }

                if (order != null) {
                    if (clickType.isShiftClick()) {
                        handleCancelOrRemove(player, order, itemsClaimed, currentPage);
                    } else if (clickType.isRightClick()) {
                        handleClaimItems(player, order, itemsClaimed, currentPage);
                    }
                } else { player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order data not found. Refreshing...</red>"))); getPlayerOrders(player.getUniqueId(), true).thenRun(() -> Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))); }
            } else { player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error identifying order.</red>"))); }
        }
    }

    // --- Click Logic (Unchanged) ---
    private void handleClaimItems(Player player, Order order, boolean itemsClaimed, int currentPage) {
        if (itemsClaimed) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>Items have already been claimed or returned.</yellow>")));
            return;
        }
        if (order.getAmountDelivered() == 0) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>No items have been delivered yet.</yellow>")));
            return;
        }
        if (order.getStatus() == Order.OrderStatus.CANCELLED) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>This order was cancelled. Items were returned then.</yellow>")));
            return;
        }

        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Attempting to claim delivered items...</green>")));
        giveStoredItemsToPlayer(player, order.getOrderId(), () -> {
            openManageGui(player, currentPage);
        });
    }

    private void handleCancelOrRemove(Player player, Order order, boolean itemsClaimed, int currentPage) {
        Order.OrderStatus currentStatus = order.getStatus();

        boolean isRemovable = currentStatus == Order.OrderStatus.CANCELLED ||
                (currentStatus == Order.OrderStatus.EXPIRED && order.getAmountDelivered() == 0) ||
                (currentStatus == Order.OrderStatus.FILLED && itemsClaimed);

        if (isRemovable) {
            ordersDatabase.deleteOrder(order.getOrderId()).whenCompleteAsync((success, error) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Removed order listing.</green>")));
                        orderSystem.sendLog("Remove: " + player.getName() + " removed listing " + order.getOrderId());
                        getPlayerOrders(player.getUniqueId(), true).thenRun(() ->
                                Bukkit.getScheduler().runTask(plugin, () -> openManageGui(player, currentPage))
                        );
                    } else {
                        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error removing listing.</red>")));
                        orderSystem.sendLog("Remove Fail DB: " + player.getName() + " O:" + order.getOrderId() + " E:"+(error!=null?error.getMessage():"DelFail"));
                    }
                });
            });
        }
        else if (currentStatus == Order.OrderStatus.ACTIVE || (currentStatus == Order.OrderStatus.EXPIRED && order.getAmountDelivered() > 0)) {
            ordersDatabase.updateOrderStatus(order.getOrderId(), Order.OrderStatus.CANCELLED).whenCompleteAsync((success, error) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (success) {
                        order.setStatus(Order.OrderStatus.CANCELLED);
                        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Order cancelled.</green>")));
                        orderSystem.sendLog("Cancel: " + player.getName() + " cancelled order " + order.getOrderId());

                        double refundAmount = (order.getTotalAmount() - order.getAmountDelivered()) * order.getPricePerItem();
                        if (refundAmount > 0.01) {
                            if (economy != null) {
                                EconomyResponse refundResp = economy.depositPlayer(player, refundAmount);
                                if (refundResp.transactionSuccess()) {
                                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Refunded " + economy.format(refundAmount) + " for the unfilled portion.</green>")));
                                    orderSystem.sendLog("Refund (Cancel): " + player.getName() + " order " + order.getOrderId() + " amount " + economy.format(refundAmount));
                                } else {
                                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order cancelled, but failed to process refund: " + refundResp.errorMessage + "</red>")));
                                    orderSystem.sendLog("REFUND FAILED (Cancel): " + player.getName() + " O:" + order.getOrderId() + " A:" + refundAmount + " E:" + refundResp.errorMessage);
                                }
                            }
                        }

                        if (order.getAmountDelivered() > 0 && !itemsClaimed) {
                            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Returning partially delivered items...</green>")));
                            giveStoredItemsToPlayer(player, order.getOrderId(), () -> {
                                openManageGui(player, currentPage);
                            });
                        } else {
                            openManageGui(player, currentPage);
                        }

                    } else { /* Error cancelling message */ }
                });
            });
        }
        else if (currentStatus == Order.OrderStatus.FILLED && !itemsClaimed) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You must claim the delivered items (Right-Click) before removing this order.</red>")));
        }
        else { player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Cannot cancel/remove order in its current state.</red>"))); }
    }

    private void giveStoredItemsToPlayer(Player player, long orderId, Runnable successCallback) {
        ordersDatabase.loadAndRemoveStoredItems(orderId).whenCompleteAsync((items, error) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (error != null) {
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error claiming items! Contact an admin.</red>")));
                    orderSystem.sendLog("CRITICAL CLAIM ERROR DB: Player " + player.getName() + " O:" + orderId + " E:" + error.getMessage());
                    return;
                }
                if (items == null || items.isEmpty()) {
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>No stored items were found for this order. They might have already been claimed or returned.</yellow>")));
                    if (successCallback != null) successCallback.run();
                    return;
                }

                HashMap<Integer, ItemStack> failed = player.getInventory().addItem(items.toArray(new ItemStack[0]));
                int totalClaimed = items.stream().mapToInt(ItemStack::getAmount).sum();

                if (!failed.isEmpty()) {
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>Your inventory was full! Some items were dropped at your feet.</yellow>")));
                    for (ItemStack drop : failed.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Successfully claimed " + totalClaimed + " items!</green>")));
                orderSystem.sendLog("Claim Items: " + player.getName() + " claimed " + totalClaimed + " items from order " + orderId);

                if (successCallback != null) successCallback.run();
            });
        });
    }


    // --- Helpers (Unchanged) ---
    private String getItemDisplayName(ItemStack item) { if (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName()) return item.getItemMeta().getDisplayName(); String name = (item != null ? item.getType().name() : "Unknown").toLowerCase().replace("_", " "); return name.substring(0, 1).toUpperCase() + name.substring(1); }
    private String formatAmount(int amount) { if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0); if (amount >= 1000) return String.format("%.0fK", amount / 1000.0); return largeNumFormat.format(amount); }
    private String formatMoney(double amount) { if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0); if (amount >= 1000) return String.format("%.0fK", amount / 1000.0); return moneyFormat.format(amount); }
}