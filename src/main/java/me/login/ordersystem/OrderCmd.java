package me.login.ordersystem;

import me.login.Login;
import me.login.ordersystem.gui.OrderAdminMenu;
import me.login.ordersystem.gui.OrderCreate;
import me.login.ordersystem.gui.OrderManage;
import me.login.ordersystem.gui.OrderMenu;
import me.login.ordersystem.util.OrderMessageHandler;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Handles all /order sub-commands.
 * (Refactored for Points 1, 2, 4, 6, 13)
 */
public class OrderCmd implements CommandExecutor {

    private final Login plugin;
    private final OrderMessageHandler messageHandler;
    private final OrderCreate orderCreate;
    private final OrderMenu orderMenu;
    private final OrderManage orderManage;
    private final OrderAdminMenu orderAdminMenu;

    public OrderCmd(Login plugin, OrderMessageHandler messageHandler, OrderMenu orderMenu, OrderManage orderManage, OrderAdminMenu orderAdminMenu, OrderCreate orderCreate) {
        this.plugin = plugin;
        this.messageHandler = messageHandler;
        this.orderMenu = orderMenu;
        this.orderManage = orderManage;
        this.orderAdminMenu = orderAdminMenu;
        this.orderCreate = orderCreate;
        // OrderCreate listener is no longer registered here, it's done by OrderModule
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only."); // Keep legacy for console
            return true;
        }

        if (args.length == 0) {
            orderMenu.openMenuListGui(player, 0);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "create":
                orderCreate.openCreateGui(player, 0);
                return true;
            case "list":
            case "menu":
                orderMenu.openMenuListGui(player, 0);
                return true;
            case "manage":
                orderManage.openManageGui(player, 0);
                return true;
            case "adminmenu":
                if (!player.hasPermission("order.admin")) {
                    messageHandler.sendMessage(player, "<red>You do not have permission to use this command.</red>");
                    return true;
                }
                orderAdminMenu.openAdminGui(player, 0);
                return true;

            // (Point 4) New command for staff to inspect alerts
            case "alert":
                if (!player.hasPermission("staff.staff")) { // Use "staff.staff" as requested
                    messageHandler.sendMessage(player, "<red>You do not have permission to use this command.</red>");
                    return true;
                }
                if (args.length < 2) {
                    messageHandler.sendMessage(player, "<red>Usage: /order alert <order-id></red>");
                    return true;
                }
                try {
                    long orderId = Long.parseLong(args[1]);
                    // The OrderAlertMenu class will handle opening
                    plugin.getOrderAlertMenu().openAlertGui(player, orderId);
                } catch (NumberFormatException e) {
                    messageHandler.sendMessage(player, "<red>'" + args[1] + "' is not a valid order ID.</red>");
                }
                return true;

            default:
                sendUsage(player);
                return true;
        }
    }

    /**
     * (Points 2, 6) Send usage message with correct prefix and newlines.
     */
    private void sendUsage(Player player) {
        StringBuilder usage = new StringBuilder("<aqua>--- Order System ---</aqua>%nl%");
        usage.append("<yellow>/order create</yellow><gray> - Create order.</gray>%nl%");
        usage.append("<yellow>/order menu</yellow><gray> - View active orders.</gray>%nl%");
        usage.append("<yellow>/order manage</yellow><gray> - Manage your orders.</gray>");

        if (player.hasPermission("order.admin")) {
            usage.append("%nl%<red>/order adminmenu</red><gray> - Admin order management.</gray>");
        }

        messageHandler.sendMultiLine(player, usage.toString());
    }
}