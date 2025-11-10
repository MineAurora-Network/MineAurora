package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class CoinflipCmd implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final CoinflipDatabase database; // Added back
    private final Economy economy; // Added back
    private final CoinflipManageMenu manageMenu;
    private final CoinflipSystem coinflipSystem;
    private final CoinflipMenu coinflipMenu;
    private final CoinflipAdminMenu adminMenu; // [Req 8]
    private final MessageManager msg; // [Req 4]
    private final CoinflipLogger logger; // [Req 9]

    // Constructor updated to include database and economy
    public CoinflipCmd(Login plugin, CoinflipDatabase database, Economy economy, CoinflipMenu coinflipMenu, CoinflipManageMenu manageMenu, CoinflipAdminMenu adminMenu, CoinflipSystem coinflipSystem, MessageManager msg, CoinflipLogger logger) {
        this.plugin = plugin;
        this.database = database; // Added back
        this.economy = economy; // Added back
        this.coinflipMenu = coinflipMenu;
        this.manageMenu = manageMenu;
        this.adminMenu = adminMenu;
        this.coinflipSystem = coinflipSystem;
        this.msg = msg;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        if (args.length == 0) {
            coinflipMenu.openMainMenu(player, 0);
            return true;
        }

        String subCommand = args[0].toLowerCase();

        switch (subCommand) {
            // [FIX] Added 'create' case back
            case "create":
                handleCreate(player, args);
                return true;
            // [Req 4] Added 'join' command
            case "join":
                handleJoin(player, args);
                return true;
            case "manage":
                manageMenu.openManageMenu(player, 0);
                return true;
            case "menu":
                coinflipMenu.openMainMenu(player, 0);
                return true;
            case "adminmenu": // [Req 8]
                if (player.hasPermission("login.coinflip.admin")) {
                    adminMenu.openAdminMenu(player, 0);
                } else {
                    msg.send(player, "&cYou do not have permission to use this command.");
                }
                return true;
            case "msgtoggle": // [Req 4]
            case "toggle":
                boolean currentToggle = msg.getToggle(player.getUniqueId());
                boolean newToggle = !currentToggle;
                msg.saveToggle(player.getUniqueId(), newToggle);
                if (newToggle) {
                    msg.send(player, "&aCoinflip broadcast messages enabled.");
                } else {
                    msg.send(player, "&cCoinflip broadcast messages disabled.");
                }
                return true;
            case "help":
            default:
                sendHelpMessage(player);
                return true;
        }
    }

    // [Req 4] Added handleJoin method
    private void handleJoin(Player player, String[] args) {
        if (args.length != 2) {
            msg.send(player, "&cUsage: /coinflip join <id>");
            return;
        }

        long gameId;
        try {
            gameId = Long.parseLong(args[1]);
        } catch (NumberFormatException e) {
            msg.send(player, "&c'" + args[1] + "' is not a valid game ID.");
            return;
        }

        // Prevent double-clicks
        // [FIX] Changed to coinflipMenu
        if (coinflipMenu.getPlayersChallengingSet().contains(player.getUniqueId())) {
            return;
        }

        CoinflipGame gameToJoin = coinflipSystem.getPendingGameById(gameId);

        if (gameToJoin == null) {
            msg.send(player, "&cCould not find a pending coinflip with that ID. It may have started or been cancelled.");
            return;
        }

        // Add lock
        // [FIX] Changed to coinflipMenu
        coinflipMenu.getPlayersChallengingSet().add(player.getUniqueId());

        // Run the join logic
        // This method is async and handles removing the lock on success/fail
        coinflipSystem.startCoinflipGame(player, gameToJoin);
    }

    // [FIX] Added handleCreate method back
    private void handleCreate(Player player, String[] args) {
        if (args.length != 3) {
            msg.send(player, "&cUsage: /coinflip create <heads/tails> <amount>");
            return;
        }

        CoinflipGame.CoinSide chosenSide;
        try {
            chosenSide = CoinflipGame.CoinSide.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            msg.send(player, "&cInvalid side. Choose 'heads' or 'tails'.");
            return;
        }

        double amount;
        try {
            amount = parseDoubleWithSuffix(args[2]);
            if (amount <= 0) {
                msg.send(player, "&cAmount must be positive.");
                return;
            }
        } catch (NumberFormatException e) {
            if (e.getMessage() != null && e.getMessage().contains("Decimals")) {
                msg.send(player, "&cDecimal amounts are not allowed.");
            } else {
                msg.send(player, "&c'" + args[2] + "' is not a valid amount. Use whole numbers (e.g., 100, 2k, 1m).");
            }
            return;
        }

        if (!economy.has(player, amount)) {
            msg.send(player, "&cYou do not have enough money to create this coinflip.");
            return;
        }

        EconomyResponse withdrawResp = economy.withdrawPlayer(player, amount);
        if (!withdrawResp.transactionSuccess()) {
            msg.send(player, "&cFailed to withdraw funds: " + withdrawResp.errorMessage);
            return;
        }

        database.createCoinflip(player.getUniqueId(), player.getName(), chosenSide, amount)
                .whenCompleteAsync((gameId, error) -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (error != null) {
                            msg.send(player, "&cAn error occurred creating the coinflip. Your money has been refunded.");
                            plugin.getLogger().log(Level.SEVERE, "Failed to save coinflip for " + player.getName(), error);
                            economy.depositPlayer(player, amount);
                        } else {
                            msg.send(player, "&aCoinflip created for " + economy.format(amount) + " on " + chosenSide.name().toLowerCase() + "!");

                            // [Req 4] & [Req 9] Broadcast and Log
                            // String broadcastMsg = "<yellow>" + player.getName() + "</yellow> created a coinflip for <gold>" + economy.format(amount) + "</gold>!";

                            // [Req 4] Create clickable broadcast message
                            String clickCommand = "/cf join " + gameId;
                            Component broadcastComponent = MiniMessage.miniMessage().deserialize(
                                    "<yellow>" + player.getName() + "</yellow> created a coinflip for <gold>" + economy.format(amount) + "</gold>! " +
                                            "(ID: " + gameId + ") <green><click:suggest_command:'" + clickCommand + "'>[Click to Join]</click></green>"
                            );
                            msg.broadcast(broadcastComponent);

                            logger.logGame(player.getName() + " created a coinflip (ID: " + gameId + ") for `" + economy.format(amount) + "` on `" + chosenSide.name() + "`");

                            // [Req 4] Manually refresh cache after creation
                            coinflipSystem.getPendingGames(true);
                        }
                    });
                });
    }

    // [FIX] Added parseDoubleWithSuffix method back
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

    // [FIX] Renamed and re-added 'create' command to help
    // [Req 2] Updated to use new multi-line send method
    private void sendHelpMessage(Player player) {
        List<String> helpLines = new ArrayList<>();
        helpLines.add("&b&lCoinflip Help Menu");
        helpLines.add("&e/cf create <heads/tails> <amount> &7- Create a new coinflip.");
        helpLines.add("&e/cf join <id> &7- Join a pending coinflip by its ID.");
        helpLines.add("&e/cf menu &7- Opens the main coinflip menu.");
        helpLines.add("&e/cf manage &7- Manage your pending coinflips.");
        helpLines.add("&e/cf msgtoggle &7- Toggles coinflip broadcast messages.");

        if (player.hasPermission("login.coinflip.admin")) {
            helpLines.add("&c/cf adminmenu &7- Opens the admin menu to cancel games.");
        }

        msg.send(player, helpLines);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>(Arrays.asList("help", "manage", "msgtoggle", "menu", "create", "join")); // [Req 4] Added 'join'
            if (sender.hasPermission("login.coinflip.admin")) {
                completions.add("adminmenu");
            }
            return completions.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("heads", "tails").stream()
                    .filter(s -> s.startsWith(args[1].toLowerCase()))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            return Arrays.asList("1k", "10k", "100k", "1m");
        }
        // [Req 4] Tab complete for /cf join
        if (args.length == 2 && args[0].equalsIgnoreCase("join")) {
            // Suggest IDs of pending games
            return coinflipSystem.getPendingGames(false).join().stream()
                    .map(g -> String.valueOf(g.getGameId()))
                    .filter(s -> s.startsWith(args[1]))
                    .toList();
        }
        return new ArrayList<>();
    }
}