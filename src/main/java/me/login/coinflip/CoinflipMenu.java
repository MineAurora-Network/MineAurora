package me.login.coinflip;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType; // <-- Was missing in my previous output, added now
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.minimessage.MiniMessage; // <-- THIS IS THE FIX

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CoinflipMenu implements Listener {

    private final Login plugin;
    private final CoinflipDatabase database;
    private CoinflipSystem coinflipSystem;
    private final Economy economy;
    private final MessageManager msg; // [Req 1]
    private final CoinflipLogger logger; // [Req 9]

    private List<CoinflipGame> pendingGamesCache = Collections.synchronizedList(new ArrayList<>());
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 10 * 1000;
    private boolean isRefreshingCache = false;

    private static final int GUI_SIZE = 36;
    private static final int GAMES_PER_PAGE = 27;
    private final NamespacedKey gameIdKey;
    public static final String GUI_MAIN_METADATA = "CoinflipMainMenu";

    // [Req 2] Sets to prevent double-clicks/spam
    private final Set<UUID> playersChallenging = new HashSet<>();
    private final Set<UUID> playersCreating = new HashSet<>();

    public CoinflipMenu(Login plugin, CoinflipDatabase database, Economy economy, MessageManager msg, CoinflipLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.msg = msg; // [Req 1]
        this.logger = logger; // [Req 9]
        this.gameIdKey = new NamespacedKey(plugin, "cf_game_id");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        getPendingGames(true);
    }

    public void setCoinflipSystem(CoinflipSystem coinflipSystem) {
        this.coinflipSystem = coinflipSystem;
    }

    public Set<UUID> getPlayersChallengingSet() {
        return playersChallenging;
    }

    private CompletableFuture<List<CoinflipGame>> getPendingGames(boolean force) {
        long now = System.currentTimeMillis();
        if (isRefreshingCache) {
            return CompletableFuture.completedFuture(new ArrayList<>(pendingGamesCache));
        }
        if (!force && (now - lastCacheUpdateTime < CACHE_DURATION_MS)) {
            return CompletableFuture.completedFuture(new ArrayList<>(pendingGamesCache)); // Return copy
        }

        isRefreshingCache = true;
        return database.loadPendingCoinflips().whenComplete((games, error) -> {
            if (games != null) {
                this.pendingGamesCache = Collections.synchronizedList(games); // Update cache
                this.lastCacheUpdateTime = System.currentTimeMillis();
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refresh coinflip games cache", error);
            }
            isRefreshingCache = false;
        });
    }

    public void openMainMenu(Player player, int page) {
        CompletableFuture<List<CoinflipGame>> gamesFuture = getPendingGames(false);
        CompletableFuture<CoinflipStats> statsFuture = database.loadPlayerStats(player.getUniqueId());

        CompletableFuture.allOf(gamesFuture, statsFuture).whenCompleteAsync((v, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error loading data for coinflip menu", error);
                Bukkit.getScheduler().runTask(plugin, () -> msg.send(player, "&cError loading coinflip menu."));
                return;
            }

            List<CoinflipGame> games = gamesFuture.join();
            CoinflipStats stats = statsFuture.join();

            Bukkit.getScheduler().runTask(plugin, () -> {
                int totalGames = games.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalGames / GAMES_PER_PAGE));
                int finalPage = Math.max(0, Math.min(page, totalPages - 1));

                // [Req 5] & [Req 6] Use Component title with DARK_GRAY
                Component title = Component.text("Coinflip Menu (Page " + (finalPage + 1) + "/" + totalPages + ")", NamedTextColor.DARK_GRAY);
                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, LegacyComponentSerializer.legacySection().serialize(title));

                int startIndex = finalPage * GAMES_PER_PAGE;
                int endIndex = Math.min(startIndex + GAMES_PER_PAGE, totalGames);
                List<CoinflipGame> pageGames = games.subList(startIndex, endIndex);
                for (int i = 0; i < pageGames.size(); i++) {
                    gui.setItem(i, createGameDisplayItem(pageGames.get(i)));
                }

                ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
                for (int i = 27; i < 36; i++) gui.setItem(i, grayPane);

                if (finalPage > 0) gui.setItem(27, createGuiItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW)));

                gui.setItem(29, createGuiItem(Material.OAK_SIGN,
                        Component.text("Create Coinflip", NamedTextColor.GREEN),
                        Component.text("Click here to start", NamedTextColor.GRAY),
                        Component.text("creating a new coinflip.", NamedTextColor.GRAY)
                ));

                gui.setItem(31, createGuiItem(Material.CLOCK, Component.text("Refresh List", NamedTextColor.AQUA)));
                gui.setItem(33, createStatsItem(player, stats));

                if (finalPage < totalPages - 1) gui.setItem(35, createGuiItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW)));

                player.setMetadata(GUI_MAIN_METADATA, new FixedMetadataValue(plugin, finalPage));
                player.openInventory(gui);
            });

        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    private ItemStack createGameDisplayItem(CoinflipGame game) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player owner = Bukkit.getPlayer(game.getCreatorUUID());
            if (owner != null) meta.setOwningPlayer(owner);
            else meta.setOwner(game.getCreatorName());

            meta.displayName(Component.text(game.getCreatorName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Amount: ", NamedTextColor.GRAY).append(Component.text(economy.format(game.getAmount()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));

            Component side = game.getChosenSide() == CoinflipGame.CoinSide.HEADS ?
                    Component.text("Heads", NamedTextColor.AQUA) :
                    Component.text("Tails", NamedTextColor.LIGHT_PURPLE);
            lore.add(Component.text("Side: ", NamedTextColor.GRAY).append(side).decoration(TextDecoration.ITALIC, false));

            lore.add(Component.empty()); // Empty line
            lore.add(Component.text("► Click to Challenge!", NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

            meta.lore(lore);
            meta.getPersistentDataContainer().set(gameIdKey, PersistentDataType.LONG, game.getGameId());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            head.setItemMeta(meta);
        }
        return head;
    }

    private ItemStack createStatsItem(Player player, CoinflipStats stats) {
        ItemStack paper = new ItemStack(Material.PAPER);
        ItemMeta meta = paper.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(player.getName() + "'s Stats", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Wins: ", NamedTextColor.GREEN).append(Component.text(stats.getWins(), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Losses: ", NamedTextColor.RED).append(Component.text(stats.getLosses(), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            paper.setItemMeta(meta);
        }
        return paper;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(GUI_MAIN_METADATA)) return;

        // [Req 5] & [Req 6] Use Component comparison
        Component title = event.getView().title();
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
        if (!legacyTitle.startsWith(LegacyComponentSerializer.legacySection().serialize(Component.text("Coinflip Menu", NamedTextColor.DARK_GRAY)))) {
            return;
        }

        if (this.coinflipSystem == null) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int currentPage = player.getMetadata(GUI_MAIN_METADATA).getFirst().asInt();
        int slot = event.getRawSlot();

        // [Req 2] Prevent double clicks on actions
        if (playersChallenging.contains(player.getUniqueId()) || playersCreating.contains(player.getUniqueId())) {
            return;
        }

        if (slot >= 27) { // Bottom row controls
            Material type = clickedItem.getType();
            if (type == Material.ARROW) {
                if (slot == 27 && currentPage > 0) openMainMenu(player, currentPage - 1);
                else if (slot == 35) openMainMenu(player, currentPage + 1);
            } else if (type == Material.CLOCK && slot == 31) {
                msg.send(player, "&aRefreshing list...");
                getPendingGames(true).thenRun(() ->
                        Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player, currentPage))
                );
            } else if (type == Material.OAK_SIGN && slot == 29) {
                playersCreating.add(player.getUniqueId()); // [Req 2] Add lock
                player.closeInventory();
                openSideSignInput(player);
            }
        } else { // Clicked on a game
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(gameIdKey, PersistentDataType.LONG)) {

                Long gameIdLong = meta.getPersistentDataContainer().get(gameIdKey, PersistentDataType.LONG);
                if (gameIdLong == null) return;
                long gameId = gameIdLong;

                CoinflipGame gameToJoin = pendingGamesCache.stream()
                        .filter(g -> g.getGameId() == gameId)
                        .findFirst()
                        .orElse(null);

                if (gameToJoin != null) {
                    playersChallenging.add(player.getUniqueId()); // [Req 2] Add lock
                    player.closeInventory();
                    coinflipSystem.startCoinflipGame(player, gameToJoin);
                } else {
                    msg.send(player, "&cThis coinflip is no longer available. Refreshing...");
                    getPendingGames(true).thenRun(() ->
                            Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player, currentPage))
                    );
                }
            }
        }
    }


    // --- SignGUI Methods ---
    private void openSideSignInput(Player player) {
        try {
            // [Req 6] Use Kyori Components for sign text
            Component line1 = Component.empty();
            Component line2 = Component.text("^^^^^^^^^^^^^^^");
            Component line3 = Component.text("Enter Side:", NamedTextColor.GRAY);
            Component line4 = Component.text("Heads", NamedTextColor.AQUA)
                    .append(Component.text(" or ", NamedTextColor.GRAY))
                    .append(Component.text("Tails", NamedTextColor.LIGHT_PURPLE))
                    .append(Component.text(" || ", NamedTextColor.GRAY))
                    .append(Component.text("C", NamedTextColor.RED))
                    .append(Component.text("ancel", NamedTextColor.GRAY));

            SignGUI.builder()
                    .setLines(
                            LegacyComponentSerializer.legacySection().serialize(line1),
                            LegacyComponentSerializer.legacySection().serialize(line2),
                            LegacyComponentSerializer.legacySection().serialize(line3),
                            LegacyComponentSerializer.legacySection().serialize(line4)
                    )
                    .setHandler((p, result) -> {
                        String input = result.getLine(0).trim();

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                msg.send(p, "&cCoinflip creation cancelled.");
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                return;
                            }

                            try {
                                CoinflipGame.CoinSide chosenSide = CoinflipGame.CoinSide.valueOf(input.toUpperCase());
                                openAmountSignInput(p, chosenSide); // Go to next step (keeps lock)
                            } catch (IllegalArgumentException e) {
                                msg.send(p, "&cInvalid side. Please enter 'Heads' or 'Tails'.");
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                            }
                        });
                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Side SignGUI", e);
            msg.send(player, "&cError opening input prompt.");
            playersCreating.remove(player.getUniqueId()); // [Req 2] Remove lock
        }
    }

    private void openAmountSignInput(Player player, CoinflipGame.CoinSide chosenSide) {
        try {
            Component line1 = Component.empty();
            Component line2 = Component.text("^^^^^^^^^^^^^^^");
            Component line3 = Component.text("Enter Amount (k/m ok)", NamedTextColor.GRAY);
            Component line4 = Component.text("Side: " + chosenSide.name(), NamedTextColor.GRAY)
                    .append(Component.text(" || ", NamedTextColor.GRAY))
                    .append(Component.text("C", NamedTextColor.RED))
                    .append(Component.text("ancel", NamedTextColor.GRAY));

            SignGUI.builder()
                    .setLines(
                            LegacyComponentSerializer.legacySection().serialize(line1),
                            LegacyComponentSerializer.legacySection().serialize(line2),
                            LegacyComponentSerializer.legacySection().serialize(line3),
                            LegacyComponentSerializer.legacySection().serialize(line4)
                    )
                    .setHandler((p, result) -> {
                        String input = result.getLine(0).trim();

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                msg.send(p, "&cCoinflip creation cancelled.");
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                return;
                            }

                            double amount;
                            try {
                                amount = parseDoubleWithSuffix(input);
                                if (amount <= 0) {
                                    msg.send(p, "&cAmount must be positive.");
                                    playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                    return;
                                }
                            } catch (NumberFormatException e) {
                                if (e.getMessage() != null && e.getMessage().contains("Decimals")) {
                                    msg.send(p, "&cDecimal amounts are not allowed.");
                                } else {
                                    msg.send(p, "&c'" + input + "' is not a valid amount. Use whole numbers (e.g., 100, 2k, 1m).");
                                }
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                return;
                            }

                            if (!economy.has(p, amount)) {
                                msg.send(p, "&cYou do not have enough money (" + economy.format(amount) + ") to create this coinflip.");
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                return;
                            }

                            EconomyResponse withdrawResp = economy.withdrawPlayer(p, amount);
                            if (!withdrawResp.transactionSuccess()) {
                                msg.send(p, "&cFailed to withdraw funds: " + withdrawResp.errorMessage);
                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock
                                return;
                            }

                            database.createCoinflip(p.getUniqueId(), p.getName(), chosenSide, amount)
                                    .whenCompleteAsync((gameId, error) -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            try {
                                                if (error != null) {
                                                    msg.send(p, "&cAn error occurred creating the coinflip. Your money has been refunded.");
                                                    plugin.getLogger().log(Level.SEVERE, "Failed to save coinflip for " + p.getName(), error);
                                                    economy.depositPlayer(p, amount); // Refund
                                                } else {
                                                    msg.send(p, "&aCoinflip created for " + economy.format(amount) + " on " + chosenSide.name().toLowerCase() + "!");

                                                    // [Req 4] & [Req 9] Broadcast and Log
                                                    String broadcastMsg = "<yellow>" + p.getName() + "</yellow> created a coinflip for <gold>" + economy.format(amount) + "</gold>!";
                                                    msg.broadcast(MiniMessage.miniMessage().deserialize(broadcastMsg));
                                                    logger.logGame(p.getName() + " created a coinflip (ID: " + gameId + ") for `" + economy.format(amount) + "` on `" + chosenSide.name() + "`");
                                                }
                                            } finally {
                                                playersCreating.remove(p.getUniqueId()); // [Req 2] Remove lock on completion
                                            }
                                        });
                                    });
                        });
                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Amount SignGUI", e);
            msg.send(player, "&cError opening input prompt.");
            playersCreating.remove(player.getUniqueId()); // [Req 2] Remove lock
        }
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

    // [Req 6]
    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            List<Component> loreList = new ArrayList<>();
            for (Component line : lore) {
                loreList.add(line.decoration(TextDecoration.ITALIC, false));
            }
            meta.lore(loreList);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}