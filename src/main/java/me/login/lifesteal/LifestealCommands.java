package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

public class LifestealCommands implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final ItemManager itemManager;
    private final LifestealManager lifestealManager;
    private final LuckPerms luckPermsApi;

    public LifestealCommands(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, LuckPerms luckPermsApi) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.luckPermsApi = luckPermsApi; // Can be null if LuckPerms is not found
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        switch (cmdName) {
            case "withdrawhearts":
                return handleWithdraw(sender, args);
            case "lsgive":
                return handleLsGive(sender, args);
            case "sethearts":
                return handleSet(sender, args);
            case "checkhearts":
                return handleCheck(sender, args);
        }
        return false;
    }

    private boolean handleWithdraw(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Only players can use this command.")));
            return true;
        }

        if (!player.hasPermission("lifesteal.withdraw")) {
            player.sendMessage(itemManager.formatMessage("<red>You do not have permission to use this command."));
            return true;
        }

        if (args.length != 1) {
            player.sendMessage(itemManager.formatMessage("<red>Usage: /withdrawhearts <amount>"));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[0]);
            if (amount <= 0) {
                player.sendMessage(itemManager.formatMessage("<red>Amount must be a positive number."));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(itemManager.formatMessage("<red>Invalid amount. Please enter a number."));
            return true;
        }

        lifestealManager.withdrawHearts(player, amount);
        return true;
    }

    private boolean handleLsGive(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Only players can use this command.")));
            return true;
        }

        if (!player.hasPermission("lifesteal.admin.give")) {
            player.sendMessage(itemManager.formatMessage("<red>You do not have permission to use this command."));
            return true;
        }

        if (args.length != 2) {
            player.sendMessage(itemManager.formatMessage("<red>Usage: /lsgive <heart | revive_beacon> <amount>"));
            return true;
        }

        String itemName = args[0].toLowerCase();
        int amount;

        try {
            amount = Integer.parseInt(args[1]);
            if (amount <= 0) {
                player.sendMessage(itemManager.formatMessage("<red>Amount must be a positive number."));
                return true;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(itemManager.formatMessage("<red>Invalid amount. Please enter a number."));
            return true;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(itemManager.formatMessage("<red>You don't have space in your inventory!"));
            return true;
        }

        if (itemName.equals("heart") || itemName.equals("hearts")) {
            player.getInventory().addItem(itemManager.getHeartItem(amount));
            player.sendMessage(itemManager.formatMessage("<green>Gave you " + amount + " heart(s)."));
            itemManager.sendLog("Admin " + player.getName() + " gave themselves " + amount + " heart(s).");
        } else if (itemName.equals("revive_beacon") || itemName.equals("beacon")) {
            player.getInventory().addItem(itemManager.getReviveBeaconItem(amount));
            player.sendMessage(itemManager.formatMessage("<green>Gave you " + amount + " revive beacon(s)."));
            itemManager.sendLog("Admin " + player.getName() + " gave themselves " + amount + " revive beacon(s).");
        } else {
            player.sendMessage(itemManager.formatMessage("<red>Invalid item. Use 'heart' or 'revive_beacon'."));
            return true;
        }

        return true;
    }

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifesteal.admin.set")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>You do not have permission to use this command.")));
            return true;
        }

        if (args.length != 2) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Usage: /sethearts <player> <amount>")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Player '" + args[0] + "' not found.")));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Invalid amount. Please enter a number.")));
            return true;
        }

        // LuckPerms Rank Weight Check
        if (luckPermsApi != null && sender instanceof Player admin) {
            User adminUser = luckPermsApi.getUserManager().getUser(admin.getUniqueId());
            User targetUser = luckPermsApi.getUserManager().getUser(target.getUniqueId());

            if (adminUser != null && targetUser != null) {
                int adminWeight = getWeight(adminUser);
                int targetWeight = getWeight(targetUser);

                // Admin has less or equal weight -> cannot modify
                if (adminWeight <= targetWeight) {
                    admin.sendMessage(itemManager.formatMessage("<red>You cannot modify the hearts of a player with equal or higher rank."));
                    return true;
                }
            }
        }
        // Console or admin with higher weight proceeds

        int actualAmount = lifestealManager.setHearts(target.getUniqueId(), amount);
        sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage(
                "<green>Set " + target.getName() + "'s hearts to " + actualAmount + "."
        )));

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null) {
            onlineTarget.sendMessage(itemManager.formatMessage(
                    "<yellow>An admin set your hearts to " + actualAmount + "."
            ));
        }

        itemManager.sendLog(sender.getName() + " set " + target.getName() + "'s hearts to " + actualAmount + ".");
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifesteal.check")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>You do not have permission to use this command.")));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Usage: /checkhearts <player>")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Player '" + args[0] + "' not found.")));
            return true;
        }

        int hearts = lifestealManager.getHearts(target.getUniqueId());
        Component message = Component.text(target.getName(), NamedTextColor.YELLOW)
                .append(Component.text(" currently has ", NamedTextColor.GRAY))
                .append(Component.text(hearts, NamedTextColor.RED))
                .append(Component.text(" heart(s).", NamedTextColor.GRAY));

        sender.sendMessage(itemManager.formatMessage(message));
        return true;
    }

    private int getWeight(User user) {
        // Get weight from meta key "weight"
        String weightString = user.getCachedData().getMetaData().getMetaValue("weight");
        if (weightString != null) {
            try {
                return Integer.parseInt(weightString);
            } catch (NumberFormatException e) {
                return 0; // Default weight if meta is invalid
            }
        }
        return 0; // Default weight if no meta
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("lsgive")) {
            if (args.length == 1) {
                return Arrays.asList("heart", "revive_beacon").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 2) {
                return Arrays.asList("1", "10", "32", "64");
            }
        }

        if (command.getName().equalsIgnoreCase("sethearts") || command.getName().equalsIgnoreCase("checkhearts")) {
            if (args.length == 1) {
                // Suggest online player names
                return null;
            }
        }

        return new ArrayList<>();
    }
}