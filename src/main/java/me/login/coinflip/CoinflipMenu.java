package me.login.coinflip;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Keep for SignGUI & legacy title checks
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CoinflipMenu implements Listener {

    private final Login plugin;
    private final CoinflipDatabase database;
    private CoinflipSystem coinflipSystem;
    private final Economy economy;

    // --- FIX: Made fields final as suggested by IDE ---
    private List<CoinflipGame> pendingGamesCache = Collections.synchronizedList(new ArrayList<>());
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 10 * 1000;
    private boolean isRefreshingCache = false;
    // --- END FIX ---

    private static final int GUI_SIZE = 36;
    private static final int GAMES_PER_PAGE = 27;
    private final NamespacedKey gameIdKey;
    public static final String GUI_MAIN_METADATA = "CoinflipMainMenu";

    private final Set<UUID> playersChallenging = new HashSet<>();
    private final Set<UUID> playersCreating = new HashSet<>();


    public CoinflipMenu(Login plugin, CoinflipDatabase database, Economy economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
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

    // --- FIX: ADDED MISSING METHOD ---
    private CompletableFuture<List<CoinflipGame>> getPendingGames(boolean force) {
        long now = System.currentTimeMillis();
        // Check 'isRefreshingCache' first to prevent multiple refreshes
        if (isRefreshingCache) {
            return CompletableFuture.completedFuture(new ArrayList<>(pendingGamesCache));
        }
        if (!force && (now - lastCacheUpdateTime < CACHE_DURATION_MS)) {
            return CompletableFuture.completedFuture(new ArrayList<>(pendingGamesCache)); // Return copy
        }

        isRefreshingCache = true;
        // plugin.getLogger().info("Refreshing coinflip games cache..."); // Optional: for debugging
        return database.loadPendingCoinflips().whenComplete((games, error) -> {
            if (games != null) {
                this.pendingGamesCache = Collections.synchronizedList(games); // Update cache
                this.lastCacheUpdateTime = System.currentTimeMillis();
                // plugin.getLogger().info("Coinflip cache updated with " + games.size() + " games."); // Optional: for debugging
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refresh coinflip games cache", error);
            }
            isRefreshingCache = false;
        });
    }
    // --- END FIX ---

    public void openMainMenu(Player player, int page) {
        CompletableFuture<List<CoinflipGame>> gamesFuture = getPendingGames(false); // This now works
        CompletableFuture<CoinflipStats> statsFuture = database.loadPlayerStats(player.getUniqueId());

        CompletableFuture.allOf(gamesFuture, statsFuture).whenCompleteAsync((v, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error loading data for coinflip menu", error);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(plugin.formatMessage("&cError loading coinflip menu.")));
                return;
            }

            List<CoinflipGame> games = gamesFuture.join();
            CoinflipStats stats = statsFuture.join();

            Bukkit.getScheduler().runTask(plugin, () -> {
                int totalGames = games.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalGames / GAMES_PER_PAGE));
                int finalPage = Math.max(0, Math.min(page, totalPages - 1));

                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_GRAY + "Coinflip Menu (Page " + (finalPage + 1) + "/" + totalPages + ")");

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

            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false)); // Empty line without italics
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
        if (!event.getView().getTitle().startsWith(ChatColor.DARK_GRAY + "Coinflip Menu")) return;

        if (this.coinflipSystem == null) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int currentPage = player.getMetadata(GUI_MAIN_METADATA).getFirst().asInt();
        int slot = event.getRawSlot();

        if (slot >= 27) { // Bottom row controls
            Material type = clickedItem.getType();
            if (type == Material.ARROW) {
                if (slot == 27 && currentPage > 0) openMainMenu(player, currentPage - 1);
                else if (slot == 35) openMainMenu(player, currentPage + 1);
            } else if (type == Material.CLOCK && slot == 31) {
                player.sendMessage(plugin.formatMessage("&aRefreshing list..."));
                getPendingGames(true).thenRun(() -> // This now works
                        Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player, currentPage))
                );
            } else if (type == Material.OAK_SIGN && slot == 29) {
                if (playersCreating.contains(player.getUniqueId())) {
                    plugin.getLogger().info("[CF-DEBUG] Player " + player.getName() + " is already creating a coinflip. Ignoring click.");
                    return;
                }
                playersCreating.add(player.getUniqueId());

                plugin.getLogger().info("[CF-DEBUG] Player " + player.getName() + " clicked create coinflip sign.");
                player.closeInventory();
                openSideSignInput(player);
            }
        } else { // Clicked on a game
            ItemMeta meta = clickedItem.getItemMeta();
            if (meta != null && meta.getPersistentDataContainer().has(gameIdKey, PersistentDataType.LONG)) {

                if (playersChallenging.contains(player.getUniqueId())) {
                    player.sendMessage(plugin.formatMessage("&ePlease wait..."));
                    return;
                }

                // --- FIX: Add null check for NPE warning ---
                Long gameIdLong = meta.getPersistentDataContainer().get(gameIdKey, PersistentDataType.LONG);
                if (gameIdLong == null) return; // Should not happen, but good practice
                long gameId = gameIdLong;
                // --- END FIX ---

                CoinflipGame gameToJoin = pendingGamesCache.stream()
                        .filter(g -> g.getGameId() == gameId)
                        .findFirst()
                        .orElse(null);

                if (gameToJoin != null) {
                    playersChallenging.add(player.getUniqueId());
                    player.closeInventory();
                    coinflipSystem.startCoinflipGame(player, gameToJoin);
                } else {
                    player.sendMessage(plugin.formatMessage("&cThis coinflip is no longer available. Refreshing..."));
                    getPendingGames(true).thenRun(() -> // This now works
                            Bukkit.getScheduler().runTask(plugin, () -> openMainMenu(player, currentPage))
                    );
                }
            }
        }
    }


    // --- SignGUI Methods (FIXED LOGIC) ---
    private void openSideSignInput(Player player) {
        plugin.getLogger().info("[CF-DEBUG] Opening SideSignInput for " + player.getName());
        try {
            String[] lines = {
                    "", // Line 0
                    "^^^^^^^^^^^^^^^", // Line 1
                    ChatColor.GRAY + "Enter Side:", // Line 2
                    ChatColor.AQUA + "Heads" + ChatColor.GRAY + " or " + ChatColor.LIGHT_PURPLE + "Tails" + ChatColor.GRAY + " || " + ChatColor.RED + "C" + ChatColor.GRAY + "ancel" // Line 3
            };

            SignGUI.builder()
                    .setLines(lines)
                    .setHandler((p, result) -> {
                        String input = result.getLine(0).trim();
                        plugin.getLogger().info("[CF-DEBUG] SideSign Handler. Raw input: '" + input + "'");

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getLogger().info("[CF-DEBUG] SideSign Task RUN. Input: '" + input + "'");
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                p.sendMessage(plugin.formatMessage("&cCoinflip creation cancelled."));
                                playersCreating.remove(p.getUniqueId());
                                return;
                            }

                            try {
                                CoinflipGame.CoinSide chosenSide = CoinflipGame.CoinSide.valueOf(input.toUpperCase());
                                plugin.getLogger().info("[CF-DEBUG] SideSign Success. Opening Amount sign.");
                                openAmountSignInput(p, chosenSide); // Go to next step
                            } catch (IllegalArgumentException e) {
                                p.sendMessage(plugin.formatMessage("&cInvalid side. Please enter 'Heads' or 'Tails'."));
                                plugin.getLogger().info("[CF-DEBUG] SideSign Invalid. User must try again.");
                                playersCreating.remove(p.getUniqueId()); // Let them try again
                            }
                        });
                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Side SignGUI", e);
            player.sendMessage(plugin.formatMessage("&cError opening input prompt."));
            playersCreating.remove(player.getUniqueId());
        }
    }

    private void openAmountSignInput(Player player, CoinflipGame.CoinSide chosenSide) {
        plugin.getLogger().info("[CF-DEBUG] Opening AmountSignInput for " + player.getName());
        try {
            String[] lines = {
                    "", // Line 0
                    "^^^^^^^^^^^^^^^", // Line 1
                    ChatColor.GRAY + "Enter Amount (k/m ok)", // Line 2
                    ChatColor.GRAY + "Side: " + chosenSide.name() + " || " + ChatColor.RED + "C" + ChatColor.GRAY + "ancel" // Line 3
            };

            SignGUI.builder()
                    .setLines(lines)
                    .setHandler((p, result) -> {
                        String input = result.getLine(0).trim();
                        plugin.getLogger().info("[CF-DEBUG] AmountSign Handler. Raw input: '" + input + "'");

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            plugin.getLogger().info("[CF-DEBUG] AmountSign Task RUN. Input: '" + input + "'");
                            if (input.isEmpty() || input.equalsIgnoreCase("C")) {
                                p.sendMessage(plugin.formatMessage("&cCoinflip creation cancelled."));
                                playersCreating.remove(p.getUniqueId());
                            }

                            double amount;
                            try {
                                amount = parseDoubleWithSuffix(input);
                                if (amount <= 0) {
                                    p.sendMessage(plugin.formatMessage("&cAmount must be positive."));
                                    plugin.getLogger().info("[CF-DEBUG] AmountSign Invalid: Not positive.");
                                    playersCreating.remove(p.getUniqueId());
                                    return;
                                }
                            } catch (NumberFormatException e) {
                                if (e.getMessage() != null && e.getMessage().contains("Decimals")) {
                                    p.sendMessage(plugin.formatMessage("&cDecimal amounts are not allowed."));
                                } else {
                                    p.sendMessage(plugin.formatMessage("&c'" + input + "' is not a valid amount. Use whole numbers (e.g., 100, 2k, 1m)."));
                                }
                                plugin.getLogger().info("[CF-DEBUG] AmountSign Invalid: NumberFormat.");
                                playersCreating.remove(p.getUniqueId());
                                return;
                            }

                            // Final Creation Logic
                            plugin.getLogger().info("[CF-DEBUG] AmountSign Success. Processing creation...");
                            if (!economy.has(p, amount)) {
                                p.sendMessage(plugin.formatMessage("&cYou do not have enough money (" + economy.format(amount) + ") to create this coinflip."));
                                playersCreating.remove(p.getUniqueId());
                                return;
                            }

                            EconomyResponse withdrawResp = economy.withdrawPlayer(p, amount);
                            if (!withdrawResp.transactionSuccess()) {
                                p.sendMessage(plugin.formatMessage("&cFailed to withdraw funds: " + withdrawResp.errorMessage));
                                playersCreating.remove(p.getUniqueId());
                                return;
                            }

                            database.createCoinflip(p.getUniqueId(), p.getName(), chosenSide, amount)
                                    .whenCompleteAsync((gameId, error) -> {
                                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                                            if (error != null) {
                                                p.sendMessage(plugin.formatMessage("&cAn error occurred creating the coinflip. Your money has been refunded."));
                                                plugin.getLogger().log(Level.SEVERE, "Failed to save coinflip for " + p.getName(), error);
                                                economy.depositPlayer(p, amount); // Refund
                                            } else {
                                                p.sendMessage(plugin.formatMessage("&aCoinflip created for " + economy.format(amount) + " on " + chosenSide.name().toLowerCase() + "!"));
                                                plugin.sendCoinflipLog(p.getName() + " created a coinflip (ID: " + gameId + ") for `" + economy.format(amount) + "` on `" + chosenSide.name() + "`");
                                            }
                                            playersCreating.remove(p.getUniqueId()); // Remove on completion
                                        });
                                    });
                        });
                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening Amount SignGUI", e);
            player.sendMessage(plugin.formatMessage("&cError opening input prompt."));
            playersCreating.remove(player.getUniqueId());
        }
    }
    // --- END SignGUI Update ---

    private double parseDoubleWithSuffix(String input) throws NumberFormatException {
        input = input.trim().toLowerCase();
        // --- FIX: IDE warning "always false" is incorrect, user can input " " ---
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

    // Helper to create a GUI item with Adventure Components
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