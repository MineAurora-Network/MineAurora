package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class CoinflipSystem {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final Economy economy;
    private final CoinflipLogger logger; // [Req 9]
    private final MessageManager msg; // [Req 1]

    private final Map<UUID, BukkitTask> activeAnimations = new ConcurrentHashMap<>();
    private final Random random = ThreadLocalRandom.current();
    private final Set<UUID> playersChallenging;

    // [Req 4] Cache logic moved from CoinflipMenu
    private List<CoinflipGame> pendingGamesCache = Collections.synchronizedList(new ArrayList<>());
    private long lastCacheUpdateTime = 0;
    private final long CACHE_DURATION_MS = 10 * 1000;
    private boolean isRefreshingCache = false;

    private static final int ANIMATION_GUI_SIZE = 27;
    private static final int ANIMATION_DURATION_TICKS = 5 * 20;
    private static final int ANIMATION_SLIDE_SPEED_TICKS = 4;

    // [Req 5] & [Req 6]
    public static final Component ANIMATION_TITLE = Component.text("Coin Flipping...", NamedTextColor.DARK_GRAY);

    public CoinflipSystem(Login plugin, CoinflipDatabase database, Economy economy, CoinflipLogger logger, MessageManager msg, Set<UUID> playersChallenging) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.logger = logger; // [Req 9]
        this.msg = msg; // [Req 1]
        this.playersChallenging = playersChallenging;
        getPendingGames(true); // [Req 4] Initial cache load
    }

    // [Req 8] Getter for admin menu
    public Map<UUID, BukkitTask> getActiveAnimations() {
        return activeAnimations;
    }

    // [Req 4] Cache logic moved from CoinflipMenu
    public CompletableFuture<List<CoinflipGame>> getPendingGames(boolean force) {
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

    // [Req 4] Helper for /cf join <id>
    public CoinflipGame getPendingGameById(long gameId) {
        // Force a refresh if cache is old, but don't wait for it
        getPendingGames(false);
        return pendingGamesCache.stream()
                .filter(g -> g.getGameId() == gameId)
                .findFirst()
                .orElse(null);
    }

    public void startCoinflipGame(Player challenger, CoinflipGame game) {
        Player creator = Bukkit.getPlayer(game.getCreatorUUID());
        if (creator == null || !creator.isOnline()) {
            msg.send(challenger, "&c" + game.getCreatorName() + " is no longer online.");
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        if (challenger.getUniqueId().equals(creator.getUniqueId())) {
            msg.send(challenger, "&cYou cannot challenge your own coinflip!");
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        double amount = game.getAmount();

        if (!economy.has(challenger, amount)) {
            msg.send(challenger, "&cYou do not have enough money (" + economy.format(amount) + ") to join this coinflip.");
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        game.setChallenger(challenger);

        database.activateCoinflip(game.getGameId()).whenCompleteAsync((success, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error activating coinflip game " + game.getGameId(), error);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msg.send(challenger, "&cAn error occurred trying to start the game. Please try again.");
                    playersChallenging.remove(challenger.getUniqueId()); // [Req 2] Remove lock on fail
                });
                return;
            }

            if (!success) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    msg.send(challenger, "&cThis coinflip is no longer available.");
                    playersChallenging.remove(challenger.getUniqueId()); // [Req 2] Remove lock on fail
                });
                return;
            }

            // Success!
            Bukkit.getScheduler().runTask(plugin, () -> {
                EconomyResponse withdrawResp = economy.withdrawPlayer(challenger, amount);
                if (!withdrawResp.transactionSuccess()) {
                    msg.send(challenger, "&cFailed to withdraw funds: " + withdrawResp.errorMessage);
                    plugin.getLogger().warning("Failed to withdraw from challenger " + challenger.getName() + " for coinflip " + game.getGameId() + " after activating it. Attempting to remove game.");
                    database.removeCoinflip(game.getGameId()); // Rollback
                    playersChallenging.remove(challenger.getUniqueId()); // [Req 2] Remove lock on fail
                    return;
                }

                // [Req 4] Remove game from pending cache
                pendingGamesCache.removeIf(g -> g.getGameId() == game.getGameId());

                msg.send(challenger, "&eYou challenged " + creator.getName() + "'s coinflip for " + economy.format(amount) + "!");
                msg.send(creator, "&e" + challenger.getName() + " challenged your coinflip for " + economy.format(amount) + "!");
                // [Req 9]
                logger.logGame(challenger.getName() + " challenged " + creator.getName() + "'s coinflip (ID: " + game.getGameId() + ") for `" + economy.format(amount) + "`");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Player creatorOnline = Bukkit.getPlayer(game.getCreatorUUID());
                        Player challengerOnline = Bukkit.getPlayer(game.getChallengerUUID());

                        if (creatorOnline != null && creatorOnline.isOnline() && challengerOnline != null && challengerOnline.isOnline()) {
                            openAnimationGUI(creatorOnline, challengerOnline, game);
                        } else {
                            // One player logged off in the 5 ticks, handle disconnect
                            handlePlayerDisconnect(creatorOnline, challengerOnline, game);
                        }
                    }
                }.runTaskLater(plugin, 5L);
            });
        });
    }

    private void openAnimationGUI(Player p1, Player p2, CoinflipGame game) {
        // [Req 6] Use Component title
        final Inventory gui = Bukkit.createInventory(null, ANIMATION_GUI_SIZE, ANIMATION_TITLE);

        ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < 9; i++) gui.setItem(i, grayPane);
        for (int i = 18; i < 27; i++) gui.setItem(i, grayPane);

        ItemStack p1Head = getPlayerHead(p1.getName());
        ItemStack p2Head = getPlayerHead(p2.getName());

        gui.setItem(10, p1Head);
        // [Req 5] FIN: Do not set the right-side (p2) head. The animation will fill this space.
        // gui.setItem(16, p2Head);

        p1.openInventory(gui);
        p2.openInventory(gui);

        BukkitTask animationTask = new BukkitRunnable() {
            int ticksElapsed = 0;
            int currentPos = 10;
            boolean movingRight = true;
            ItemStack[] heads = {p1Head, p2Head};
            int headIndex = 0;

            @Override
            public void run() {
                Player p1Online = Bukkit.getPlayer(game.getCreatorUUID());
                Player p2Online = Bukkit.getPlayer(game.getChallengerUUID());

                if (p1Online == null || !p1Online.isOnline() || p2Online == null || !p2Online.isOnline()) {
                    this.cancel();
                    handlePlayerDisconnect(p1Online, p2Online, game);
                    return;
                }

                if (ticksElapsed >= ANIMATION_DURATION_TICKS) {
                    this.cancel();
                    finishCoinflip(p1Online, p2Online, game, gui);
                    return;
                }

                // Check if players closed the GUI
                if (ticksElapsed > 5) { // Give time for GUI to open
                    InventoryView p1View = p1Online.getOpenInventory();
                    InventoryView p2View = p2Online.getOpenInventory();

                    // Compare components
                    if (!ANIMATION_TITLE.equals(p1View.title()) ||
                            !ANIMATION_TITLE.equals(p2View.title()))
                    {
                        this.cancel();
                        handlePlayerDisconnect(p1Online, p2Online, game);
                        return;
                    }
                }

                // Slide logic
                if (ticksElapsed > 0 && ticksElapsed % ANIMATION_SLIDE_SPEED_TICKS == 0) {
                    gui.setItem(currentPos, null);

                    if (movingRight) {
                        currentPos++;
                        if (currentPos > 16) {
                            currentPos = 15;
                            movingRight = false;
                            headIndex = (headIndex + 1) % 2;
                        }
                    } else {
                        currentPos--;
                        if (currentPos < 10) {
                            currentPos = 11;
                            movingRight = true;
                            headIndex = (headIndex + 1) % 2;
                        }
                    }
                    gui.setItem(currentPos, heads[headIndex]);

                    p1Online.playSound(p1Online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                    p2Online.playSound(p2Online.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.5f, 1.2f);
                }

                ticksElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 1L);

        activeAnimations.put(game.getCreatorUUID(), animationTask);
        activeAnimations.put(game.getChallengerUUID(), animationTask);
    }


    private void finishCoinflip(Player p1, Player p2, CoinflipGame game, Inventory gui) {
        UUID p1UUID = game.getCreatorUUID();
        UUID p2UUID = game.getChallengerUUID();

        // [Req 8] Remove task from map
        BukkitTask task = activeAnimations.remove(p1UUID);
        if (p2UUID != null) activeAnimations.remove(p2UUID);

        if (task == null) {
            return; // Already handled (e.g., by admin cancel)
        }
        // Don't cancel task, it's already finished

        playersChallenging.remove(p1UUID); // [Req 2]
        if (p2UUID != null) playersChallenging.remove(p2UUID); // [Req 2]

        CoinflipGame.CoinSide winningSide = random.nextBoolean() ? CoinflipGame.CoinSide.HEADS : CoinflipGame.CoinSide.TAILS;
        boolean creatorChoseWinningSide = game.getChosenSide() == winningSide;

        Player winner = creatorChoseWinningSide ? p1 : p2;
        Player loser = creatorChoseWinningSide ? p2 : p1;

        double totalPot = game.getAmount() * 2;

        // [Req 9]
        logger.logGame("Game ID: " + game.getGameId() + " | Creator: " + p1.getName() + " (" + game.getChosenSide().name() + ") | Challenger: " + p2.getName() + " | Result: " + winningSide.name() + " | Winner: " + winner.getName() + " | Pot: " + economy.format(totalPot));

        // Update database (remove game, update stats)
        database.removeCoinflip(game.getGameId());
        database.updatePlayerStats(winner.getUniqueId(), winner.getName(), true);
        database.updatePlayerStats(loser.getUniqueId(), loser.getName(), false);

        // GUI result display
        ItemStack winnerTrophy = createGuiItem(Material.GOLD_BLOCK, Component.text(winner.getName() + " Wins!", NamedTextColor.GOLD));
        ItemStack loserBlock = createGuiItem(Material.REDSTONE_BLOCK, Component.text(loser.getName() + " Lost.", NamedTextColor.RED));

        gui.setItem(13, winningSide == CoinflipGame.CoinSide.HEADS ?
                createGuiItem(Material.GOLD_BLOCK, Component.text("Result: HEADS", NamedTextColor.AQUA)) :
                createGuiItem(Material.GOLD_BLOCK, Component.text("Result: TAILS", NamedTextColor.LIGHT_PURPLE))
        );

        if (creatorChoseWinningSide) {
            gui.setItem(10, winnerTrophy);
            gui.setItem(16, loserBlock);
        } else {
            gui.setItem(10, loserBlock);
            gui.setItem(16, winnerTrophy);
        }

        // Payout and messaging
        EconomyResponse depositResp = economy.depositPlayer(winner, totalPot);
        if (!depositResp.transactionSuccess()) {
            plugin.getLogger().severe("CRITICAL: Failed payout for Coinflip game " + game.getGameId() + " to " + winner.getName() + " for " + economy.format(totalPot));
            msg.send(winner, "&cCRITICAL: FAILED TO PAYOUT YOUR COINFLIP WINNINGS! Contact staff immediately.");
            msg.send(loser, "&cCRITICAL: FAILED TO PAYOUT " + winner.getName() + "'s WINNINGS. Contact staff immediately.");
            logger.logAdmin("CRITICAL: FAILED PAYOUT for CF Game " + game.getGameId() + ". Winner: " + winner.getName() + ", Pot: " + economy.format(totalPot));
        }

        // [Req 4] Broadcast winner
        String broadcastMsg = "<yellow>" + winner.getName() + "</yellow> won a coinflip against <yellow>" + loser.getName() + "</yellow> for <gold>" + economy.format(totalPot) + "</gold>!";
        msg.broadcast(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(broadcastMsg));

        winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

        // Close inventories after 3 seconds
        new BukkitRunnable() {
            @Override
            public void run() {
                Player p1Online = Bukkit.getPlayer(p1UUID);
                Player p2Online = Bukkit.getPlayer(p2UUID);
                if (p1Online != null && p1Online.isOnline() && ANIMATION_TITLE.equals(p1Online.getOpenInventory().title())) p1Online.closeInventory();
                if (p2Online != null && p2Online.isOnline() && ANIMATION_TITLE.equals(p2Online.getOpenInventory().title())) p2Online.closeInventory();
            }
        }.runTaskLater(plugin, 3 * 20L);
    }

    // [Req 8] Admin function to cancel a game
    public void adminCancelGame(CoinflipGame game, String adminName) {
        UUID p1UUID = game.getCreatorUUID();
        UUID p2UUID = game.getChallengerUUID();
        double amount = game.getAmount();

        // [Req 4] Remove from cache if it's pending
        pendingGamesCache.removeIf(g -> g.getGameId() == game.getGameId());

        // Check if game is 'ACTIVE' (in an animation)
        if (activeAnimations.containsKey(p1UUID) || (p2UUID != null && activeAnimations.containsKey(p2UUID))) {
            BukkitTask task = activeAnimations.remove(p1UUID);
            if (task == null && p2UUID != null) task = activeAnimations.remove(p2UUID);

            if (task != null) task.cancel();

            playersChallenging.remove(p1UUID); // [Req 2]
            if (p2UUID != null) playersChallenging.remove(p2UUID); // [Req 2]

            // Both players are in the game, refund both
            Player p1 = Bukkit.getPlayer(p1UUID);
            Player p2 = Bukkit.getPlayer(p2UUID);

            if (p1 != null && p1.isOnline()) {
                economy.depositPlayer(p1, amount);
                msg.send(p1, "&eYour coinflip (ID: " + game.getGameId() + ") was cancelled by an admin. Your " + economy.format(amount) + " was refunded.");
                if (ANIMATION_TITLE.equals(p1.getOpenInventory().title())) p1.closeInventory();
            } else {
                plugin.getLogger().warning("Player " + game.getCreatorName() + " (P1) was offline for admin CF cancel, refunding.");
                economy.depositPlayer(Bukkit.getOfflinePlayer(p1UUID), amount);
            }

            if (p2 != null && p2.isOnline()) {
                economy.depositPlayer(p2, amount);
                msg.send(p2, "&eYour coinflip (ID: " + game.getGameId() + ") was cancelled by an admin. Your " + economy.format(amount) + " was refunded.");
                if (ANIMATION_TITLE.equals(p2.getOpenInventory().title())) p2.closeInventory();
            } else {
                plugin.getLogger().warning("Player " + game.getChallengerName() + " (P2) was offline for admin CF cancel, refunding.");
                economy.depositPlayer(Bukkit.getOfflinePlayer(p2UUID), amount);
            }

            logger.logAdmin(adminName + " cancelled ACTIVE game ID " + game.getGameId() + " (" + game.getCreatorName() + " vs " + game.getChallengerName() + "). Both players refunded " + economy.format(amount) + ".");

        } else if (game.getStatus().equals("PENDING")) {
            // Game is 'PENDING', only refund creator
            Player p1 = Bukkit.getPlayer(p1UUID);
            if (p1 != null && p1.isOnline()) {
                economy.depositPlayer(p1, amount);
                msg.send(p1, "&eYour coinflip (ID: " + game.getGameId() + ") was cancelled by an admin. Your " + economy.format(amount) + " was refunded.");
            } else {
                plugin.getLogger().warning("Player " + game.getCreatorName() + " (P1) was offline for admin CF cancel, refunding.");
                economy.depositPlayer(Bukkit.getOfflinePlayer(p1UUID), amount);
            }
            logger.logAdmin(adminName + " cancelled PENDING game ID " + game.getGameId() + " (" + game.getCreatorName() + "). Refunded " + economy.format(amount) + ".");
        }

        // Remove from DB regardless of state
        database.removeCoinflip(game.getGameId());
    }

    private void handlePlayerDisconnect(Player p1, Player p2, CoinflipGame game) {
        UUID p1UUID = game.getCreatorUUID();
        UUID p2UUID = game.getChallengerUUID();
        double totalPot = game.getAmount() * 2;

        // [Req 8] Remove task from map
        activeAnimations.remove(p1UUID);
        if (p2UUID != null) activeAnimations.remove(p2UUID);

        playersChallenging.remove(p1UUID); // [Req 2]
        if (p2UUID != null) playersChallenging.remove(p2UUID); // [Req 2]

        // Remove from DB
        database.removeCoinflip(game.getGameId());

        Player winner = null;
        Player loser = null;

        if (p1 == null || !p1.isOnline()) {
            // P1 (Creator) disconnected
            winner = p2;
            loser = p1;
        } else if (p2 == null || !p2.isOnline()) {
            // P2 (Challenger) disconnected
            winner = p1;
            loser = p2;
        } else {
            // Both are online but closed GUI, refund both and cancel game
            economy.depositPlayer(p1, game.getAmount());
            economy.depositPlayer(p2, game.getAmount());
            msg.send(p1, "&eCoinflip (ID: " + game.getGameId() + ") cancelled as one of you closed the window. Money refunded.");
            msg.send(p2, "&eCoinflip (ID: " + game.getGameId() + ") cancelled as one of you closed the window. Money refunded.");
            logger.logGame("Game ID " + game.getGameId() + " cancelled (GUI closed). Refunded " + economy.format(game.getAmount()) + " to both " + p1.getName() + " and " + p2.getName() + ".");
            return;
        }

        String loserName = (loser == p1) ? game.getCreatorName() : game.getChallengerName();

        if (winner != null) {
            // Payout winner
            EconomyResponse depositResp = economy.depositPlayer(winner, totalPot);
            if (!depositResp.transactionSuccess()) {
                plugin.getLogger().severe("CRITICAL: Failed payout for Coinflip game " + game.getGameId() + " (due to disconnect) to " + winner.getName() + " for " + economy.format(totalPot));
                msg.send(winner, "&cCRITICAL: FAILED TO PAYOUT YOUR COINFLIP WINNINGS! Contact staff immediately.");
                logger.logAdmin("CRITICAL: FAILED PAYOUT (Disconnect) for CF Game " + game.getGameId() + ". Winner: " + winner.getName() + ", Pot: " + economy.format(totalPot));
            }

            // Update stats
            database.updatePlayerStats(winner.getUniqueId(), winner.getName(), true);
            UUID loserUUID = (loser == p1) ? game.getCreatorUUID() : game.getChallengerUUID();
            database.updatePlayerStats(loserUUID, loserName, false);

            // Send message and log
            msg.send(winner, "&a" + loserName + " disconnected from the coinflip. You win " + economy.format(totalPot) + "!");
            logger.logGame("Game ID: " + game.getGameId() + " | " + loserName + " disconnected. | Winner: " + winner.getName() + " | Pot: " + economy.format(totalPot));

            // [Req 4] Broadcast winner
            String broadcastMsg = "<yellow>" + winner.getName() + "</yellow> won a coinflip against <yellow>" + loserName + "</yellow> (who disconnected) for <gold>" + economy.format(totalPot) + "</gold>!";
            msg.broadcast(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(broadcastMsg));
        }
    }


    // [Req 6]
    private ItemStack getPlayerHead(String ownerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwner(ownerName);
            meta.displayName(Component.text(ownerName, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            head.setItemMeta(meta);
        }
        return head;
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
    /**
     * Safely removes a pending game from the cache without blocking the main thread.
     * This should be called from a main-thread task.
     * @param gameId The ID of the game to remove.
     */
    public void removePendingGameFromCache(long gameId) {
        pendingGamesCache.removeIf(g -> g.getGameId() == gameId);
    }
}