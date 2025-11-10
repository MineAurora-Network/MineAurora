package me.login.ordersystem.gui;

import me.login.ordersystem.OrderModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.metadata.MetadataValue;

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
        InventoryView view = event.getView();

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

        // (Point 10) It is our GUI, so cancel the event.
        event.setCancelled(true);

        // Click was outside the top inventory (e.g., in player's inventory)
        if (event.getRawSlot() >= view.getTopInventory().getSize()) {
            return;
        }

        // Clicked on empty space
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

        // (Point 10) CRITICAL FIX: Remove all metadata keys on close.
        boolean hadMetadata = false;
        for (String key : OrderModule.ALL_GUI_METADATA) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, module.getPlugin());
                hadMetadata = true;
            }
        }

        // This is a failsafe for the alert menu, which has dynamic metadata
        if (player.hasMetadata(OrderAlertMenu.ALERT_ORDER_KEY)) {
            player.removeMetadata(OrderAlertMenu.ALERT_ORDER_KEY, module.getPlugin());
        }
    }
}