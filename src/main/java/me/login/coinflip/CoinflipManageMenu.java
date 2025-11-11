package me.login.coinflip;

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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // [Req 6] IMPORT ADDED
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

public class CoinflipManageMenu implements Listener {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final Economy economy;
    private final MessageManager msg; // [Req 1]
    private final CoinflipLogger logger; // [Req 9]
    private final CoinflipSystem coinflipSystem; // [Req 4]

    private static final int GUI_SIZE = 54;
    private static final int GAMES_PER_PAGE = 45;
    private final NamespacedKey gameIdKey;
    private final NamespacedKey gameAmountKey;
    private final Set<UUID> playersCancelling = new HashSet<>(); // [Req 2]
    public static final String GUI_MANAGE_METADATA = "CoinflipManageMenu";


    public CoinflipManageMenu(Login plugin, CoinflipDatabase database, Economy economy, MessageManager msg, CoinflipLogger logger, CoinflipSystem coinflipSystem) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.msg = msg; // [Req 1]
        this.logger = logger; // [Req 9]
        this.coinflipSystem = coinflipSystem; // [Req 4]
        this.gameIdKey = new NamespacedKey(plugin, "cf_manage_game_id");
        this.gameAmountKey = new NamespacedKey(plugin, "cf_manage_game_amount");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openManageMenu(Player player, int page) {
        database.loadPlayerPendingCoinflips(player.getUniqueId()).whenCompleteAsync((games, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error loading player coinflips for " + player.getName(), error);
                Bukkit.getScheduler().runTask(plugin, () -> msg.send(player, "&cError loading your coinflips."));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (games.isEmpty()) {
                    msg.send(player, "&eYou have no pending coinflips.");
                    return;
                }

                int totalGames = games.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalGames / GAMES_PER_PAGE));
                int finalPage = Math.max(0, Math.min(page, totalPages - 1));

                // [Req 5] & [Req 6] Use Component title
                Component title = Component.text("Manage Coinflips (Page " + (finalPage + 1) + "/" + totalPages + ")", NamedTextColor.DARK_GRAY);
                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, LegacyComponentSerializer.legacySection().serialize(title));

                int startIndex = finalPage * GAMES_PER_PAGE;
                int endIndex = Math.min(startIndex + GAMES_PER_PAGE, totalGames);
                List<CoinflipGame> pageGames = games.subList(startIndex, endIndex);
                for (int i = 0; i < pageGames.size(); i++) {
                    gui.setItem(i, createManageDisplayItem(pageGames.get(i)));
                }

                ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
                for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

                if (finalPage > 0) gui.setItem(45, createGuiItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW)));
                gui.setItem(48, createGuiItem(Material.BARRIER, Component.text("Close", NamedTextColor.RED)));
                gui.setItem(50, createGuiItem(Material.CLOCK, Component.text("Refresh List", NamedTextColor.AQUA)));
                if (finalPage < totalPages - 1) gui.setItem(53, createGuiItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW)));

                player.setMetadata(GUI_MANAGE_METADATA, new FixedMetadataValue(plugin, finalPage));
                player.openInventory(gui);
            });

        }, runnable -> Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    // [Req 6]
    private ItemStack createManageDisplayItem(CoinflipGame game) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player owner = Bukkit.getPlayer(game.getCreatorUUID());
            if (owner != null) meta.setOwningPlayer(owner);
            else meta.setOwner(game.getCreatorName());

            // --- FIX: Removed bold ---
            meta.displayName(Component.text("Your Coinflip (ID: " + game.getGameId() + ")", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false)); // [Req 4] Show ID

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Amount: ", NamedTextColor.GRAY).append(Component.text(economy.format(game.getAmount()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));

            Component side = game.getChosenSide() == CoinflipGame.CoinSide.HEADS ?
                    Component.text("Heads", NamedTextColor.AQUA) :
                    Component.text("Tails", NamedTextColor.LIGHT_PURPLE);
            lore.add(Component.text("Side: ", NamedTextColor.GRAY).append(side).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));

            lore.add(Component.text("Created: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(game.getCreationTime())), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            lore.add(Component.empty());
            lore.add(Component.text("▶ Shift+Click to Cancel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            // --- END FIX ---

            meta.lore(lore);
            meta.getPersistentDataContainer().set(gameIdKey, PersistentDataType.LONG, game.getGameId());
            meta.getPersistentDataContainer().set(gameAmountKey, PersistentDataType.DOUBLE, game.getAmount());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            head.setItemMeta(meta);
        }
        return head;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();

        // --- GUI BUG FIX: Logic Re-ordered ---
        // 1. Check if this is the GUI we care about
        Component title = event.getView().title();
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
        if (!legacyTitle.startsWith(LegacyComponentSerializer.legacySection().serialize(Component.text("Manage Coinflips", NamedTextColor.DARK_GRAY)))) {
            return; // Not our inventory, ignore
        }

        // 2. ALWAYS CANCEL FIRST! This stops item stealing.
        event.setCancelled(true);

        // 3. Now check metadata to see if we should process the click
        if (!player.hasMetadata(GUI_MANAGE_METADATA)) {
            return; // It's our GUI, but player has no metadata, so don't do any actions
        }
        // --- END FIX ---

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // [Req 2] Prevent double clicks
        if (playersCancelling.contains(player.getUniqueId())) {
            return;
        }

        int currentPage = player.getMetadata(GUI_MANAGE_METADATA).getFirst().asInt();
        int slot = event.getRawSlot();
        ClickType clickType = event.getClick();

        if (slot >= 45) { // Bottom row controls
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) player.closeInventory();
            else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) openManageMenu(player, currentPage - 1);
                else if (slot == 53) {
                    database.loadPlayerPendingCoinflips(player.getUniqueId()).whenCompleteAsync((games, err) -> {
                        if (err == null) {
                            int totalPages = Math.max(1, (int) Math.ceil((double) games.size() / GAMES_PER_PAGE));
                            if (currentPage < totalPages - 1) {
                                Bukkit.getScheduler().runTask(plugin, () -> openManageMenu(player, currentPage + 1));
                            }
                        }
                    });
                }
            } else if (type == Material.CLOCK && slot == 50) {
                msg.send(player, "&aRefreshing list...");
                openManageMenu(player, currentPage);
            }
        } else {
            if (clickType.isShiftClick()) {
                // --- FIX: Check lock *before* scheduling task ---
                if (playersCancelling.contains(player.getUniqueId())) {
                    return;
                }

                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(gameIdKey, PersistentDataType.LONG)) {
                    long gameId = meta.getPersistentDataContainer().get(gameIdKey, PersistentDataType.LONG);

                    if (!meta.getPersistentDataContainer().has(gameAmountKey, PersistentDataType.DOUBLE)) {
                        msg.send(player, "&cError: Could not read amount from item. Please refresh.");
                        return;
                    }
                    double amount = meta.getPersistentDataContainer().get(gameAmountKey, PersistentDataType.DOUBLE);

                    // --- FIX: Add 1-tick delay ---
                    // Add lock *before* the delay to prevent double clicks
                    playersCancelling.add(player.getUniqueId());
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        // The handler will be responsible for removing the lock
                        handleCancelCoinflip(player, gameId, amount, currentPage);
                    }, 1L);
                    // --- END FIX ---
                }
            }
        }
    }

    // [Req 6] ADDED: InventoryCloseEvent to remove metadata
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.hasMetadata(GUI_MANAGE_METADATA)) {
            player.removeMetadata(GUI_MANAGE_METADATA, plugin);
        }
    }

    private void handleCancelCoinflip(Player player, long gameId, double amount, int currentPage) {
        // --- FIX: Lock is now added in the click event *before* the delay ---
        // playersCancelling.add(player.getUniqueId()); // [Req 2] Add lock

        database.removeCoinflip(gameId).whenCompleteAsync((success, error) -> {
            UUID playerUUID = player.getUniqueId();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                try {
                    if (error != null) {
                        msg.send(player, "&cAn error occurred cancelling the coinflip.");
                        plugin.getLogger().log(Level.SEVERE, "Failed to remove coinflip " + gameId + " for " + player.getName(), error);
                        return;
                    }

                    if (success) {
                        // --- SERVER FREEZE FIX ---
                        // [Req 4] Also remove from cache, using the new safe method
                        coinflipSystem.removePendingGameFromCache(gameId);
                        // --- END FIX ---

                        EconomyResponse refundResp = economy.depositPlayer(player, amount);
                        if (refundResp.transactionSuccess()) {
                            msg.send(player, "&aCoinflip cancelled and " + economy.format(amount) + " refunded.");
                            // [Req 9]
                            logger.logGame(player.getName() + " cancelled coinflip ID " + gameId + " (Refunded " + economy.format(amount) + ")");
                        } else {
                            msg.send(player, "&cCoinflip cancelled, but refund failed: " + refundResp.errorMessage);
                            plugin.getLogger().severe("CRITICAL: Failed refund for cancelled coinflip " + gameId + " for player " + player.getName());
                            logger.logGame(player.getName() + " cancelled coinflip ID " + gameId + " (REFUND FAILED)");
                        }
                    } else {
                        msg.send(player, "&cCould not cancel coinflip (it might have been accepted already).");
                    }
                } finally {
                    playersCancelling.remove(playerUUID); // [Req 2] Remove lock
                    // Refresh menu only if the player is still online and has the menu open
                    if (player.isOnline() && player.hasMetadata(GUI_MANAGE_METADATA)) {
                        openManageMenu(player, currentPage); // Refresh menu
                    }
                }
            });
        });
    }

    // [Req 6]
    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // --- FIX: Removed bold ---
            meta.displayName(name.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            List<Component> loreList = new ArrayList<>();
            for (Component line : lore) {
                loreList.add(line.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            }
            // --- END FIX ---
            meta.lore(loreList);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}