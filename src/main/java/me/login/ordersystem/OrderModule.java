package me.login.ordersystem;

import me.login.Login;
import me.login.ordersystem.data.OfflineDeliveryManager;
import me.login.ordersystem.gui.*;
import me.login.ordersystem.system.OrderFilling;
import me.login.ordersystem.system.OrderSystem;
import me.login.ordersystem.util.OrderLogger;
import me.login.ordersystem.util.OrderMessageHandler;
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Objects;

/**
 * Main module for the Order System.
 * Handles initialization of all managers, commands, and listeners.
 */
public class OrderModule {

    private final Login plugin;
    private static Economy economy = null;

    // GUI Metadata Keys (Point 10)
    public static final String GUI_CREATE_METADATA = "OrderCreateGUI";
    public static final String GUI_MENU_METADATA = "OrderMenuGUI";
    public static final String GUI_MANAGE_METADATA = "OrderManageGUI";
    public static final String GUI_SEARCH_METADATA = "OrderSearchGUI"; // Create search
    public static final String GUI_MENU_SEARCH_METADATA = "OrderMenuSearchGUI"; // Menu search
    public static final String GUI_ADMIN_METADATA = "OrderAdminMenuGUI";
    public static final String GUI_ALERT_METADATA = "OrderAlertMenuGUI"; // (Point 4)

    // All metadata keys for easy cleanup
    public static final String[] ALL_GUI_METADATA = {
            GUI_CREATE_METADATA, GUI_MENU_METADATA, GUI_MANAGE_METADATA,
            GUI_SEARCH_METADATA, GUI_MENU_SEARCH_METADATA, GUI_ADMIN_METADATA, GUI_ALERT_METADATA
    };

    // Managers
    private OrdersDatabase ordersDatabase;
    private OrderLogger orderLogger;
    private OrderMessageHandler messageHandler;
    private OrderSystem orderSystem;
    private OrderFilling orderFilling;
    private OfflineDeliveryManager offlineDeliveryManager;

    // GUIs
    private OrderMenu orderMenu;
    private OrderCreate orderCreate;
    private OrderManage orderManage;
    private OrderAdminMenu orderAdminMenu;
    private OrderAlertMenu orderAlertMenu; // (Point 4)

    public OrderModule(Login plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        // 1. Setup Utilities
        MiniMessage miniMessage = MiniMessage.miniMessage();
        this.messageHandler = new OrderMessageHandler(plugin, miniMessage);
        this.orderLogger = new OrderLogger(plugin); // (Point 11)

        // 2. Setup Economy
        if (!setupEconomy()) {
            plugin.getLogger().severe("[OrderSystem] Disabled due to no Vault dependency found!");
            return;
        }

        // 3. Setup Database
        this.ordersDatabase = new OrdersDatabase(plugin); // --- FIX: Class now found ---
        this.ordersDatabase.connect();

        // 4. Setup Core Managers
        this.offlineDeliveryManager = new OfflineDeliveryManager(plugin, ordersDatabase, messageHandler); // (Point 4 & 5)
        this.orderSystem = new OrderSystem(plugin, ordersDatabase, orderLogger);
        this.orderFilling = new OrderFilling(plugin, orderSystem, ordersDatabase, messageHandler, orderLogger);

        // 5. Setup GUIs
        this.orderMenu = new OrderMenu(plugin, orderSystem, ordersDatabase, orderFilling, messageHandler);
        this.orderCreate = new OrderCreate(plugin, orderSystem, ordersDatabase, messageHandler, orderLogger);
        this.orderManage = new OrderManage(plugin, orderSystem, ordersDatabase, messageHandler, orderLogger, offlineDeliveryManager);

        // (Point 4 & 5) Admin/Alert menus need OfflineDeliveryManager
        this.orderAlertMenu = new OrderAlertMenu(plugin, ordersDatabase, messageHandler, orderLogger, offlineDeliveryManager);
        this.orderAdminMenu = new OrderAdminMenu(plugin, orderSystem, ordersDatabase, messageHandler, orderLogger, offlineDeliveryManager);

        // 6. Register Central GUI Listener (Point 10)
        plugin.getServer().getPluginManager().registerEvents(new OrderGuiListener(this), plugin);

        // 7. Register Offline Delivery Listener
        plugin.getServer().getPluginManager().registerEvents(offlineDeliveryManager, plugin);

        // 8. Register Command
        // --- FIX: Corrected constructor argument order ---
        OrderCmd orderCmd = new OrderCmd(plugin, messageHandler, orderMenu, orderManage, orderAdminMenu, orderCreate);
        Objects.requireNonNull(plugin.getCommand("order")).setExecutor(orderCmd);

        plugin.getLogger().info("Order System Module enabled.");
    }

    public void disable() {
        if (ordersDatabase != null) {
            ordersDatabase.disconnect();
        }
        plugin.getLogger().info("Order System Module disabled.");
    }

    private boolean setupEconomy() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public static Economy getEconomy() {
        return economy;
    }

    // --- FIX: Added getter for Login.java ---
    public OrderAlertMenu getOrderAlertMenu() { return orderAlertMenu; }

    // --- FIX: Added getter for OrderGuiListener ---
    public Login getPlugin() { return plugin; }

    // Getters for the central listener
    public OrderMenu getOrderMenu() { return orderMenu; }
    public OrderCreate getOrderCreate() { return orderCreate; }
    public OrderManage getOrderManage() { return orderManage; }
    public OrderAdminMenu getOrderAdminMenu() { return orderAdminMenu; }
}