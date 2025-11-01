package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.logging.Level;

public class CoinflipCmd implements CommandExecutor {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final Economy economy;
    private final CoinflipManageMenu manageMenu;
    private final CoinflipSystem coinflipSystem;
    private final CoinflipMenu coinflipMenu;

    public CoinflipCmd(Login plugin, CoinflipDatabase database, Economy economy, CoinflipManageMenu manageMenu, CoinflipSystem coinflipSystem, CoinflipMenu coinflipMenu) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.manageMenu = manageMenu;
        this.coinflipSystem = coinflipSystem;
        this.coinflipMenu = coinflipMenu;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Players only command!.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendUsage(player);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            case "create":
                handleCreate(player, args);
                return true;
            case "manage":
                manageMenu.openManageMenu(player, 0);
                return true;
            case "menu":
                coinflipMenu.openMainMenu(player, 0);
                return true;
            case "reload":
                handleReload(sender);
                return true;
            default:
                sendUsage(player);
                return true;
        }
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("coinflip.admin.reload")) {
            sender.sendMessage(plugin.formatMessage("&cYou do not have permission to use this command."));
            return;
        }
        plugin.reloadConfig();
        // Assuming you have a method in Login.java to reload the prefix
        // plugin.loadCoinflipConfig();
        sender.sendMessage(plugin.formatMessage("&aConfiguration and prefix reloaded."));
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length != 3) {
            player.sendMessage(plugin.formatMessage("&cUsage: /coinflip create <heads/tails> <amount>"));
            return;
        }

        CoinflipGame.CoinSide chosenSide;
        try {
            chosenSide = CoinflipGame.CoinSide.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            player.sendMessage(plugin.formatMessage("&cInvalid side. Choose 'heads' or 'tails'."));
            return;
        }

        double amount;
        try {
            amount = parseDoubleWithSuffix(args[2]);
            if (amount <= 0) {
                player.sendMessage(plugin.formatMessage("&cAmount must be positive."));
                return;
            }
        } catch (NumberFormatException e) {
            if (e.getMessage() != null && e.getMessage().contains("Decimals")) {
                player.sendMessage(plugin.formatMessage("&cDecimal amounts are not allowed."));
            } else {
                player.sendMessage(plugin.formatMessage("&c'" + args[2] + "' is not a valid amount. Use whole numbers (e.g., 100, 2k, 1m)."));
            }
            return;
        }

        if (!economy.has(player, amount)) {
            player.sendMessage(plugin.formatMessage("&cYou do not have enough money to create this coinflip."));
            return;
        }

        EconomyResponse withdrawResp = economy.withdrawPlayer(player, amount);
        if (!withdrawResp.transactionSuccess()) {
            player.sendMessage(plugin.formatMessage("&cFailed to withdraw funds: " + withdrawResp.errorMessage));
            return;
        }

        database.createCoinflip(player.getUniqueId(), player.getName(), chosenSide, amount)
                .whenCompleteAsync((gameId, error) -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            player.sendMessage(plugin.formatMessage("&cAn error occurred creating the coinflip. Your money has been refunded."));
                            plugin.getLogger().log(Level.SEVERE, "Failed to save coinflip for " + player.getName(), error);
                            economy.depositPlayer(player, amount);
                        } else {
                            player.sendMessage(plugin.formatMessage("&aCoinflip created for " + economy.format(amount) + " on " + chosenSide.name().toLowerCase() + "!"));
                            plugin.sendCoinflipLog(player.getName() + " created a coinflip (ID: " + gameId + ") for `" + economy.format(amount) + "` on `" + chosenSide.name() + "`");
                        }
                    });
                });
    }

    private double parseDoubleWithSuffix(String input) throws NumberFormatException {
        input = input.trim().toLowerCase();
        if (input.isEmpty()) {
            throw new NumberFormatException("Input cannot be empty.");
        }

        double multiplier = 1.0;
        char lastChar = input.charAt(input.length() - 1);

        if (input.length() > 1 && lastChar == 'k') {
            multiplier = 1_000.0;
            input = input.substring(0, input.length() - 1);
        } else if (input.length() > 1 && lastChar == 'm') {
            multiplier = 1_000_000.0;
            input = input.substring(0, input.length() - 1);
        }

        if (input.isEmpty()) {
            throw new NumberFormatException("Number part cannot be empty after removing suffix.");
        }

        double value;
        try {
            value = Double.parseDouble(input);
        } catch (NumberFormatException e) {
            throw new NumberFormatException("Invalid number format.");
        }

        value = value * multiplier;

        if (value % 1 != 0) {
            throw new NumberFormatException("Decimals not allowed.");
        }

        return value;
    }


    private void sendUsage(CommandSender sender) {
        sender.sendMessage(Component.text("--- Coinflip Commands ---", NamedTextColor.AQUA));

        sender.sendMessage(Component.text()
                .append(Component.text("/coinflip create <heads/tails> <amount>", NamedTextColor.YELLOW))
                .append(Component.text(" - Create a new coinflip (use k/m).", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/coinflip manage", NamedTextColor.YELLOW))
                .append(Component.text(" - View and cancel your pending coinflips.", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/coinflip menu", NamedTextColor.YELLOW))
                .append(Component.text(" - Open the main coinflip menu.", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/coinflip help", NamedTextColor.YELLOW))
                .append(Component.text(" - Shows this help message.", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text()
                .append(Component.text("/coinflip reload", NamedTextColor.YELLOW))
                .append(Component.text(" - (Admin) Reloads the config prefix.", NamedTextColor.GRAY))
                .build());

        sender.sendMessage(Component.text("(You can also open the menu by clicking the NPC)", NamedTextColor.GRAY));
    }
}