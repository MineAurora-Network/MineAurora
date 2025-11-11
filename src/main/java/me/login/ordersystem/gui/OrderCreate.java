package me.login.ordersystem.gui;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import me.login.ordersystem.data.Order;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.OrdersDatabase;
import me.login.ordersystem.system.OrderSystem;
import me.login.ordersystem.util.OrderLogger;
import me.login.ordersystem.util.OrderMessageHandler;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * Handles the /order create GUI and logic.
 * (Refactored for Points 1, 2, 3, 4, 6, 10, 11, 13, 14)
 */
public class OrderCreate {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;
    private final OrderMessageHandler messageHandler;
    private final OrderLogger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final long ORDER_DURATION_DAYS = 4;
    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;

    // For metadata
    private static class SearchData {
        final int page;
        final String searchTerm;
        SearchData(int page, String searchTerm) { this.page = page; this.searchTerm = searchTerm; }
    }

    public OrderCreate(Login plugin, OrderSystem orderSystem, OrdersDatabase ordersDatabase, OrderMessageHandler messageHandler, OrderLogger logger) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = ordersDatabase;
        this.messageHandler = messageHandler;
        this.logger = logger;
    }

    // --- GUI Opening Methods ---

    public void openCreateGui(Player player, int page) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        List<ItemStack> orderableItems = orderSystem.getOrderableItems();
        int totalItems = orderableItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        Component title = OrderGuiUtils.getMenuTitle("Create Order (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        int startIndex = finalPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        for (int i = startIndex; i < endIndex; i++) {
            gui.setItem(i - startIndex, orderableItems.get(i)); // Fill slots 0-44
        }

        ItemStack grayPane = OrderGuiUtils.getGrayPane();
        for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

        if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
        gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
        gui.setItem(50, OrderGuiUtils.createGuiItem(Material.OAK_SIGN, mm.deserialize("<aqua>Search Items</aqua>"), null));
        if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

        player.setMetadata(OrderModule.GUI_CREATE_METADATA, new FixedMetadataValue(plugin, finalPage));
        player.openInventory(gui);
    }

    public void openSearchResultsGui(Player player, int page, String searchTerm) {
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, plugin);
        }
        List<ItemStack> allItems = orderSystem.getOrderableItems();
        List<ItemStack> filteredItems = allItems.stream()
                .filter(item -> item.getType().name().toLowerCase().replace("_", " ").contains(searchTerm.toLowerCase()))
                .toList();

        if (filteredItems.isEmpty()) {
            messageHandler.sendMessage(player, "<red>No items found for '" + searchTerm + "'.</red>");
            openCreateGui(player, 0);
            return;
        }

        int totalItems = filteredItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        Component title = OrderGuiUtils.getMenuTitle("Search: '" + searchTerm + "' (" + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        int startIndex = finalPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        for (int i = startIndex; i < endIndex; i++) {
            gui.setItem(i - startIndex, filteredItems.get(i));
        }

        ItemStack grayPane = OrderGuiUtils.getGrayPane();
        for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

        if (finalPage > 0) gui.setItem(45, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Previous Page</green>"), null));
        gui.setItem(48, OrderGuiUtils.createGuiItem(Material.BARRIER, mm.deserialize("<red>Close</red>"), null));
        gui.setItem(50, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<aqua>Back to Main Menu</aqua>"), null));
        if (finalPage < totalPages - 1) gui.setItem(53, OrderGuiUtils.createGuiItem(Material.ARROW, mm.deserialize("<green>Next Page</green>"), null));

        player.setMetadata(OrderModule.GUI_SEARCH_METADATA, new FixedMetadataValue(plugin, new SearchData(finalPage, searchTerm)));
        player.openInventory(gui);
    }

    // --- Click Handlers (Called by OrderGuiListener) ---

    public void handleCreateGuiClick(InventoryClickEvent event, Player player) {
        int currentPage = player.getMetadata(OrderModule.GUI_CREATE_METADATA).get(0).asInt();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (slot >= ITEMS_PER_PAGE) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
            else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) { // Previous
                    openCreateGui(player, currentPage - 1);
                } else if (slot == 53) { // Next
                    List<ItemStack> items = orderSystem.getOrderableItems();
                    int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
                    if (currentPage < totalPages - 1) openCreateGui(player, currentPage + 1);
                }
            } else if (type == Material.OAK_SIGN && slot == 50) {
                player.closeInventory();
                openSearchSignInput(player);
            }
        } else { // Item selected
            ItemStack selected = clickedItem.clone(); selected.setAmount(1);
            player.closeInventory();
            openPriceSignInput(player, selected);
        }
    }

    public void handleSearchGuiClick(InventoryClickEvent event, Player player) {
        SearchData searchData = (SearchData) player.getMetadata(OrderModule.GUI_SEARCH_METADATA).get(0).value();
        ItemStack clickedItem = event.getCurrentItem();
        int slot = event.getSlot();

        if (slot >= ITEMS_PER_PAGE) { // Bottom row
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
            else if (type == Material.ARROW) {
                if (slot == 45 && searchData.page > 0) { // Previous
                    openSearchResultsGui(player, searchData.page - 1, searchData.searchTerm);
                } else if (slot == 53) { // Next
                    List<ItemStack> allItems = orderSystem.getOrderableItems();
                    List<ItemStack> filteredItems = allItems.stream()
                            .filter(item -> item.getType().name().toLowerCase().replace("_", " ").contains(searchData.searchTerm.toLowerCase()))
                            .toList();
                    int totalPages = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
                    if (searchData.page < totalPages - 1) openSearchResultsGui(player, searchData.page + 1, searchData.searchTerm);
                } else if (slot == 50) { // Back button
                    openCreateGui(player, 0);
                }
            }
        } else { // Item selected
            ItemStack selected = clickedItem.clone(); selected.setAmount(1);
            player.closeInventory();
            openPriceSignInput(player, selected);
        }
    }

    // --- Sign GUI Handling ---

    private void openSearchSignInput(Player player) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Search Term", "or 'C' to cancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                openCreateGui(p, 0);
                            } else {
                                openSearchResultsGui(p, 0, input);
                            }
                        });
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Search SignGUI", e);
            messageHandler.sendMessage(player, "<red>Error opening search prompt.</red>");
            openCreateGui(player, 0);
        }
    }

    private void openPriceSignInput(Player player, ItemStack item) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Price per", "item (k/m) | 'C'ancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                            messageHandler.sendMessage(p, "<red>Order creation cancelled.</red>");
                            return null;
                        }
                        try {
                            double price = parseDoubleWithSuffix(input);
                            if (price <= 0) {
                                messageHandler.sendMessage(p, "<red>Price must be positive. Cancelled.</red>");
                                return null;
                            }
                            Bukkit.getScheduler().runTask(plugin, () -> openQuantitySignInput(p, item, price));
                        } catch (NumberFormatException e) {
                            messageHandler.sendMessage(p, "<red>'" + input + "' is not a valid price. Use numbers (e.g., 100, 2k, 1m).</red>");
                        }
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Price SignGUI", e);
            messageHandler.sendMessage(player, "<red>Error opening input prompt.</red>");
        }
    }

    private void openQuantitySignInput(Player player, ItemStack item, double price) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Quantity", "(k/m) | 'C'ancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                            messageHandler.sendMessage(p, "<red>Order creation cancelled.</red>");
                            return null;
                        }
                        try {
                            int quantity = parseIntWithSuffix(input);
                            double totalCost = quantity * price;

                            ordersDatabase.loadPlayerOrders(p.getUniqueId()).whenCompleteAsync((playerOrders, dbError) -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (dbError != null) {
                                        messageHandler.sendMessage(p, "<red>Error checking your order limit. Please try again.</red>");
                                        logger.logError("Failed to load orders for limit check: " + p.getName(), dbError, 0);
                                        return;
                                    }

                                    int limit = getPlayerOrderLimit(p);
                                    long activeCount = playerOrders.stream()
                                            .filter(o -> o.getStatus() == Order.OrderStatus.ACTIVE)
                                            .count();

                                    if (activeCount >= limit && limit != Integer.MAX_VALUE) {
                                        messageHandler.sendMultiLine(p, "<red>You have reached your active order limit of " + limit + ".%nl%Cancel old orders in /order manage to create new ones.</red>");
                                        return;
                                    }

                                    Economy economy = OrderModule.getEconomy();
                                    if (economy == null || !economy.has(p, totalCost)) {
                                        messageHandler.sendMultiLine(p, "<red>You cannot afford to place this order!%nl%Required: " + (economy != null ? economy.format(totalCost) : "$" + totalCost) + "</red>");
                                        return;
                                    }

                                    EconomyResponse withdrawResp = economy.withdrawPlayer(p, totalCost);
                                    if (!withdrawResp.transactionSuccess()) {
                                        messageHandler.sendMessage(p, "<red>Failed to withdraw funds: " + withdrawResp.errorMessage + ". Order cancelled.</red>");
                                        return;
                                    }

                                    long durationMillis = TimeUnit.DAYS.toMillis(ORDER_DURATION_DAYS);
                                    Order newOrder = new Order(p.getUniqueId(), p.getName(), item, quantity, price, durationMillis);

                                    ordersDatabase.saveOrder(newOrder).whenCompleteAsync((generatedId, saveError) -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (saveError != null || generatedId == null || generatedId <= 0) {
                                                logger.logError("Error saving order DB", saveError, 0);
                                                messageHandler.sendMessage(p, "<red>Failed to save order! Refunding cost...</red>");
                                                economy.depositPlayer(p, totalCost);
                                            } else {
                                                newOrder.setOrderId(generatedId);
                                                messageHandler.sendMessage(p, "<green>Order created successfully! (ID: " + generatedId + ")</green>");
                                                messageHandler.sendMessage(p, "<yellow>" + economy.format(totalCost) + " has been deducted to fund this order.</yellow>");
                                                logger.logCreation(newOrder);

                                                // (Points 3, 4) Staff Alert
                                                if (totalCost >= 1_000_000.0) {
                                                    String itemName = newOrder.getFormattedItemName();
                                                    String priceStr = OrderGuiUtils.formatMoney(totalCost);
                                                    Component alertMessage = mm.deserialize("<aqua>" + p.getName() + "</aqua> created an order for <yellow>" + itemName + "</yellow> worth <green>" + priceStr + "!");
                                                    String command = "/order alert " + generatedId;

                                                    Bukkit.getOnlinePlayers().forEach(staff -> {
                                                        if (staff.hasPermission("staff.staff")) {
                                                            messageHandler.sendClickableAlert(staff, alertMessage, command);
                                                        }
                                                    });
                                                }
                                                // --- End Alert ---

                                                // Send slot usage message
                                                sendSlotUsage(p);
                                            }
                                        });
                                    });
                                });
                            });

                        } catch (NumberFormatException | ArithmeticException e) {
                            messageHandler.sendMessage(p, "<red>'" + input + "' is not a valid quantity. Use numbers (e.g., 64, 1k, 2m).%nl%Reason: " + e.getMessage() + "</red>");
                        }
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Quantity SignGUI", e);
            messageHandler.sendMessage(player, "<red>Error opening input prompt.</red>");
        }
    }

    private void sendSlotUsage(Player p) {
        final int currentLimit = getPlayerOrderLimit(p);
        if (currentLimit != Integer.MAX_VALUE) {
            ordersDatabase.loadPlayerOrders(p.getUniqueId()).whenCompleteAsync((updatedOrders, countError) -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (countError == null) {
                        long newActiveCount = updatedOrders.stream()
                                .filter(o -> o.getStatus() == Order.OrderStatus.ACTIVE)
                                .count();
                        long remaining = Math.max(0, currentLimit - newActiveCount);
                        messageHandler.sendMessage(p, "<white>[<red>" + remaining + " order slots left</red>]</white>");
                    }
                });
            });
        }
    }

    // --- Helpers ---

    private int getPlayerOrderLimit(Player player) {
        int maxLimit = plugin.getDefaultOrderLimit();
        if (player.isOp()) {
            return Integer.MAX_VALUE;
        }
        for (PermissionAttachmentInfo permInfo : player.getEffectivePermissions()) {
            String perm = permInfo.getPermission();
            if (perm.startsWith("order.limit.") && permInfo.getValue()) {
                try {
                    String limitStr = perm.substring(perm.lastIndexOf('.') + 1);
                    if (limitStr.equalsIgnoreCase("unlimited")) {
                        return Integer.MAX_VALUE;
                    }
                    int limit = Integer.parseInt(limitStr);
                    if (limit > maxLimit) {
                        maxLimit = limit;
                    }
                } catch (Exception e) {
                    plugin.getLogger().warning("Player " + player.getName() + " has malformed order limit permission: " + perm);
                }
            }
        }
        return maxLimit;
    }

    private double parseDoubleWithSuffix(String input) throws NumberFormatException {
        input = input.trim().toLowerCase().replace(",", "");
        double multiplier = 1.0;

        if (input.endsWith("k")) {
            multiplier = 1_000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1_000_000.0;
            input = input.substring(0, input.length() - 1);
        }

        double value = Double.parseDouble(input);
        double result = value * multiplier;

        if (result <= 0) {
            throw new NumberFormatException("Price must be positive.");
        }
        return result;
    }

    private int parseIntWithSuffix(String input) throws NumberFormatException, ArithmeticException {
        input = input.trim().toLowerCase().replace(",", "");
        double multiplier = 1.0;

        if (input.endsWith("k")) {
            multiplier = 1_000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.endsWith("m")) {
            multiplier = 1_000_000.0;
            input = input.substring(0, input.length() - 1);
        }

        double value = Double.parseDouble(input);
        double result = value * multiplier;

        if (result > Integer.MAX_VALUE) {
            throw new ArithmeticException("Quantity exceeds maximum allowed value (" + Integer.MAX_VALUE + ").");
        }
        if (result <= 0) {
            throw new NumberFormatException("Quantity must be positive.");
        }
        return (int) result;
    }
}