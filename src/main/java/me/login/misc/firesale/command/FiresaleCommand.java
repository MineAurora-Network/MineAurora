package me.login.misc.firesale.command;

import me.login.Login;
import me.login.misc.firesale.FiresaleManager;
import me.login.misc.firesale.item.FiresaleItemManager;
import me.login.misc.firesale.model.FiresaleItem;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * /firesale admin command with tab completion.
 */
public class FiresaleCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final FiresaleManager manager;
    private final FiresaleItemManager itemManager;
    private final MiniMessage miniMessage;
    private final Component serverPrefix;
    private final Component helpMessage;

    public FiresaleCommand(Login plugin, FiresaleManager manager, FiresaleItemManager itemManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemManager = itemManager;
        this.miniMessage = plugin.getComponentSerializer();
        this.serverPrefix = miniMessage.deserialize(plugin.getServerPrefix());
        this.helpMessage = miniMessage.deserialize(
                "<gold>--- Firesale Admin Help ---%nl%" +
                        "<yellow>/firesale create [hand | <item_id>] <price> <start_in> <quantity> <duration>%nl%" +
                        "<gray>e.g., /fs create heart_fragment 1000 10m 50 1h%nl%" +
                        "<gray>e.g., /fs create hand 500 0s 10 30m%nl%" +
                        "<yellow>/firesale remove <sale_id>%nl%" +
                        "<yellow>/firesale giveitem <item_id> [amount]%nl%"
        ).replaceText(config -> config.match("%nl%").replacement(Component.newline()));

        // Register this as the TabCompleter for the command(s) if present in plugin.yml
        tryRegisterTabCompleter("firesale");
        tryRegisterTabCompleter("fs"); // common alias
    }

    private void tryRegisterTabCompleter(String commandName) {
        try {
            if (plugin.getCommand(commandName) != null) {
                plugin.getCommand(commandName).setTabCompleter(this);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("This command can only be used by players."));
            return true;
        }
        Player player = (Player) sender;
        if (!player.hasPermission("login.firesale.admin")) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You do not have permission to use this command.")));
            return true;
        }

        if (args.length == 0) {
            player.sendMessage(helpMessage);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        // Log the command usage
        if (manager != null && manager.getLogger() != null) {
            try {
                manager.getLogger().logAdminCommand(player, command.getName() + " " + String.join(" ", args));
            } catch (Throwable ignored) {}
        }

        switch (subCommand) {
            case "create":
                // /fs create [hand | <item_id>] <price> <start_in> <quantity> <duration>
                handleCreate(player, args);
                break;
            case "remove":
                // /fs remove <sale_id>
                handleRemove(player, args);
                break;
            case "giveitem":
                // /fs giveitem <item_id> [amount]
                handleGiveItem(player, args);
                break;
            default:
                player.sendMessage(helpMessage);
                break;
        }
        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 6) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Usage: /fs create [hand | <item_id>] <price> <start_in> <quantity> <duration>")));
            return;
        }

        String itemIdentifier = args[1];
        ItemStack itemToSell;

        if (itemIdentifier.equalsIgnoreCase("hand")) {
            itemToSell = player.getInventory().getItemInMainHand();
            if (itemToSell == null || itemToSell.getType() == Material.AIR) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You must be holding an item to use 'hand'.")));
                return;
            }
            // Check for custom player head
            if (itemToSell.getType() == Material.PLAYER_HEAD && itemToSell.getItemMeta() != null && itemToSell.getItemMeta().hasCustomModelData()) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Custom player heads from hand are not supported. Please use an item from items.yml.")));
                return;
            }
            itemToSell = itemToSell.clone(); // Clone to prevent modification
            itemToSell.setAmount(1); // Sale is always for 1 item

        } else {
            FiresaleItem fsItem = itemManager.getItem(itemIdentifier);
            if (fsItem == null) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Invalid item ID. Item '<yellow>" + itemIdentifier + "</yellow>' not found in items.yml.")));
                return;
            }
            itemToSell = fsItem.getItemStack().clone();
        }

        try {
            double price = Double.parseDouble(args[2]);
            String startIn = args[3];
            int quantity = Integer.parseInt(args[4]);
            String duration = args[5];

            if (price <= 0 || quantity <= 0) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Price and quantity must be greater than 0.")));
                return;
            }

            Component result = manager.createSale(player, itemToSell, price, startIn, quantity, duration);
            player.sendMessage(result);

        } catch (NumberFormatException e) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Invalid number for price or quantity.")));
        }
    }

    private void handleRemove(Player player, String[] args) {
        if (args.length != 2) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Usage: /fs remove <sale_id>")));
            return;
        }
        try {
            int saleId = Integer.parseInt(args[1]);
            Component result = manager.removeSale(player, saleId);
            player.sendMessage(result);
        } catch (NumberFormatException e) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Invalid sale ID. Must be a number.")));
        }
    }

    private void handleGiveItem(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Usage: /fs giveitem <item_id> [amount]")));
            return;
        }

        String itemId = args[1];
        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Invalid amount. Must be a number.")));
                return;
            }
        }

        FiresaleItem fsItem = itemManager.getItem(itemId);
        if (fsItem == null) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Invalid item ID. Item '<yellow>" + itemId + "</yellow>' not found in items.yml.")));
            return;
        }

        ItemStack itemStack = fsItem.getItemStack().clone();
        itemStack.setAmount(amount);
        player.getInventory().addItem(itemStack);
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(
                "<green>Gave you <yellow>" + amount + "x </yellow><white>" + itemId + "</white>."
        )));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender,
                                      @NotNull Command command,
                                      @NotNull String label,
                                      @NotNull String[] args) {

        // Basic safeguard: return empty list instead of null
        try {
            if (args.length == 1) {
                return filter(Arrays.asList("create", "remove", "giveitem"), args[0]);
            }

            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("create")) {
                if (args.length == 2) {
                    List<String> suggestions = new ArrayList<>(itemManager.getAllItemIds());
                    suggestions.add("hand");
                    return filter(suggestions, args[1]);
                }
                if (args.length == 3) return Arrays.asList("<price>");
                if (args.length == 4) return filter(Arrays.asList("0s", "1m", "10m", "30m", "1h"), args[3]);
                if (args.length == 5) return Arrays.asList("<quantity>");
                if (args.length == 6) return filter(Arrays.asList("10m", "30m", "1h", "12h", "1d"), args[5]);

            } else if (subCommand.equals("remove")) {
                if (args.length == 2) return Arrays.asList("<sale_id>");

            } else if (subCommand.equals("giveitem")) {
                if (args.length == 2) {
                    return filter(new ArrayList<>(itemManager.getAllItemIds()), args[1]);
                }
                if (args.length == 3) return Arrays.asList("1", "16", "32", "64");
            }
        } catch (Throwable ignored) {
        }

        return new ArrayList<>();
    }

    private List<String> filter(List<String> list, String input) {
        if (input == null) input = "";
        final String lower = input.toLowerCase();
        return list.stream()
                .filter(s -> s != null && s.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}