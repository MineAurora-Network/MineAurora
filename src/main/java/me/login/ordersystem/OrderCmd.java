package me.login.ordersystem;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class OrderCmd implements CommandExecutor {

    private final Login plugin;
    private final OrderSystem orderSystem;
    private final OrderCreate orderCreate; // Handles /order create
    private final OrderMenu orderMenu;     // Handles /order menu
    private final OrderManage orderManage;   // Handles /order manage
    // --- ADDED ---
    private final OrderAdminMenu orderAdminMenu; // Handles /order adminmenu

    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    // --- CONSTRUCTOR UPDATED ---
    public OrderCmd(Login plugin, OrderSystem orderSystem, OrderMenu orderMenu, OrderManage orderManage, OrderAdminMenu orderAdminMenu) {
        this.plugin = plugin;
        this.orderSystem = orderSystem;
        this.orderMenu = orderMenu;
        this.orderManage = orderManage;
        this.orderAdminMenu = orderAdminMenu;
        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        // Instantiate OrderCreate here
        this.orderCreate = new OrderCreate(plugin, orderSystem);

        // Register listener for OrderCreate GUI clicks
        plugin.getServer().getPluginManager().registerEvents(orderCreate, plugin);
    }
    // --- END CONSTRUCTOR UPDATE ---

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("order")) return false;
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cPlayers only.");
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
            case "list": case "menu":
                orderMenu.openMenuListGui(player, 0);
                return true;
            case "manage":
                orderManage.openManageGui(player, 0);
                return true;
            // --- ADDED: Admin Menu ---
            case "adminmenu":
                if (!player.hasPermission("order.admin")) {
                    player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You do not have permission to use this command.</red>")));
                    return true;
                }
                orderAdminMenu.openAdminGui(player, 0);
                return true;
            // --- END ADD ---
            default:
                sendUsage(player);
                return true;
        }
    }

    // --- MODIFIED: Use Kyori Adventure components ---
    private void sendUsage(Player player) {
        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<aqua>--- Order System ---</aqua>")));
        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>/order create</yellow><gray> - Create order.</gray>")));
        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>/order menu</yellow><gray> - View active orders.</gray>")));
        player.sendMessage(serverPrefix.append(miniMessage.deserialize("<yellow>/order manage</yellow><gray> - Manage your orders.</gray>")));
        // --- ADDED ---
        if (player.hasPermission("order.admin")) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>/order adminmenu</red><gray> - Admin order management.</gray>")));
        }
    }
}