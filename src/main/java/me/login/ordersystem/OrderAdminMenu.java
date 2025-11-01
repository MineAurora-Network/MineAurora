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
import java.util.stream.Collectors;

public class OrderAdminMenu implements Listener {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private static Economy economy = null;

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    // Use the same cache as OrderMenu to keep things in sync
    private final Map<Long, Order> activeOrdersCache;
    private final long CACHE_DURATION_MS = 30 * 1000; // 30 seconds
    private long lastCacheRefresh = 0;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45;
    private static final DecimalFormat largeNumFormat = new DecimalFormat("#,###");
    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0");
    private final NamespacedKey orderIdKey;

    public OrderAdminMenu(Login plugin, OrderSystem orderSystem) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = plugin.getOrdersDatabase();

        // --- CORRECTED LINE (Solution 2) ---
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        // We can re-use the OrderMenu's key, as it's just for GUI clicks
        this.orderIdKey = new NamespacedKey(plugin, "menu_order_id");

        this.activeOrdersCache = new ConcurrentHashMap<>(); // Admin menu will manage its own cache of *all* active orders

        setupEconomy();
    }

    // --- Economy Setup (Copied from OrderManage) ---
    private boolean setupEconomy() {
        if (economy != null) return true;
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            plugin.getLogger().severe("Vault not found for OrderAdminMenu!");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            plugin.getLogger().severe("No Economy plugin found by Vault for OrderAdminMenu!");
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    // --- Cache Handling ---
    private CompletableFuture<List<Order>> getActiveOrders(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && (now - lastCacheRefresh < CACHE_DURATION_MS) && !activeOrdersCache.isEmpty()) {
            return CompletableFuture.completedFuture(new ArrayList<>(activeOrdersCache.values()));
        }

        return ordersDatabase.loadActiveOrders().thenApply(orders -> {
            activeOrdersCache.clear();
            if (orders != null) {
                orders.forEach(order -> activeOrdersCache.put(order.getOrderId(), order));
            }
            lastCacheRefresh = System.currentTimeMillis();
            plugin.getLogger().info("Refreshed admin cache, loaded " + activeOrdersCache.size() + " active orders.");
            // Sort by creation time, newest first
            return orders.stream()
                    .sorted(Comparator.comparingLong(Order::getCreationTimestamp).reversed())
                    .collect(Collectors.toList());
        });
    }


    // --- GUI Opening ---
    public void openAdminGui(Player player, int page) {
        getActiveOrders(false).whenCompleteAsync((activeOrders, error) -> {
            if (error != null) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error loading active orders for admin menu.</red>")));
                plugin.getLogger().log(Level.SEVERE, "Failed to load orders for admin " + player.getName(), error);
                return;
            }
            if (activeOrders.isEmpty()) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>There are currently no active orders.</yellow>")));
                return;
            }

            int totalOrders = activeOrders.size();
            int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
            int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

            // Use DARK_RED to distinguish admin menu
            Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_RED + "Admin: Active Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");

            int startIndex = finalPage * ORDERS_PER_PAGE;
            int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
            List<Order> pageOrders = activeOrders.subList(startIndex, endIndex);

            Bukkit.getScheduler().runTask(plugin, () -> {
                for (int i = 0; i < pageOrders.size(); i++) {
                    gui.setItem(i, createAdminDisplayItem(pageOrders.get(i)));
                }

                ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
                for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

                if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null));
                gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null));
                gui.setItem(50, OrderSystem.createGuiItem(Material.CLOCK, "§bRefresh List (Force)", null));
                if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null));

                player.setMetadata(OrderSystem.GUI_ADMIN_METADATA, new FixedMetadataValue(plugin, finalPage));
                player.openInventory(gui);
            });

        }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable)); // Run GUI opening on main
    }

    private ItemStack createAdminDisplayItem(Order order) {
        ItemStack displayItem = order.getItem().clone();
        ItemMeta meta = displayItem.getItemMeta();
        if (meta == null) return displayItem;
        List<String> lore = new ArrayList<>();

        lore.add(" ");
        lore.add("§c§lPLACER: " + order.getPlacerName());
        lore.add("§8---------------");
        lore.add("§bOrder Info");
        lore.add("§d✍ §7Want: §e" + formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName());
        lore.add("§a$ §7Price: §e" + formatMoney(order.getPricePerItem()) + " Each");
        lore.add(" ");
        lore.add("§bOrder Status");
        lore.add("§6⚒ §7Got: §e" + formatAmount(order.getAmountDelivered()) + "§7/§e" + formatAmount(order.getTotalAmount()));
        lore.add("§a$ §7Total Value: §e" + formatMoney(order.getTotalPrice()));
        lore.add("§9⏱ §7Expires: §e" + order.getFormattedExpiryTimeLeft());
        lore.add(" ");
        lore.add("§c§l► Shift-Click to Force Cancel");

        meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId());
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        displayItem.setItemMeta(meta);
        return displayItem;
    }

    // --- Click Handler ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(OrderSystem.GUI_ADMIN_METADATA)) return;
        if (!event.getView().getTitle().startsWith(ChatColor.DARK_RED + "Admin: Active Orders")) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int currentPage = player.getMetadata(OrderSystem.GUI_ADMIN_METADATA).get(0).asInt();
        int slot = event.getSlot();
        ClickType clickType = event.getClick();

        if (slot >= 45) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) {
                player.closeInventory();
            } else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) openAdminGui(player, currentPage - 1);
                else if (slot == 53) {
                    // Recalculate total pages
                    getActiveOrders(false).whenCompleteAsync((orders, err) -> {
                        if (err == null) {
                            int totalPages = (int) Math.ceil((double) orders.size() / ORDERS_PER_PAGE);
                            if (currentPage < totalPages - 1) {
                                Bukkit.getScheduler().runTask(plugin, () -> openAdminGui(player, currentPage + 1));
                            }
                        }
                    });
                }
            } else if (type == Material.CLOCK && slot == 50) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Forcing cache refresh...</green>")));
                getActiveOrders(true).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> openAdminGui(player, currentPage))
                );
            }
        } else { // Order item clicked
            if (clickType.isShiftClick()) {
                handleAdminCancel(player, clickedItem, currentPage);
            } else {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You must Shift-Click to cancel an order.</red>")));
            }
        }
    }

    // --- Admin Cancel Logic ---
    private void handleAdminCancel(Player admin, ItemStack clickedItem, int currentPage) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null || !meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
            admin.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error identifying order.</red>")));
            return;
        }

        long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);
        Order order = activeOrdersCache.get(orderId);

        if (order == null) {
            admin.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order data not found in cache. Refreshing...</red>")));
            getActiveOrders(true).thenRun(() ->
                    Bukkit.getScheduler().runTask(plugin, () -> openAdminGui(admin, currentPage))
            );
            return;
        }

        // 1. Calculate refund
        // The money to refund is the amount *not* yet paid out to fillers.
        double refundAmount = (order.getTotalAmount() - order.getAmountDelivered()) * order.getPricePerItem();

        // 2. Delete the order from the database
        // This will also delete associated stored items, which is what we want.
        // We are NOT giving the partially-filled items back.
        ordersDatabase.deleteOrder(orderId).whenCompleteAsync((deleteSuccess, deleteError) -> {
            if (deleteError != null || !deleteSuccess) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    admin.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Failed to delete order from database. Aborting.</red>")));
                    plugin.getLogger().severe("Admin " + admin.getName() + " failed to delete order " + orderId + ": " + (deleteError != null ? deleteError.getMessage() : "N/A"));
                });
                return;
            }

            // 3. Database deletion successful, remove from cache
            activeOrdersCache.remove(orderId);

            // 4. Process refund
            if (refundAmount > 0.01 && economy != null) {
                EconomyResponse refundResp = economy.depositPlayer(Bukkit.getOfflinePlayer(order.getPlacerUUID()), refundAmount);

                if (refundResp.transactionSuccess()) {
                    orderSystem.sendLog("Admin Refund: " + admin.getName() + " refunded " + economy.format(refundAmount) + " to " + order.getPlacerName() + " for cancelled order " + orderId);
                } else {
                    orderSystem.sendLog("CRITICAL ADMIN REFUND FAILED: " + admin.getName() + " tried to refund " + order.getPlacerName() + " (" + order.getPlacerUUID() + ") $" + refundAmount + " but it failed: " + refundResp.errorMessage);
                }
            }

            // 5. Send Discord log
            String orderValueStr = formatMoney(order.getTotalPrice());
            orderSystem.sendLog("Admin Cancel: `"+admin.getName()+"` force-cancelled `"+order.getPlacerName()+"`'s order (ID: "+orderId+"). Item: `"+order.getFormattedItemName()+"`. Total Value: `"+orderValueStr+"`. Refunded: `"+formatMoney(refundAmount)+"`.");

            // 6. Notify admin and (if online) the player
            Bukkit.getScheduler().runTask(plugin, () -> {
                admin.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Successfully cancelled and removed order " + orderId + ".</green>")));

                Player placerOnline = Bukkit.getPlayer(order.getPlacerUUID());
                if (placerOnline != null && placerOnline.isOnline()) {
                    placerOnline.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Your order for " + order.getFormattedItemName() + " (ID: " + orderId + ") was forcibly cancelled by an administrator.</red>")));
                    if (refundAmount > 0.01) {
                        placerOnline.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>The remaining funds (" + economy.format(refundAmount) + ") have been refunded to you.</green>")));
                    }
                }

                // 7. Refresh GUI
                openAdminGui(admin, currentPage);
            });

        });
    }

    // --- Helpers ---
    private String formatAmount(int amount) {
        if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0);
        if (amount >= 1000) return String.format("%.0fK", amount / 1000.0);
        return largeNumFormat.format(amount);
    }

    private String formatMoney(double amount) {
        if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0);
        if (amount >= 1000) return String.format("%.0fK", amount / 1000.0);
        return moneyFormat.format(amount);
    }
}