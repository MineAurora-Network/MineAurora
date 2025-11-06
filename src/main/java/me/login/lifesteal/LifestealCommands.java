package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
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
    private final DeadPlayerManager deadPlayerManager;
    private final LuckPerms luckPermsApi;
    private final LifestealLogger logger; // <-- ADDED FIELD

    // --- CONSTRUCTOR UPDATED ---
    public LifestealCommands(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, DeadPlayerManager deadPlayerManager, LuckPerms luckPermsApi, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.deadPlayerManager = deadPlayerManager;
        this.luckPermsApi = luckPermsApi;
        this.logger = logger; // <-- STORE LOGGER
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String cmdName = command.getName().toLowerCase();

        // Handle the standalone /withdrawhearts command
        if (cmdName.equals("withdrawhearts")) {
            return handleWithdraw(sender, args);
        }

        // Handle the base /lifesteal and /ls commands
        if (cmdName.equals("lifesteal") || cmdName.equals("ls")) {
            if (args.length == 0) {
                sendUsage(sender); // Send help/usage message
                return true;
            }

            String subCommand = args[0].toLowerCase();
            // Create new array for subcommand arguments
            String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

            switch (subCommand) {
                case "give":
                    return handleLsGive(sender, subArgs);
                case "sethearts":
                    return handleSet(sender, subArgs);
                case "checkhearts":
                    return handleCheck(sender, subArgs);
                case "revive":
                    return handleRevive(sender, subArgs); // New command handler
                default:
                    sendUsage(sender);
                    return true;
            }
        }
        return false;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<gold>--- Lifesteal Commands ---")));
        if (sender.hasPermission("lifesteal.admin.give")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<yellow>/ls give <heart|revive_beacon> <amount>")));
        }
        if (sender.hasPermission("lifesteal.admin.set")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<yellow>/ls sethearts <player> <amount>")));
        }
        if (sender.hasPermission("lifesteal.admin.revive")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<yellow>/ls revive <player>")));
        }
        if (sender.hasPermission("lifesteal.check")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<yellow>/ls checkhearts <player>")));
        }
        if (sender.hasPermission("lifesteal.withdraw")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<yellow>/withdrawhearts <amount>")));
        }
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
            player.sendMessage(itemManager.formatMessage("<red>Usage: /ls give <heart | revive_beacon> <amount>"));
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
            if (logger != null) {
                logger.logAdmin("Admin `" + player.getName() + "` gave themselves " + amount + " heart(s).");
            }
        } else if (itemName.equals("revive_beacon") || itemName.equals("beacon")) {
            player.getInventory().addItem(itemManager.getReviveBeaconItem(amount));
            player.sendMessage(itemManager.formatMessage("<green>Gave you " + amount + " revive beacon(s)."));
            if (logger != null) {
                logger.logAdmin("Admin `" + player.getName() + "` gave themselves " + amount + " revive beacon(s).");
            }
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
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Usage: /ls sethearts <player> <amount>")));
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

        // --- MODIFICATION (Request 2) ---
        // LuckPerms Rank Weight Check
        if (luckPermsApi != null && sender instanceof Player admin) {
            // Check if admin is targeting themselves
            if (admin.getUniqueId().equals(target.getUniqueId())) {
                // Admin is targeting themselves, allow it.
            } else {
                // Admin is targeting someone else, check hierarchy
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
        }
        // Console or admin with higher weight (or self-targeting) proceeds
        // --- END MODIFICATION ---

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

        if (logger != null) {
            logger.logAdmin("`" + sender.getName() + "` set `" + target.getName() + "`'s hearts to " + actualAmount + ".");
        }
        return true;
    }

    private boolean handleCheck(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifesteal.check")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>You do not have permission to use this command.")));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Usage: /ls checkhearts <player>")));
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

    private boolean handleRevive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("lifesteal.admin.revive")) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>You do not have permission to use this command.")));
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Usage: /ls revive <player>")));
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>Player '" + args[0] + "' not found.")));
            return true;
        }

        if (!deadPlayerManager.isDead(target.getUniqueId())) {
            sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<red>That player is not dead.")));
            return true;
        }

        // Revive the player
        deadPlayerManager.removeDeadPlayer(target.getUniqueId());
        // Reset hearts to default
        int newHearts = lifestealManager.setHearts(target.getUniqueId(), lifestealManager.DEFAULT_HEARTS);

        sender.sendMessage(ItemManager.toLegacy(itemManager.formatMessage("<green>You have revived " + target.getName() + " and set their hearts to " + newHearts + ".")));

        if (logger != null) {
            logger.logAdmin("`" + sender.getName() + "` revived `" + target.getName() + "`.");
        }

        Player onlineTarget = target.getPlayer();
        if (onlineTarget != null && onlineTarget.isOnline()) {
            onlineTarget.setGameMode(GameMode.SURVIVAL);
            onlineTarget.sendMessage(itemManager.formatMessage("<green>You have been revived by an admin!</green>"));
            lifestealManager.updatePlayerHealth(onlineTarget);
        }
        return true;
    }

    private int getWeight(User user) {
        String weightString = user.getCachedData().getMetaData().getMetaValue("weight");
        if (weightString != null) {
            try {
                return Integer.parseInt(weightString);
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {

        if (command.getName().equalsIgnoreCase("lifesteal") || command.getName().equalsIgnoreCase("ls")) {
            if (args.length == 1) {
                List<String> subcommands = new ArrayList<>();
                if (sender.hasPermission("lifesteal.admin.give")) subcommands.add("give");
                if (sender.hasPermission("lifesteal.admin.set")) subcommands.add("sethearts");
                if (sender.hasPermission("lifesteal.check")) subcommands.add("checkhearts");
                if (sender.hasPermission("lifesteal.admin.revive")) subcommands.add("revive");

                return subcommands.stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }

            if (args.length > 0) {
                String subCmd = args[0].toLowerCase();
                if (subCmd.equals("give")) {
                    if (args.length == 2) {
                        return Arrays.asList("heart", "revive_beacon").stream()
                                .filter(s -> s.startsWith(args[1].toLowerCase()))
                                .collect(Collectors.toList());
                    }
                    if (args.length == 3) {
                        return Arrays.asList("1", "10", "32", "64");
                    }
                }
                if (subCmd.equals("sethearts") || subCmd.equals("checkhearts") || subCmd.equals("revive")) {
                    if (args.length == 2) {
                        return null;
                    }
                }
            }
        }

        if (command.getName().equalsIgnoreCase("withdrawhearts")) {
            if (args.length == 1) {
                return Arrays.asList("1", "2", "5");
            }
        }

        return new ArrayList<>();
    }
}