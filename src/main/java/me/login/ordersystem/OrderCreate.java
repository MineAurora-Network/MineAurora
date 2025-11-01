package me.login.ordersystem;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class OrderCreate implements Listener {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrdersDatabase ordersDatabase;

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    private static final long ORDER_DURATION_DAYS = 4;
    private static final int GUI_SIZE = 54;
    private static final int ITEMS_PER_PAGE = 45;

    private static final DecimalFormat moneyFormat = new DecimalFormat("#,##0");

    private static class SearchData {
        final int page;
        final String searchTerm;
        SearchData(int page, String searchTerm) { this.page = page; this.searchTerm = searchTerm; }
    }

    public OrderCreate(Login plugin, OrderSystem orderSystem) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.ordersDatabase = plugin.getOrdersDatabase();
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);
    }

    // --- GUI Opening Methods (openCreateGui) ---
    // ... (Unchanged) ...
    public void openCreateGui(Player player, int page) {
        List<ItemStack> orderableItems = orderSystem.getOrderableItems(); int totalItems = orderableItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.GRAY + "Create Order (Page " + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")");
        int startIndex = finalPage * ITEMS_PER_PAGE; int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        for (int i = startIndex; i < endIndex; i++) gui.setItem(i - startIndex, orderableItems.get(i)); // Fill slots 0-44

        ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);
        if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null)); else gui.setItem(45, grayPane);
        gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null));
        gui.setItem(50, OrderSystem.createGuiItem(Material.OAK_SIGN, "§bSearch Items", null));
        if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null)); else gui.setItem(53, grayPane);

        player.setMetadata(OrderSystem.GUI_CREATE_METADATA, new FixedMetadataValue(plugin, finalPage)); // Store finalPage
        player.openInventory(gui);
    }

    // --- openSearchResultsGui (Unchanged) ---
    public void openSearchResultsGui(Player player, int page, String searchTerm) {
        List<ItemStack> allItems = orderSystem.getOrderableItems();
        List<ItemStack> filteredItems = allItems.stream()
                .filter(item -> item.getType().name().toLowerCase().replace("_", " ").contains(searchTerm.toLowerCase()))
                .toList();

        if (filteredItems.isEmpty()) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>No items found for '" + searchTerm + "'.</red>")));
            openCreateGui(player, 0);
            return;
        }

        int totalItems = filteredItems.size();
        int totalPages = (int) Math.ceil((double) totalItems / ITEMS_PER_PAGE);
        int finalPage = Math.max(0, Math.min(page, totalPages > 0 ? totalPages - 1 : 0));
        String guiTitle = ChatColor.GRAY + "Search: '" + searchTerm + "' (" + (finalPage + 1) + "/" + Math.max(1, totalPages) + ")";
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, guiTitle);

        int startIndex = finalPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalItems);
        for (int i = startIndex; i < endIndex; i++) {
            gui.setItem(i - startIndex, filteredItems.get(i));
        }

        ItemStack grayPane = OrderSystem.createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);
        if (finalPage > 0) gui.setItem(45, OrderSystem.createGuiItem(Material.ARROW, "§ePrevious Page", null));
        gui.setItem(48, OrderSystem.createGuiItem(Material.BARRIER, "§cClose", null));
        gui.setItem(50, OrderSystem.createGuiItem(Material.ARROW, "§bBack to Main Menu", null));
        if (finalPage < totalPages - 1) gui.setItem(53, OrderSystem.createGuiItem(Material.ARROW, "§eNext Page", null));

        player.setMetadata(OrderSystem.GUI_SEARCH_METADATA, new FixedMetadataValue(plugin, new SearchData(finalPage, searchTerm)));
        player.openInventory(gui);
    }

    // --- Click Handler (Unchanged) ---
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        List<MetadataValue> createMeta = player.getMetadata(OrderSystem.GUI_CREATE_METADATA);
        if (!createMeta.isEmpty() && title.startsWith(ChatColor.GRAY + "Create Order")) {
            handleCreateGuiClick(event, player, createMeta.get(0).asInt());
            return;
        }

        List<MetadataValue> searchMeta = player.getMetadata(OrderSystem.GUI_SEARCH_METADATA);
        if (!searchMeta.isEmpty() && title.startsWith(ChatColor.GRAY + "Search:")) {
            Object metaValue = searchMeta.get(0).value();
            if (metaValue instanceof SearchData) {
                handleSearchGuiClick(event, player, (SearchData) metaValue);
            }
        }
    }

    // --- GUI Click Handlers (Unchanged) ---
    private void handleCreateGuiClick(InventoryClickEvent event, Player player, int currentPage) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot < GUI_SIZE) {
            if (slot >= ITEMS_PER_PAGE) { // Bottom row
                Material type = clickedItem.getType();
                if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
                else if (type == Material.ARROW) {
                    if (slot == 45) { // Previous
                        if (currentPage > 0) openCreateGui(player, currentPage - 1);
                    } else if (slot == 53) { // Next
                        List<ItemStack> items = orderSystem.getOrderableItems();
                        int totalPages = (int) Math.ceil((double) items.size() / ITEMS_PER_PAGE);
                        if (currentPage < totalPages - 1) openCreateGui(player, currentPage + 1);
                    }
                } else if (type == Material.OAK_SIGN && slot == 50) {
                    player.closeInventory();
                    openSearchSignInput(player); // Open search prompt
                }
            } else { // Item selected
                ItemStack selected = clickedItem.clone(); selected.setAmount(1);
                player.closeInventory();
                openPriceSignInput(player, selected);
            }
        }
    }
    private void handleSearchGuiClick(InventoryClickEvent event, Player player, SearchData searchData) {
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getRawSlot();
        if (slot < GUI_SIZE) {
            if (slot >= ITEMS_PER_PAGE) { // Bottom row
                Material type = clickedItem.getType();
                if (type == Material.BARRIER && slot == 48) { player.closeInventory(); }
                else if (type == Material.ARROW) {
                    if (slot == 45) { // Previous
                        if (searchData.page > 0) openSearchResultsGui(player, searchData.page - 1, searchData.searchTerm);
                    } else if (slot == 53) { // Next
                        List<ItemStack> allItems = orderSystem.getOrderableItems();
                        List<ItemStack> filteredItems = allItems.stream()
                                .filter(item -> item.getType().name().toLowerCase().replace("_", " ").contains(searchData.searchTerm.toLowerCase()))
                                .toList(); // Use modern .toList()
                        int totalPages = (int) Math.ceil((double) filteredItems.size() / ITEMS_PER_PAGE);
                        if (searchData.page < totalPages - 1) openSearchResultsGui(player, searchData.page + 1, searchData.searchTerm);
                    } else if (slot == 50) { // Back button
                        openCreateGui(player, 0);
                    }
                }
            } else { // Item selected
                ItemStack selected = clickedItem.clone(); selected.setAmount(1);
                player.closeInventory();
                openPriceSignInput(player, selected); // Same process
            }
        }
    }

    // --- Order Limit Check (Unchanged) ---
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
                    int limit = Integer.parseInt(limitStr);
                    if (limit > maxLimit) {
                        maxLimit = limit;
                    }
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    plugin.getLogger().warning("Player " + player.getName() + " has malformed order limit permission: " + perm);
                }
            }
        }
        return maxLimit;
    }


    // --- Suffix Parsers (Unchanged) ---
    private double parseDoubleAsLongWithSuffix(String input) throws NumberFormatException {
        input = input.trim().toLowerCase();
        long multiplier = 1L;
        char lastChar = input.charAt(input.length() - 1);

        if (lastChar == 'k') {
            multiplier = 1_000L;
            input = input.substring(0, input.length() - 1);
        } else if (lastChar == 'm') {
            multiplier = 1_000_000L;
            input = input.substring(0, input.length() - 1);
        }

        if (input.contains(".")) {
            throw new NumberFormatException("Price must be a whole number.");
        }
        long value = Long.parseLong(input);
        long result = value * multiplier;

        if (result <= 0) {
            throw new NumberFormatException("Price must be positive.");
        }
        return (double) result;
    }
    private int parseIntWithSuffix(String input) throws NumberFormatException, ArithmeticException {
        input = input.trim().toLowerCase();
        long multiplier = 1L;
        char lastChar = input.charAt(input.length() - 1);

        if (lastChar == 'k') {
            multiplier = 1_000L;
            input = input.substring(0, input.length() - 1);
        } else if (lastChar == 'm') {
            multiplier = 1_000_000L;
            input = input.substring(0, input.length() - 1);
        }

        if (input.contains(".")) {
            throw new NumberFormatException("Quantity must be a whole number.");
        }
        long value = Long.parseLong(input);
        long result = value * multiplier;

        if (result > Integer.MAX_VALUE) {
            throw new ArithmeticException("Quantity exceeds maximum allowed value (" + Integer.MAX_VALUE + ").");
        }
        if (result <= 0) {
            throw new NumberFormatException("Quantity must be positive.");
        }
        return (int) result;
    }
    private String formatMoney(double amount) {
        return moneyFormat.format(amount);
    }


    // --- Sign GUI Handling (Unchanged) ---
    private void openSearchSignInput(Player player) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", ChatColor.GRAY + "Enter Search Term", ChatColor.GRAY + "or " + ChatColor.RED + "C" + ChatColor.GRAY + " to cancel")
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
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error opening search prompt.</red>")));
            openCreateGui(player, 0);
        }
    }
    private void openPriceSignInput(Player player, ItemStack item) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", ChatColor.GRAY + "Enter Price per", ChatColor.GRAY + "item (k/m ok) || " + ChatColor.RED + "C" + ChatColor.GRAY + "ancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                            p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order creation cancelled.</red>")));
                            return null;
                        }
                        try {
                            double price = parseDoubleAsLongWithSuffix(input);
                            if (price <= 0) {
                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Price must be positive. Cancelled.</red>")));
                                return null;
                            }
                            Bukkit.getScheduler().runTask(plugin, () -> openQuantitySignInput(p, item, price));
                        } catch (NumberFormatException e) {
                            p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>'" + input + "' is not a valid price. Use whole numbers (e.g., 100, 2k, 1m).</red>")));
                        }
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Price SignGUI", e);
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error opening input prompt.</red>")));
        }
    }

    // --- openQuantitySignInput (MODIFIED: Staff alert now has prefix) ---
    private void openQuantitySignInput(Player player, ItemStack item, double price) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", ChatColor.GRAY + "Enter Quantity", ChatColor.GRAY + "(k/m ok) || " + ChatColor.RED + "C" + ChatColor.GRAY + "ancel")
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                            p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order creation cancelled.</red>")));
                            return null;
                        }
                        try {
                            int quantity = parseIntWithSuffix(input);
                            double totalCost = quantity * price;

                            ordersDatabase.loadPlayerOrders(p.getUniqueId()).whenCompleteAsync((playerOrders, dbError) -> {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    if (dbError != null) {
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error checking your order limit. Please try again.</red>")));
                                        plugin.getLogger().warning("Failed to load orders for limit check: " + dbError.getMessage());
                                        return;
                                    }

                                    int limit = getPlayerOrderLimit(p);
                                    long activeCount = playerOrders.stream()
                                            .filter(o -> o.getStatus() == Order.OrderStatus.ACTIVE)
                                            .count();

                                    if (activeCount >= limit && limit != Integer.MAX_VALUE) {
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You have reached your active order limit of " + limit + ".</red>")));
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Cancel old orders in /order manage to create new ones.</red>")));
                                        return;
                                    }

                                    Economy economy = OrderFilling.getEconomy();
                                    if (economy == null || !economy.has(p, totalCost)) {
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You cannot afford to place this order!</red>")));
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Required: " + (economy != null ? economy.format(totalCost) : totalCost) + "</red>")));
                                        return;
                                    }

                                    EconomyResponse withdrawResp = economy.withdrawPlayer(p, totalCost);
                                    if (!withdrawResp.transactionSuccess()) {
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Failed to withdraw funds: " + withdrawResp.errorMessage + "</red>")));
                                        p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Order cancelled.</red>")));
                                        return;
                                    }

                                    long durationMillis = TimeUnit.DAYS.toMillis(ORDER_DURATION_DAYS);
                                    Order newOrder = new Order(p.getUniqueId(), p.getName(), item, quantity, price, durationMillis);

                                    ordersDatabase.saveOrder(newOrder).whenCompleteAsync((generatedId, saveError) -> {
                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                            if (saveError != null) {
                                                plugin.getLogger().log(Level.SEVERE, "Error saving order DB", saveError);
                                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Failed to save order! Refunding cost...</red>")));
                                                economy.depositPlayer(p, totalCost);
                                            } else if (generatedId != null && generatedId > 0) {
                                                newOrder.setOrderId(generatedId);
                                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<green>Order created successfully! (ID: " + generatedId + ")</green>")));
                                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>" + economy.format(totalCost) + " has been deducted to fund this order.</yellow>")));
                                                orderSystem.sendLog("New Order: " + p.getName() + " [" + quantity + "x " + item.getType() + " @ " + price + " each] ID: " + generatedId + " Cost: " + totalCost);

                                                // --- MODIFIED: Staff Alert ---
                                                if (totalCost >= 1_000_000.0) {
                                                    String itemName = newOrder.getFormattedItemName();
                                                    String priceStr = formatMoney(totalCost);
                                                    Component alertMessage = miniMessage.deserialize("<red>Alert<white>: <aqua>" + p.getName() + "</aqua> created an order for <yellow>" + itemName + "</yellow> worth <green>" + priceStr + "!");

                                                    Bukkit.getOnlinePlayers().forEach(staff -> {
                                                        if (staff.hasPermission("staff.staff")) {
                                                            // --- THIS LINE IS THE FIX ---
                                                            staff.sendMessage(serverPrefix.append(alertMessage));
                                                        }
                                                    });
                                                }
                                                // --- END MODIFICATION ---

                                                final int currentLimit = getPlayerOrderLimit(p);
                                                if (currentLimit != Integer.MAX_VALUE) {
                                                    ordersDatabase.loadPlayerOrders(p.getUniqueId()).whenCompleteAsync((updatedOrders, countError) -> {
                                                        Bukkit.getScheduler().runTask(plugin, () -> {
                                                            if (countError == null) {
                                                                long newActiveCount = updatedOrders.stream()
                                                                        .filter(o -> o.getStatus() == Order.OrderStatus.ACTIVE)
                                                                        .count();
                                                                long remaining = Math.max(0, currentLimit - newActiveCount);
                                                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<white>[<red>" + remaining + " order slots left</red>]</white>")));
                                                            }
                                                        });
                                                    });
                                                }
                                            } else {
                                                p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Failed save order (DB error). Refunding cost...</red>")));
                                                economy.depositPlayer(p, totalCost);
                                            }
                                        });
                                    });
                                });
                            });

                        } catch (NumberFormatException | ArithmeticException e) {
                            p.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>'" + input + "' is not a valid quantity. Use whole numbers (e.g., 64, 1k, 2m). Reason: " + e.getMessage() + "</red>")));
                        }
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Quantity SignGUI", e);
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Error opening input prompt.</red>")));
        }
    }
}