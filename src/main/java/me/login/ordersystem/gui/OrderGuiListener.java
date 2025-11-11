package me.login.ordersystem.gui;

import me.login.ordersystem.OrderModule;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory; // <-- Make sure this is imported
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitRunnable; // <-- Make sure this is imported

/**
 * Central listener for all Order System GUIs.
 * Handles click routing and metadata cleanup on close.
 * (Point 10)
 */
public class OrderGuiListener implements Listener {

    private final OrderModule module;

    public OrderGuiListener(OrderModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // Find the metadata key that this GUI has
        String activeMetadataKey = null;
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                activeMetadataKey = key;
                break;
            }
        }

        // Not an order GUI
        if (activeMetadataKey == null) {
            return;
        }

        // --- THIS IS THE FIX for letting you move items in your own inventory ---
        // Check if the click happened in the player's inventory or outside.
        if (event.getClickedInventory() == null || event.getClickedInventory() instanceof PlayerInventory) {
            // This click is in the player's inventory.
            // We MUST NOT cancel it.
            return;
        }

        // At this point, we know the click was in the *top inventory* (our GUI).
        // NOW it is safe to cancel the event.
        event.setCancelled(true);

        // Clicked on empty space in the GUI
        if (event.getCurrentItem() == null) {
            return;
        }

        // Route the click to the correct handler
        switch (activeMetadataKey) {
            case OrderModule.GUI_MENU_METADATA -> module.getOrderMenu().handleMainMenuClick(event, player);
            case OrderModule.GUI_MENU_SEARCH_METADATA -> module.getOrderMenu().handleSearchMenuClick(event, player);
            case OrderModule.GUI_CREATE_METADATA -> module.getOrderCreate().handleCreateGuiClick(event, player);
            case OrderModule.GUI_SEARCH_METADATA -> module.getOrderCreate().handleSearchGuiClick(event, player);
            case OrderModule.GUI_MANAGE_METADATA -> module.getOrderManage().handleManageGuiClick(event, player);
            case OrderModule.GUI_ADMIN_METADATA -> module.getOrderAdminMenu().handleAdminMenuClick(event, player);
            case OrderModule.GUI_ALERT_METADATA -> module.getOrderAlertMenu().handleAlertMenuClick(event, player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();

        // Check if the player has any of our metadata *right now*.
        boolean hadMetadata = false;
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                hadMetadata = true;
                break;
            }
        }
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            hadMetadata = true;
        }

        if (!hadMetadata) {
            // This close event wasn't for us.
            return;
        }

        // --- THIS IS THE FIX for refreshing GUIs ---
        // Schedule metadata cleanup for 1 tick later
        new BukkitRunnable() {
            @Override
            public void run() {
                // Check if player is still online
                if (!player.isOnline()) {
                    return;
                }

                // Check if the player has opened *another* order GUI
                boolean inNewOrderGui = false;
                for (String key : OrderModule.ALL_GUI_METADATA) {
                    if (player.hasMetadata(key)) {
                        inNewOrderGui = true;
                        break;
                    }
                }
                if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
                    inNewOrderGui = true;
                }

                // If they are *not* in a new order GUI, it means they
                // closed it for real (e.g., 'Esc' key).
                // NOW we can safely remove all metadata.
                if (!inNewOrderGui) {
                    for (String key : OrderModule.ALL_GUI_METADATA) {
                        if (player.hasMetadata(key)) {
                            player.removeMetadata(key, module.getPlugin());
                        }
                    }
                    if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
                        player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, module.getPlugin());
                    }
                }
                // If they *are* in a new order GUI (from a refresh),
                // we do nothing and let the new GUI keep its metadata.
            }
        }.runTaskLater(module.getPlugin(), 1L); // 1-tick delay
    }
}