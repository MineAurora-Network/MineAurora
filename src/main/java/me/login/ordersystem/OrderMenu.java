package me.login.ordersystem;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OrderMenu implements Listener {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private final OrderFilling orderFilling;
    private Map<Long, Order> activeOrdersCache = new ConcurrentHashMap<>();
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 30 * 1000;
    private boolean isRefreshingCache = false;

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    private static final int GUI_SIZE = 54;
    private static final int ORDERS_PER_PAGE = 45; // Renamed for clarity

    private static final DecimalFormat largeNumFormat = new DecimalFormat("#,###");
    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0");

    private final NamespacedKey orderIdKey;

    private static class SearchData {
        final int page;
        final String searchTerm;
        SearchData(int page, String searchTerm) { this.page = page; this.searchTerm = searchTerm; }
    }

    public OrderMenu(Login plugin, OrderSystem orderSystem, OrderFilling orderFilling) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = plugin.getOrdersDatabase();
        this.orderFilling = orderFilling;

        this.miniMessage = MiniMessage.miniMessage(); // Solution 2
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        this.orderIdKey = new NamespacedKey(plugin, "menu_order_id");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshActiveOrdersCache(true);
    }

    // --- Cache Handling (Unchanged) ---
    private void refreshActiveOrdersCache(boolean force) { if(isRefreshingCache||(!force&&(System.currentTimeMillis()-lastCacheUpdateTime<=CACHE_DURATION_MS)))return; isRefreshingCache=true; plugin.getLogger().info("Refreshing active orders..."); ordersDatabase.loadActiveOrders().whenCompleteAsync((orders,error)->{if(error!=null){plugin.getLogger().severe("Failed load active orders: "+error.getMessage());error.printStackTrace();}else{Map<Long,Order> newCache=new ConcurrentHashMap<>();if(orders!=null)orders.forEach(order->newCache.put(order.getOrderId(),order));this.activeOrdersCache=newCache;this.lastCacheUpdateTime=System.currentTimeMillis();plugin.getLogger().info("Loaded "+newCache.size()+" active orders.");}isRefreshingCache=false;},runnable->Bukkit.getScheduler().runTask(plugin,runnable)); }
    private List<Order> getActiveOrdersFromCache() { refreshActiveOrdersCache(false); return activeOrdersCache.values().stream().sorted(Comparator.comparingLong(Order::getCreationTimestamp).reversed()).collect(Collectors.toList()); }


    // --- GUI Opening (Unchanged) ---
    public void openMenuListGui(Player player, int page) {
        CompletableFuture.supplyAsync(this::getActiveOrdersFromCache,
                        runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable))
                .thenAcceptAsync(activeOrders -> {
                    int totalOrders = activeOrders.size();
                    int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
                    int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

                    Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.GRAY + "Active Orders (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");

                    int startIndex = finalPage * ORDERS_PER_PAGE;
                    int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
                    List<Order> pageOrders = activeOrders.subList(startIndex, endIndex);
                    for (int i = 0; i < pageOrders.size(); i++) {
                        gui.setItem(i, createOrderDisplayItem(pageOrders.get(i)));
                    }

                    ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
                    for (int i = ORDERS_PER_PAGE; i < GUI_SIZE; i++) gui.setItem(i, grayPane); // Use constants

                    if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null));
                    gui.setItem(47, OrderSystem.createGuiItem(Material.DIAMOND, "§aCreate New Order", Arrays.asList("§7Click to open the", "§7order creation menu.")));
                    gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null));
                    gui.setItem(49, OrderSystem.createGuiItem(Material.OAK_SIGN, "§bSearch For Orders", Arrays.asList("§7Click to search for", "§7a specific item.")));
                    gui.setItem(50, OrderSystem.createGuiItem(Material.CLOCK, "§bRefresh List", null));
                    if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null));

                    player.setMetadata(OrderSystem.GUI_MENU_METADATA, new FixedMetadataValue(plugin, finalPage));
                    player.openInventory(gui);

                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- Search Results GUI (Unchanged) ---
    public void openMenuSearchResultsGui(Player player, int page, String searchTerm) {
        CompletableFuture.supplyAsync(this::getActiveOrdersFromCache)
                .thenApply(allOrders -> allOrders.stream()
                        .filter(order -> order.getFormattedItemName().toLowerCase().contains(searchTerm.toLowerCase()))
                        .collect(Collectors.toList()))
                .whenCompleteAsync((filteredOrders, error) -> {
                    if (error != null) {
                        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error loading search results.</red>")));
                        return;
                    }
                    if (filteredOrders.isEmpty()) {
                        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>No active orders found for '" + searchTerm + "'.</red>")));
                        openMenuListGui(player, 0); // Go back to main menu
                        return;
                    }

                    int totalOrders = filteredOrders.size();
                    int totalPages = (int) Math.ceil((double) totalOrders / ORDERS_PER_PAGE);
                    int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

                    String guiTitle = ChatColor.GRAY + "Search: '" + searchTerm + "' (" + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")";
                    Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);

                    int startIndex = finalPage * ORDERS_PER_PAGE;
                    int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, totalOrders);
                    List<Order> pageOrders = filteredOrders.subList(startIndex, endIndex);
                    for (int i = 0; i < pageOrders.size(); i++) {
                        gui.setItem(i, createOrderDisplayItem(pageOrders.get(i)));
                    }

                    ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
                    for (int i = ORDERS_PER_PAGE; i < GUI_SIZE; i++) gui.setItem(i, grayPane); // Use constants
                    if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null));
                    gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null));
                    gui.setItem(50, OrderSystem.createGuiItem(Material.ARROW, "§bBack to Main Menu", null));
                    if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null));

                    player.setMetadata(OrderSystem.GUI_MENU_SEARCH_METADATA, new FixedMetadataValue(plugin, new SearchData(finalPage, searchTerm)));
                    player.openInventory(gui);

                }, runnable -> Bukkit.getScheduler().runTask(plugin, runnable));
    }

    // --- Sign Input (Unchanged) ---
    private void openMenuSearchSignInput(Player player) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Item Name", "or 'C' to cancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                openMenuListGui(p, 0); // Back to main menu
                            } else {
                                openMenuSearchResultsGui(p, 0, input);
                            }
                        });
                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Order Menu Search SignGUI", e);
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error opening search prompt.</red>")));
        }
    }

    // --- Create Display Item (Unchanged) ---
    private ItemStack createOrderDisplayItem(Order order) {
        ItemStack displayItem = order.getItem().clone(); ItemMeta meta = displayItem.getItemMeta(); if (meta == null) return displayItem; List<String> lore = new ArrayList<>(); lore.add(" "); lore.add("§3§l" + order.getPlacerName() + "'S Order"); lore.add("§8---------------"); lore.add("§bOrder Info"); lore.add("§d✍ §7Requested: §e" + formatAmount(order.getTotalAmount()) + " " + order.getFormattedItemName()); lore.add("§a$ §7Price: §e" + formatMoney(order.getPricePerItem()) + " Each"); lore.add(" "); lore.add("§bOrder Status"); lore.add("§6⚒ §7Delivered: §e" + formatAmount(order.getAmountDelivered()) + "§7/§e" + formatAmount(order.getTotalAmount())); lore.add("§a$ §7Paid Value: §e" + formatMoney(order.getAmountPaidByFillers()) + "§7/§e" + formatMoney(order.getTotalPrice())); lore.add("§9⏱ §7Expiry: §e" + order.getFormattedExpiryTimeLeft()); lore.add(" "); lore.add("§e► Click to Fill Order"); meta.getPersistentDataContainer().set(orderIdKey, PersistentDataType.LONG, order.getOrderId()); meta.setLore(lore); meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES); displayItem.setItemMeta(meta); return displayItem;
    }

    // --- MODIFIED: onInventoryClick (Handles cancellation upfront) ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        boolean isOrderMenuGUI = player.hasMetadata(OrderSystem.GUI_MENU_METADATA);
        boolean isOrderSearchGUI = player.hasMetadata(OrderSystem.GUI_MENU_SEARCH_METADATA);

        // If it's either of our GUIs, cancel the event immediately
        if (isOrderMenuGUI || isOrderSearchGUI) {
            event.setCancelled(true); // Cancel upfront!
        } else {
            return; // Not our GUI, ignore
        }

        // Now determine which specific handler to call
        if (isOrderMenuGUI) {
            handleMainMenuClick(event, player);
        } else if (isOrderSearchGUI) {
            handleSearchMenuClick(event, player);
        }
    }
    // --- END MODIFICATION ---

    // --- MODIFIED: handleMainMenuClick (Removed redundant setCancelled, use RawSlot, check slot bounds) ---
    private void handleMainMenuClick(InventoryClickEvent event, Player player) {
        // No need for setCancelled here anymore
        if (!event.getView().getTitle().startsWith(ChatColor.GRAY + "Active Orders")) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int currentPage = player.getMetadata(OrderSystem.GUI_MENU_METADATA).get(0).asInt();
        int slot = event.getRawSlot(); // Use RawSlot

        if (slot < GUI_SIZE) { // Check if click is within the top inventory (0-53)
            if (slot >= ORDERS_PER_PAGE) { // Bottom row (indices 45-53)
                Material type = clickedItem.getType();
                if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
                else if (type == Material.DIAMOND && slot == 47) { player.performCommand("order create"); }
                else if (type == Material.OAK_SIGN && slot == 49) { player.closeInventory(); openMenuSearchSignInput(player); }
                else if (type == Material.ARROW) {
                    if (slot == 45 && currentPage > 0) openMenuListGui(player, currentPage - 1);
                    else if (slot == 53) openMenuListGui(player, currentPage + 1);
                } else if (type == Material.CLOCK && slot == 50) {
                    refreshActiveOrdersCache(true);
                    openMenuListGui(player, currentPage);
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Orders refreshed!</green>")));
                }
            } else { // Order item (indices 0-44)
                processFillClick(player, clickedItem, currentPage);
            }
        }
        // Clicks outside the top GUI (e.g., in player inventory) are ignored due to early cancellation
    }
    // --- END MODIFICATION ---

    // --- MODIFIED: handleSearchMenuClick (Removed redundant setCancelled, use RawSlot, check slot bounds) ---
    private void handleSearchMenuClick(InventoryClickEvent event, Player player) {
        // No need for setCancelled here anymore
        if (!event.getView().getTitle().startsWith(ChatColor.GRAY + "Search:")) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        Object metaValue = player.getMetadata(OrderSystem.GUI_MENU_SEARCH_METADATA).get(0).value();
        if (!(metaValue instanceof SearchData searchData)) return;

        int slot = event.getRawSlot(); // Use RawSlot

        if (slot < GUI_SIZE) { // Check if click is within the top inventory (0-53)
            if (slot >= ORDERS_PER_PAGE) { // Bottom row (indices 45-53)
                Material type = clickedItem.getType();
                if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
                else if (type == Material.ARROW) {
                    if (slot == 45 && searchData.page > 0) { openMenuSearchResultsGui(player, searchData.page - 1, searchData.searchTerm); }
                    else if (slot == 53) { openMenuSearchResultsGui(player, searchData.page + 1, searchData.searchTerm); }
                    else if (slot == 50) { openMenuListGui(player, 0); } // Back button
                }
            } else { // Order item (indices 0-44)
                processFillClick(player, clickedItem, searchData.page);
            }
        }
        // Clicks outside the top GUI (e.g., in player inventory) are ignored due to early cancellation
    }
    // --- END MODIFICATION ---

    // --- processFillClick (Unchanged) ---
    private void processFillClick(Player player, ItemStack clickedItem, int currentPage) {
        ItemMeta meta = clickedItem.getItemMeta();
        if (meta != null && meta.getPersistentDataContainer().has(orderIdKey, PersistentDataType.LONG)) {
            long orderId = meta.getPersistentDataContainer().get(orderIdKey, PersistentDataType.LONG);
            Order order = activeOrdersCache.get(orderId);

            if (order != null && order.getStatus() == Order.OrderStatus.ACTIVE && !order.isExpired()) {
                orderFilling.startFillingProcess(player, order, currentPage);
            } else {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order unavailable/changed. Refreshing...</red>")));
                refreshActiveOrdersCache(true);
                // Go back to main menu page 0 if fill fails from search
                openMenuListGui(player, 0);
            }
        } else {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error retrieving order ID.</red>")));
        }
    }

    // --- Helpers (Unchanged) ---
    private String formatAmount(int amount) { if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0); if (amount >= 1000) return String.format("%.0fK", amount / 1000.0); return largeNumFormat.format(amount); }
    private String formatMoney(double amount) { if (amount >= 1000000) return String.format("%.0fM", amount / 1000000.0); if (amount >= 1000) return String.format("%.0fK", amount / 1000.0); return moneyFormat.format(amount); }
}