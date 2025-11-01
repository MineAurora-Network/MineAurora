package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor; // Keep for legacy title check
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
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

    private static final int GUI_SIZE = 54;
    private static final int GAMES_PER_PAGE = 45;
    private final NamespacedKey gameIdKey;
    private final NamespacedKey gameAmountKey;
    private final Set<UUID> playersCancelling = new HashSet<>();
    public static final String GUI_MANAGE_METADATA = "CoinflipManageMenu";


    public CoinflipManageMenu(Login plugin, CoinflipDatabase database, Economy economy) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.gameIdKey = new NamespacedKey(plugin, "cf_manage_game_id");
        this.gameAmountKey = new NamespacedKey(plugin, "cf_manage_game_amount");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void openManageMenu(Player player, int page) {
        database.loadPlayerPendingCoinflips(player.getUniqueId()).whenCompleteAsync((games, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error loading player coinflips for " + player.getName(), error);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(plugin.formatMessage("&cError loading your coinflips.")));
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                if (games.isEmpty()) {
                    player.sendMessage(plugin.formatMessage("&eYou have no pending coinflips."));
                    return;
                }

                int totalGames = games.size();
                int totalPages = Math.max(1, (int) Math.ceil((double) totalGames / GAMES_PER_PAGE));
                int finalPage = Math.max(0, Math.min(page, totalPages - 1));

                // --- FIX: Changed title color ---
                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ChatColor.DARK_GRAY + "Manage Coinflips (Page " + (finalPage + 1) + "/" + totalPages + ")");
                // --- END FIX ---

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

    private ItemStack createManageDisplayItem(CoinflipGame game) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player owner = Bukkit.getPlayer(game.getCreatorUUID());
            if (owner != null) meta.setOwningPlayer(owner);
            else meta.setOwner(game.getCreatorName());

            meta.displayName(Component.text("Your Coinflip", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

            List<Component> lore = new ArrayList<>();
            lore.add(Component.text("Amount: ", NamedTextColor.GRAY).append(Component.text(economy.format(game.getAmount()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));

            Component side = game.getChosenSide() == CoinflipGame.CoinSide.HEADS ?
                    Component.text("Heads", NamedTextColor.AQUA) :
                    Component.text("Tails", NamedTextColor.LIGHT_PURPLE);
            lore.add(Component.text("Side: ", NamedTextColor.GRAY).append(side).decoration(TextDecoration.ITALIC, false));

            lore.add(Component.text("Created: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(game.getCreationTime())), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("▶ Shift+Click to Cancel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));

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
        if (!player.hasMetadata(GUI_MANAGE_METADATA)) return;
        // --- FIX: Check for correct title color ---
        if (!event.getView().getTitle().startsWith(ChatColor.DARK_GRAY + "Manage Coinflips")) return;
        // --- END FIX ---

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

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
                player.sendMessage(plugin.formatMessage("&aRefreshing list..."));
                openManageMenu(player, currentPage);
            }
        } else {
            if (clickType.isShiftClick()) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(gameIdKey, PersistentDataType.LONG)) {
                    long gameId = meta.getPersistentDataContainer().get(gameIdKey, PersistentDataType.LONG);

                    if (!meta.getPersistentDataContainer().has(gameAmountKey, PersistentDataType.DOUBLE)) {
                        player.sendMessage(plugin.formatMessage("&cError: Could not read amount from item. Please refresh."));
                        return;
                    }
                    double amount = meta.getPersistentDataContainer().get(gameAmountKey, PersistentDataType.DOUBLE);

                    handleCancelCoinflip(player, gameId, amount, currentPage);
                }
            }
        }
    }

    private void handleCancelCoinflip(Player player, long gameId, double amount, int currentPage) {
        if (playersCancelling.contains(player.getUniqueId())) {
            player.sendMessage(plugin.formatMessage("&ePlease wait, cancellation in progress..."));
            return;
        }
        playersCancelling.add(player.getUniqueId());

        database.removeCoinflip(gameId).whenCompleteAsync((success, error) -> {
            UUID playerUUID = player.getUniqueId();

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                playersCancelling.remove(playerUUID);

                if (error != null) {
                    player.sendMessage(plugin.formatMessage("&cAn error occurred cancelling the coinflip."));
                    plugin.getLogger().log(Level.SEVERE, "Failed to remove coinflip " + gameId + " for " + player.getName(), error);
                    return;
                }

                if (success) {
                    EconomyResponse refundResp = economy.depositPlayer(player, amount);
                    if (refundResp.transactionSuccess()) {
                        player.sendMessage(plugin.formatMessage("&aCoinflip cancelled and " + economy.format(amount) + " refunded."));
                        plugin.sendCoinflipLog(player.getName() + " cancelled coinflip ID " + gameId + " (Refunded " + economy.format(amount) + ")");
                    } else {
                        player.sendMessage(plugin.formatMessage("&cCoinflip cancelled, but refund failed: " + refundResp.errorMessage));
                        plugin.getLogger().severe("CRITICAL: Failed refund for cancelled coinflip " + gameId + " for player " + player.getName());
                        plugin.sendCoinflipLog(player.getName() + " cancelled coinflip ID " + gameId + " (REFUND FAILED)");
                    }
                } else {
                    player.sendMessage(plugin.formatMessage("&cCould not cancel coinflip (it might have been accepted already)."));
                }

                openManageMenu(player, currentPage);
            });
        });
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