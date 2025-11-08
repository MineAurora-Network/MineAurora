package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // <-- ADDED IMPORT
import java.util.List;
import java.util.stream.Collectors;
import net.kyori.adventure.text.format.TextDecoration; // <-- ADDED IMPORT
import java.util.stream.Stream; // <-- ADDED IMPORT

public class TokenCommands implements CommandExecutor, TabCompleter, Listener {

    private final Login plugin;
    private final TokenManager manager;
    private final TokenShopGUI tokenShopGUI;
    private final MiniMessage mm;
    private final Component prefix;
    private static final String TOKEN_MENU_METADATA = "TokenMenu";

    public TokenCommands(Login plugin, TokenManager manager, TokenShopGUI tokenShopGUI) {
        this.plugin = plugin;
        this.manager = manager;
        this.tokenShopGUI = tokenShopGUI;
        this.mm = manager.getMiniMessage();
        this.prefix = manager.getPrefix();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sendMsg(sender, "<red>This command can only be run by a player.");
                return true;
            }
            openTokenMenu(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        if (subCommand.equals("balance")) {
            handleBalance(sender);
            return true;
        }

        // Admin commands
        if (!sender.hasPermission("token.admin")) {
            sendMsg(sender, "<red>Unknown command. Usage: /token or /token balance</red>");
            return true;
        }

        if (args.length < 3) {
            sendMsg(sender, "<red>Usage: /token <add|remove|set> <player> <amount></red>");
            return true;
        }

        String targetName = args[1];
        String amountStr = args[2];
        long amount;

        try {
            amount = Long.parseLong(amountStr);
            // --- FIX: Cleaned up logic ---
            if (subCommand.equals("set")) {
                if (amount < 0) {
                    sendMsg(sender, "<red>Amount must be a positive number.</red>");
                    return true;
                }
            } else { // add or remove
                if (amount <= 0) {
                    sendMsg(sender, "<red>Amount must be a positive number greater than 0.</red>");
                    return true;
                }
            }
            // --- END FIX ---
        } catch (NumberFormatException e) {
            sendMsg(sender, "<red>'" + amountStr + "' is not a valid number.</red>");
            return true;
        }

        switch (subCommand) {
            case "add":
                manager.addTokens(sender, targetName, amount);
                break;
            case "remove":
                manager.removeTokens(sender, targetName, amount);
                break;
            case "set":
                manager.setTokens(sender, targetName, amount);
                break;
            default:
                sendMsg(sender, "<red>Unknown command. Usage: /token <add|remove|set|balance></red>");
        }
        return true;
    }

    private void handleBalance(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMsg(sender, "<red>This command can only be run by a player.");
            return; // This is line 113, return; is correct.
        }
        manager.getTokenBalance(player.getUniqueId()).thenAccept(balance -> {
            // --- FIX: Replaced unicode ---
            sendMsg(player, "<gray>Your token balance: <gold>" + balance + " ☆</gold></gray>");
        });
    }

    private void openTokenMenu(Player player) {
        // --- FIX: Replaced unicode ---
        Inventory gui = Bukkit.createInventory(null, 36, mm.deserialize("<dark_gray>Token Menu</dark_gray>")); // 4 rows

        // How to get
        ItemStack howTo = new ItemStack(Material.SUNFLOWER);
        ItemMeta howToMeta = howTo.getItemMeta();
        howToMeta.displayName(mm.deserialize("<gold><bold>How to get Tokens</bold></gold>").decoration(TextDecoration.ITALIC, false));
        howToMeta.lore(Arrays.asList(
                mm.deserialize("<gray>You can earn tokens from:</gray>").decoration(TextDecoration.ITALIC, false),
                mm.deserialize("<white> - <aqua>/dailyreward</aqua></white>").decoration(TextDecoration.ITALIC, false),
                mm.deserialize("<white> - <aqua>/playtimerewards</aqua></white>").decoration(TextDecoration.ITALIC, false),
                mm.deserialize("<white> - <green>Server Events</green></white>").decoration(TextDecoration.ITALIC, false)
        ));
        howTo.setItemMeta(howToMeta);
        gui.setItem(11, howTo);

        // Token Shop
        ItemStack shop = new ItemStack(Material.CHEST);
        ItemMeta shopMeta = shop.getItemMeta();
        shopMeta.displayName(mm.deserialize("<green><bold>Open Token Shop</bold></green>").decoration(TextDecoration.ITALIC, false));
        shopMeta.lore(List.of(
                mm.deserialize("<gray>Click to spend your tokens!</gray>").decoration(TextDecoration.ITALIC, false)
        ));
        shop.setItemMeta(shopMeta);
        gui.setItem(14, shop);

        // Close button
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(mm.deserialize("<red><bold>Close</bold></red>").decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        gui.setItem(31, close); // Slot 31

        player.setMetadata(TOKEN_MENU_METADATA, new FixedMetadataValue(plugin, true));
        player.openInventory(gui);
    }

    @EventHandler
    public void onMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata(TOKEN_MENU_METADATA)) return;

        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null) return;

        if (clicked.getType() == Material.CHEST) {
            // Open Token Shop
            player.closeInventory();
            tokenShopGUI.openGUI(player);
        } else if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("token.admin")) {
            if (args.length == 1) {
                // --- FIX: Use Stream.of ---
                return Stream.of("balance")
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            // --- FIX: Use Stream.of ---
            return Stream.of("add", "remove", "set", "balance")
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set"))) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args.length == 3 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("set"))) {
            return Arrays.asList("1", "10", "100", "1000");
        }

        return Collections.emptyList();
    }

    private void sendMsg(CommandSender sender, String message) {
        sender.sendMessage(prefix.append(mm.deserialize(message)));
    }
}