package me.login.coinflip;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Manages the entire Coinflip system, including initialization,
 * components, listeners, and commands.
 */
public class CoinflipModule implements Listener {

    private final Login plugin;
    private final Economy vaultEconomy;

    // Coinflip Components
    private CoinflipDatabase coinflipDatabase;
    private CoinflipSystem coinflipSystem;
    private CoinflipMenu coinflipMenu;
    private CoinflipManageMenu coinflipManageMenu;
    private CoinflipAdminMenu coinflipAdminMenu;
    private CoinflipLogger coinflipLogger;
    private MessageManager coinflipMessageManager;

    public CoinflipModule(Login plugin) {
        this.plugin = plugin;
        this.vaultEconomy = plugin.getVaultEconomy();
        // Register this class's listeners (onPlayerQuit, onAnimationInventoryClick)
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Initializes the Coinflip database.
     * This should be called early in onEnable.
     *
     * @return true if successful, false if connection failed.
     */
    public boolean initDatabase() {
        this.coinflipDatabase = new CoinflipDatabase(plugin);
        this.coinflipDatabase.connect();
        if (this.coinflipDatabase.getConnection() == null) {
            return false;
        }
        return true;
    }

    /**
     * Initializes all logic, listeners, and commands for the Coinflip system.
     * This is designed to be called *after* the JDA bots are confirmed ready.
     */
    public void initLogicAndListeners() {
        plugin.getLogger().info("LagClear Logger JDA is now connected. Initializing Coinflip system...");

        // 1. Initialize all components
        // [Req 3] Pass database to MessageManager
        this.coinflipMessageManager = new MessageManager(plugin, coinflipDatabase);
        this.coinflipLogger = new CoinflipLogger(plugin);

        // [Req 4] CoinflipMenu no longer needs database (for cache)
        this.coinflipMenu = new CoinflipMenu(plugin, coinflipDatabase, vaultEconomy, coinflipMessageManager, coinflipLogger);
        // [Req 4] CoinflipSystem now needs database (for cache)
        this.coinflipSystem = new CoinflipSystem(plugin, coinflipDatabase, vaultEconomy, coinflipLogger, coinflipMessageManager, coinflipMenu.getPlayersChallengingSet());
        this.coinflipMenu.setCoinflipSystem(coinflipSystem);
        // [FIX] Added coinflipSystem to constructor
        this.coinflipManageMenu = new CoinflipManageMenu(plugin, coinflipDatabase, vaultEconomy, coinflipMessageManager, coinflipLogger, coinflipSystem);
        this.coinflipAdminMenu = new CoinflipAdminMenu(plugin, coinflipDatabase, coinflipSystem, vaultEconomy, coinflipMessageManager, coinflipLogger);

        // 2. Register Listeners
        if (plugin.getServer().getPluginManager().getPlugin("Citizens") != null) {
            plugin.getServer().getPluginManager().registerEvents(new CoinflipNpcListener(plugin, coinflipMenu), plugin);
            plugin.getLogger().info("Citizens found, Coinflip NPC listener registered.");
        } else {
            plugin.getLogger().warning("Citizens plugin not found! Coinflip NPC click will not work.");
        }
        plugin.getServer().getPluginManager().registerEvents(coinflipMenu, plugin);
        plugin.getServer().getPluginManager().registerEvents(coinflipManageMenu, plugin);
        plugin.getServer().getPluginManager().registerEvents(coinflipAdminMenu, plugin);

        // 3. Register Command
        CoinflipCmd coinflipCmd = new CoinflipCmd(plugin, coinflipDatabase, vaultEconomy, coinflipMenu, coinflipManageMenu, coinflipAdminMenu, coinflipSystem, coinflipMessageManager, coinflipLogger);
        plugin.getCommand("coinflip").setExecutor(coinflipCmd);
        plugin.getCommand("coinflip").setTabCompleter(coinflipCmd);

        plugin.getLogger().info("Coinflip system enabled.");
    }

    // --- Event Handlers (Moved from Login.java) ---

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Handle Coinflip logic on quit
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // This check ensures the module has been initialized.
        if (coinflipDatabase == null || vaultEconomy == null || coinflipSystem == null) {
            return;
        }

        // [Req 6] Remove all metadata on quit to prevent "stuck" GUIs
        if (player.hasMetadata(CoinflipMenu.GUI_MAIN_METADATA)) {
            player.removeMetadata(CoinflipMenu.GUI_MAIN_METADATA, plugin);
        }
        if (player.hasMetadata(CoinflipManageMenu.GUI_MANAGE_METADATA)) {
            player.removeMetadata(CoinflipManageMenu.GUI_MANAGE_METADATA, plugin);
        }
        if (player.hasMetadata(CoinflipAdminMenu.GUI_ADMIN_METADATA)) {
            player.removeMetadata(CoinflipAdminMenu.GUI_ADMIN_METADATA, plugin);
        }

        // [Req 7] This logic handles refunding pending coinflips on quit. It seems correct.
        coinflipDatabase.loadPlayerPendingCoinflips(playerUUID).whenCompleteAsync((games, error) -> {
            if (error != null || games == null || games.isEmpty()) {
                return;
            }

            double totalRefund = games.stream().mapToDouble(CoinflipGame::getAmount).sum();
            List<Long> gameIds = games.stream()
                    .map(CoinflipGame::getGameId)
                    .collect(Collectors.toList());

            if (totalRefund <= 0) {
                return;
            }

            // [Req 4] Also remove them from the cache
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (Long gameId : gameIds) {
                    coinflipSystem.getPendingGames(false).join().removeIf(g -> g.getGameId() == gameId);
                }
            });

            CompletableFuture<?>[] removalFutures = gameIds.stream()
                    .map(coinflipDatabase::removeCoinflip)
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(removalFutures).whenComplete((v, removalError) -> {
                if (removalError != null) {
                    plugin.getLogger().severe("Error removing coinflips for " + player.getName() + " on quit: " + removalError.getMessage());
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    EconomyResponse refundResp = vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(playerUUID), totalRefund);
                    if (refundResp.transactionSuccess()) {
                        plugin.getLogger().info("Refunded " + player.getName() + " " + vaultEconomy.format(totalRefund) + " for " + gameIds.size() + " cancelled coinflips on quit.");
                    } else {
                        plugin.getLogger().severe("CRITICAL: Failed to refund " + player.getName() + " " + vaultEconomy.format(totalRefund) + " on quit. Error: " + refundResp.errorMessage);
                    }
                });
            });
        });
    }

    @EventHandler
    public void onAnimationInventoryClick(InventoryClickEvent event) {
        // This check ensures the module has been initialized.
        if (coinflipSystem == null) {
            return;
        }
        Component title = event.getView().title();
        if (title.equals(CoinflipSystem.ANIMATION_TITLE)) {
            event.setCancelled(true);
        }
    }

    // --- Public Getters ---

    public CoinflipDatabase getDatabase() {
        return coinflipDatabase;
    }

    public CoinflipSystem getCoinflipSystem() {
        return coinflipSystem;
    }

    public CoinflipMenu getCoinflipMenu() {
        return coinflipMenu;
    }

    public CoinflipManageMenu getCoinflipManageMenu() {
        return coinflipManageMenu;
    }

    public CoinflipAdminMenu getCoinflipAdminMenu() {
        return coinflipAdminMenu;
    }

    public CoinflipLogger getCoinflipLogger() {
        return coinflipLogger;
    }

    public MessageManager getCoinflipMessageManager() {
        return coinflipMessageManager;
    }
}