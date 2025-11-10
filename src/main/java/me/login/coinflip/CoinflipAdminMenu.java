package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
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
import org.bukkit.scheduler.BukkitRunnable; // <-- IMPORT ADDED

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Level;

public class CoinflipAdminMenu implements Listener {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final CoinflipSystem coinflipSystem;
    private final Economy economy;
    private final MessageManager msg;
    private final CoinflipLogger logger;

    private static final int GUI_SIZE = 54;
    private static final int GAMES_PER_PAGE = 45;
    private final NamespacedKey gameIdKey;
    private final Set<UUID> playersCancelling = new HashSet<>(); // [Req 2]
    public static final String GUI_ADMIN_METADATA = "CoinflipAdminMenu";

    // Cache for all games
    private List<CoinflipGame> allGamesCache = Collections.synchronizedList(new ArrayList<>());
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 10 * 1000;
    private boolean isRefreshingCache = false;


    public CoinflipAdminMenu(Login plugin, CoinflipDatabase database, CoinflipSystem coinflipSystem, Economy economy, MessageManager msg, CoinflipLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.coinflipSystem = coinflipSystem;
        this.economy = economy;
        this.msg = msg;
        this.logger = logger;
        this.gameIdKey = new NamespacedKey(plugin, "cf_admin_game_id");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        refreshAllGamesCache(true); // Initial load
    }

    private List<CoinflipGame> refreshAllGamesCache(boolean force) {
        long now = System.currentTimeMillis();
        if (isRefreshingCache) {
            return new ArrayList<>(allGamesCache);
        }
        if (!force && (now - lastCacheUpdateTime < CACHE_DURATION_MS)) {
            return new ArrayList<>(allGamesCache);
        }

        isRefreshingCache = true;
        database.loadAllGames().whenComplete((games, error) -> {
            if (games != null) {
                this.allGamesCache = Collections.synchronizedList(games);
                this.lastCacheUpdateTime = System.currentTimeMillis();
            }
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Failed to refresh all coinflip games cache for admin menu", error);
            }
            isRefreshingCache = false;
        });
        // Return the current cache, it will be updated on next open
        return new ArrayList<>(allGamesCache);
    }

    public void openAdminMenu(Player player, int page) {
        List<CoinflipGame> games = refreshAllGamesCache(false);

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (games.isEmpty()) {
                msg.send(player, "&eNo active or pending coinflips found.");
                return;
            }

            int totalGames = games.size();
            int totalPages = Math.max(1, (int) Math.ceil((double) totalGames / GAMES_PER_PAGE));
            int finalPage = Math.max(0, Math.min(page, totalPages - 1));

            // [Req 5] & [Req 6] Use Component title
            Component title = Component.text("Coinflip Admin (Page " + (finalPage + 1) + "/" + totalPages + ")", NamedTextColor.DARK_GRAY);
            Inventory gui = Bukkit.createInventory(null, GUI_SIZE, LegacyComponentSerializer.legacySection().serialize(title));

            int startIndex = finalPage * GAMES_PER_PAGE;
            int endIndex = Math.min(startIndex + GAMES_PER_PAGE, totalGames);
            List<CoinflipGame> pageGames = games.subList(startIndex, endIndex);
            for (int i = 0; i < pageGames.size(); i++) {
                gui.setItem(i, createAdminDisplayItem(pageGames.get(i)));
            }

            ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
            for (int i = 45; i < 54; i++) gui.setItem(i, grayPane);

            if (finalPage > 0) gui.setItem(45, createGuiItem(Material.ARROW, Component.text("Previous Page", NamedTextColor.YELLOW)));
            gui.setItem(48, createGuiItem(Material.BARRIER, Component.text("Close", NamedTextColor.RED)));
            gui.setItem(50, createGuiItem(Material.CLOCK, Component.text("Refresh List", NamedTextColor.AQUA)));
            if (finalPage < totalPages - 1) gui.setItem(53, createGuiItem(Material.ARROW, Component.text("Next Page", NamedTextColor.YELLOW)));

            player.setMetadata(GUI_ADMIN_METADATA, new FixedMetadataValue(plugin, finalPage));
            player.openInventory(gui);
        });
    }

    // [Req 6]
    private ItemStack createAdminDisplayItem(CoinflipGame game) {
        boolean isActive = game.getStatus().equals("ACTIVE");
        Material mat = isActive ? Material.LIME_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            // --- FIX: Removed BOLD from status creation ---
            Component status = isActive ?
                    Component.text("ACTIVE", NamedTextColor.GREEN) :
                    Component.text("PENDING", NamedTextColor.YELLOW);

            // --- FIX: Added .decoration(TextDecoration.BOLD, false) ---
            meta.displayName(Component.text("Game ID: " + game.getGameId(), NamedTextColor.WHITE).append(Component.text(" (", NamedTextColor.GRAY)).append(status).append(Component.text(")", NamedTextColor.GRAY)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));

            List<Component> lore = new ArrayList<>();
            // --- FIX: Added .decoration(TextDecoration.BOLD, false) to all lore lines ---
            lore.add(Component.text("Creator: ", NamedTextColor.GRAY).append(Component.text(game.getCreatorName(), NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            lore.add(Component.text("Amount: ", NamedTextColor.GRAY).append(Component.text(economy.format(game.getAmount()), NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));

            if (isActive) {
                Player p2 = Bukkit.getPlayer(game.getChallengerUUID());
                String p2Name = (p2 != null) ? p2.getName() : game.getChallengerName(); // Fallback just in case
                if(p2Name == null) p2Name = "Loading...";
                lore.add(Component.text("Challenger: ", NamedTextColor.GRAY).append(Component.text(p2Name, NamedTextColor.WHITE)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            } else {
                lore.add(Component.text("Challenger: ", NamedTextColor.GRAY).append(Component.text("N/A", NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            }

            lore.add(Component.text("Created: " + new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(game.getCreationTime())), NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            lore.add(Component.empty());
            // --- FIX: Removed BOLD from creation, added .decoration(TextDecoration.BOLD, false) ---
            lore.add(Component.text("▶ Shift+Click to Cancel", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            lore.add(Component.text("(Refunds all parties)", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            // --- END FIX ---

            meta.lore(lore);
            meta.getPersistentDataContainer().set(gameIdKey, PersistentDataType.LONG, game.getGameId());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(GUI_ADMIN_METADATA)) return;

        // [Req 5] & [Req 6] Use Component comparison
        Component title = event.getView().title();
        String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
        if (!legacyTitle.startsWith(LegacyComponentSerializer.legacySection().serialize(Component.text("Coinflip Admin", NamedTextColor.DARK_GRAY)))) {
            return;
        }

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // [Req 2] Prevent double clicks
        if (playersCancelling.contains(player.getUniqueId())) {
            return;
        }

        int currentPage = player.getMetadata(GUI_ADMIN_METADATA).getFirst().asInt();
        int slot = event.getRawSlot();
        ClickType clickType = event.getClick();

        if (slot >= 45) { // Bottom row controls
            Material type = clickedItem.getType();
            if (type == Material.BARRIER && slot == 48) player.closeInventory();
            else if (type == Material.ARROW) {
                if (slot == 45 && currentPage > 0) openAdminMenu(player, currentPage - 1);
                else if (slot == 53) {
                    int totalPages = Math.max(1, (int) Math.ceil((double) allGamesCache.size() / GAMES_PER_PAGE));
                    if (currentPage < totalPages - 1) {
                        openAdminMenu(player, currentPage + 1);
                    }
                }
            } else if (type == Material.CLOCK && slot == 50) {
                msg.send(player, "&aRefreshing list...");
                refreshAllGamesCache(true);
                openAdminMenu(player, currentPage);
            }
        } else {
            if (clickType.isShiftClick()) {
                ItemMeta meta = clickedItem.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(gameIdKey, PersistentDataType.LONG)) {
                    long gameId = meta.getPersistentDataContainer().get(gameIdKey, PersistentDataType.LONG);

                    CoinflipGame game = allGamesCache.stream()
                            .filter(g -> g.getGameId() == gameId)
                            .findFirst()
                            .orElse(null);

                    if (game == null) {
                        msg.send(player, "&cCould not find that game (ID: " + gameId + "). It may have just finished.");
                        return;
                    }

                    // --- FIX: Add 1-tick delay ---
                    // Add lock *before* scheduling task
                    playersCancelling.add(player.getUniqueId());

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            // Find game again, just in case cache updated or something
                            CoinflipGame gameNow = allGamesCache.stream()
                                    .filter(g -> g.getGameId() == game.getGameId())
                                    .findFirst()
                                    .orElse(null);

                            if (gameNow == null) {
                                msg.send(player, "&cThat game just finished or was cancelled.");
                                playersCancelling.remove(player.getUniqueId()); // Remove lock
                                return;
                            }

                            // Call handler (which will remove lock after 10 ticks)
                            handleAdminCancel(player, gameNow, currentPage);
                        }
                    }.runTaskLater(plugin, 1L);
                    // --- END FIX ---
                }
            }
        }
    }

    // [Req 6] ADDED: InventoryCloseEvent to remove metadata
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.hasMetadata(GUI_ADMIN_METADATA)) {
            player.removeMetadata(GUI_ADMIN_METADATA, plugin);
        }
    }

    // [FIX] Removed 'static' keyword from the method definition
    private void handleAdminCancel(Player admin, CoinflipGame game, int currentPage) {
        // --- FIX: Lock is now added in onInventoryClick ---
        // playersCancelling.add(admin.getUniqueId()); // [Req 2] Add lock

        // This method handles all logic (stopping animation, refunding, db removal)
        coinflipSystem.adminCancelGame(game, admin.getName());

        msg.send(admin, "&aSuccessfully cancelled coinflip game ID " + game.getGameId() + " and refunded all parties.");

        // Refresh cache and menu after a short delay
        new BukkitRunnable() {
            @Override
            public void run() {
                playersCancelling.remove(admin.getUniqueId()); // [Req 2] Remove lock
                refreshAllGamesCache(true);

                // [Req 6] Check if player is still online and has menu open
                if (admin.isOnline() && admin.hasMetadata(GUI_ADMIN_METADATA)) {
                    openAdminMenu(admin, currentPage);
                }
            }
        }.runTaskLater(plugin, 10L); // 10 ticks to allow DB operations to complete
    }

    // [Req 6]
    private ItemStack createGuiItem(Material material, Component name, Component... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // --- FIX: Added .decoration(TextDecoration.BOLD, false) ---
            meta.displayName(name.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            List<Component> loreList = new ArrayList<>();
            for (Component line : lore) {
                // --- FIX: Added .decoration(TextDecoration.BOLD, false) ---
                loreList.add(line.decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, false));
            }
            meta.lore(loreList);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }
}