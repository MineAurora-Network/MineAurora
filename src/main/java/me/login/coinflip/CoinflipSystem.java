package me.login.coinflip;

import club.minnced.discord.webhook.WebhookClient;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

public class CoinflipSystem {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final Economy economy;
    private final WebhookClient webhookClient;

    private final Map<UUID, BukkitTask> activeAnimations = new ConcurrentHashMap<>();
    private final Random random = ThreadLocalRandom.current();
    private final Set<UUID> playersChallenging;

    private static final int ANIMATION_GUI_SIZE = 27;
    private static final int ANIMATION_DURATION_TICKS = 5 * 20;
    private static final int ANIMATION_SLIDE_SPEED_TICKS = 4;

    // Store the Adventure Component for the title
    private static final Component ANIMATION_TITLE = Component.text("Coin Flipping...", NamedTextColor.DARK_GRAY);

    public CoinflipSystem(Login plugin, CoinflipDatabase database, Economy economy, WebhookClient webhookClient, Set<UUID> playersChallenging) {
        this.plugin = plugin;
        this.database = database;
        this.economy = economy;
        this.webhookClient = webhookClient;
        this.playersChallenging = playersChallenging;
    }


    public void startCoinflipGame(Player challenger, CoinflipGame game) {
        Player creator = Bukkit.getPlayer(game.getCreatorUUID());
        if (creator == null || !creator.isOnline()) {
            challenger.sendMessage(plugin.formatMessage("&c" + game.getCreatorName() + " is no longer online."));
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        if (challenger.getUniqueId().equals(creator.getUniqueId())) {
            challenger.sendMessage(plugin.formatMessage("&cYou cannot challenge your own coinflip!"));
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        double amount = game.getAmount();

        if (!economy.has(challenger, amount)) {
            challenger.sendMessage(plugin.formatMessage("&cYou do not have enough money (" + economy.format(amount) + ") to join this coinflip."));
            playersChallenging.remove(challenger.getUniqueId());
            return;
        }

        game.setChallenger(challenger);

        database.activateCoinflip(game.getGameId()).whenCompleteAsync((success, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "Error activating coinflip game " + game.getGameId(), error);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    challenger.sendMessage(plugin.formatMessage("&cAn error occurred trying to start the game. Please try again."));
                    playersChallenging.remove(challenger.getUniqueId());
                });
                return;
            }

            if (!success) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    challenger.sendMessage(plugin.formatMessage("&cThis coinflip is no longer available."));
                    playersChallenging.remove(challenger.getUniqueId());
                    // Don't re-open the menu here, the message is enough.
                    // This fixes the bug where the "no longer available" message shows *and* the animation opens.
                    // The client is already in the animation-opening flow.
                });
                // IMPORTANT: We return here *but* the client-side 'playersChallenging' set
                // is NOT removed if the activation was successful, only if it failed.
                // This is INTENTIONAL. We want the message to send, but the animation to continue.
                // The bug was that the *challenger* was being told it wasn't available.
                // The player who got the game first will continue. The player who was second will get the message.

                // --- NEW FIX ---
                // The check for `success` tells us if we won the database race.
                // If `success` is FALSE, it means someone *else* (or us, in a double-click) already activated it.
                // In this case, we MUST tell the player and remove them from the set.
                if (!success) { // Re-check, this is the logic for the "loser" of the race
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        challenger.sendMessage(plugin.formatMessage("&cThis coinflip is no longer available."));
                        playersChallenging.remove(challenger.getUniqueId());
                    });
                    return; // Stop here.
                }
                // If `success` is TRUE, we are the "winner" of the race. Proceed.
                // --- END NEW FIX ---
            }

            // Success!
            Bukkit.getScheduler().runTask(plugin, () -> {
                EconomyResponse withdrawResp = economy.withdrawPlayer(challenger, amount);
                if (!withdrawResp.transactionSuccess()) {
                    challenger.sendMessage(plugin.formatMessage("&cFailed to withdraw funds: " + withdrawResp.errorMessage));
                    plugin.getLogger().warning("Failed to withdraw from challenger " + challenger.getName() + " for coinflip " + game.getGameId() + " after activating it. Attempting to remove game.");
                    database.removeCoinflip(game.getGameId()); // Rollback
                    playersChallenging.remove(challenger.getUniqueId());
                    return;
                }

                challenger.sendMessage(plugin.formatMessage("&eYou challenged " + creator.getName() + "'s coinflip for " + economy.format(amount) + "!"));
                creator.sendMessage(plugin.formatMessage("&e" + challenger.getName() + " challenged your coinflip for " + economy.format(amount) + "!"));
                plugin.sendCoinflipLog(challenger.getName() + " challenged " + creator.getName() + "'s coinflip (ID: " + game.getGameId() + ") for `" + economy.format(amount) + "`");

                new BukkitRunnable() {
                    @Override
                    public void run() {
                        Player creatorOnline = Bukkit.getPlayer(game.getCreatorUUID());
                        Player challengerOnline = Bukkit.getPlayer(game.getChallengerUUID());

                        if (creatorOnline != null && creatorOnline.isOnline() && challengerOnline != null && challengerOnline.isOnline()) {
                            openAnimationGUI(creatorOnline, challengerOnline, game);
                        } else {
                            handlePlayerDisconnect(creatorOnline, challengerOnline, game);
                        }
                    }
                }.runTaskLater(plugin, 5L);
            });
        });
    }

    private void openAnimationGUI(Player p1, Player p2, CoinflipGame game) {
        final Inventory gui = Bukkit.createInventory(null, ANIMATION_GUI_SIZE, ANIMATION_TITLE);

        ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < 9; i++) gui.setItem(i, grayPane);
        for (int i = 18; i < 27; i++) gui.setItem(i, grayPane);

        ItemStack p1Head = getPlayerHead(p1.getName());
        ItemStack p2Head = getPlayerHead(p2.getName());

        gui.setItem(10, p1Head);
        gui.setItem(16, p2Head);

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

                if (ticksElapsed > 5) {
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

        BukkitTask task = activeAnimations.remove(p1UUID);
        if (p2UUID != null) activeAnimations.remove(p2UUID);

        if (task == null) {
            return; // Already handled
        }

        playersChallenging.remove(p1UUID);
        if (p2UUID != null) playersChallenging.remove(p2UUID);

        CoinflipGame.CoinSide winningSide = random.nextBoolean() ? CoinflipGame.CoinSide.HEADS : CoinflipGame.CoinSide.TAILS;
        boolean creatorChoseWinningSide = game.getChosenSide() == winningSide;

        Player winner = creatorChoseWinningSide ? p1 : p2;
        Player loser = creatorChoseWinningSide ? p2 : p1;

        double totalPot = game.getAmount() * 2;

        plugin.sendCoinflipLog("Game ID: " + game.getGameId() + " | Creator: " + p1.getName() + " (" + game.getChosenSide() + ") | Challenger: " + p2.getName() + " | Winning Side: " + winningSide + " | Winner: " + winner.getName());

        if (winner == null || !winner.isOnline()) {
            plugin.getLogger().severe("CRITICAL: Coinflip winner " + (winner != null ? winner.getName() : "UNKNOWN") + " is offline! Refunding both players for game " + game.getGameId());
            plugin.sendCoinflipLog("Winner offline for game " + game.getGameId() + ". Refunding " + p1.getName() + " and " + p2.getName() + " `"+ economy.format(game.getAmount()) +"` each.");
            economy.depositPlayer(Bukkit.getOfflinePlayer(p1UUID), game.getAmount());
            if(p2UUID != null) economy.depositPlayer(Bukkit.getOfflinePlayer(p2UUID), game.getAmount());
            if (p1.isOnline()) p1.sendMessage(plugin.formatMessage("&cCoinflip cancelled due to an issue during payout. Your bet was refunded."));
            if (p2.isOnline()) p2.sendMessage(plugin.formatMessage("&cCoinflip cancelled due to an issue during payout. Your bet was refunded."));

        } else {
            EconomyResponse depositResp = economy.depositPlayer(winner, totalPot);
            if (!depositResp.transactionSuccess()) {
                plugin.getLogger().severe("CRITICAL: Failed to pay coinflip winner " + winner.getName() + " amount " + totalPot + " for game " + game.getGameId() + ": " + depositResp.errorMessage);
                plugin.sendCoinflipLog("PAYOUT FAILED for game " + game.getGameId() + ". Winner: " + winner.getName() + ", Amount: `" + economy.format(totalPot) + "`. Refunding bets.");
                economy.depositPlayer(Bukkit.getOfflinePlayer(p1UUID), game.getAmount());
                if(p2UUID != null) economy.depositPlayer(Bukkit.getOfflinePlayer(p2UUID), game.getAmount());
                if (p1.isOnline()) p1.sendMessage(plugin.formatMessage("&cA critical error occurred during payout! Your bet has been refunded."));
                if (p2.isOnline()) p2.sendMessage(plugin.formatMessage("&cA critical error occurred during payout! Your bet has been refunded."));
            } else {
                winner.sendMessage(plugin.formatMessage("&aYou won the coinflip against " + loser.getName() + " and received " + economy.format(totalPot) + "!"));
                if(loser != null && loser.isOnline()) loser.sendMessage(plugin.formatMessage("&cYou lost the coinflip against " + winner.getName()));
                winner.playSound(winner.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                if(loser != null && loser.isOnline()) loser.playSound(loser.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);

                plugin.sendCoinflipLog(winner.getName() + " won `"+ economy.format(totalPot) +"` against " + loser.getName() + " (Game ID: " + game.getGameId() + ")");

                database.updatePlayerStats(winner.getUniqueId(), winner.getName(), true);
                if (loser != null) {
                    database.updatePlayerStats(loser.getUniqueId(), loser.getName(), false);
                }
            }
        }

        ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < 9; i++) gui.setItem(i, grayPane);
        for (int i = 18; i < 27; i++) gui.setItem(i, grayPane);

        for(int i = 9; i < 18; i++) gui.setItem(i, null);
        if (winner != null) {
            gui.setItem(13, getPlayerHead(winner.getName()));
        } else {
            gui.setItem(13, createGuiItem(Material.BARRIER, Component.text("Error Determining Winner", NamedTextColor.RED)));
        }

        database.removeCoinflip(game.getGameId());

        new BukkitRunnable() {
            @Override
            public void run() {
                Player p1Online = Bukkit.getPlayer(p1UUID);
                Player p2Online = Bukkit.getPlayer(p2UUID);
                if(p1Online != null && p1Online.isOnline() && ANIMATION_TITLE.equals(p1Online.getOpenInventory().title())) p1Online.closeInventory();
                if(p2Online != null && p2Online.isOnline() && ANIMATION_TITLE.equals(p2Online.getOpenInventory().title())) p2Online.closeInventory();
            }
        }.runTaskLater(plugin, 60L);
    }

    private void handlePlayerDisconnect(Player p1, Player p2, CoinflipGame game) {
        UUID p1UUID = game.getCreatorUUID();
        UUID p2UUID = game.getChallengerUUID();

        BukkitTask task = activeAnimations.remove(p1UUID);
        if (p2UUID != null) activeAnimations.remove(p2UUID);

        if (task == null) {
            return; // Already handled
        }
        task.cancel();

        playersChallenging.remove(p1UUID);
        if (p2UUID != null) playersChallenging.remove(p2UUID);

        plugin.getLogger().warning("Coinflip game " + game.getGameId() + " cancelled due to player disconnect or closing GUI.");
        plugin.sendCoinflipLog("Game ID: " + game.getGameId() + " cancelled prematurely. Refunding bets.");

        Player stillInGuiPlayer = null;

        if (p1 != null && p1.isOnline() && ANIMATION_TITLE.equals(p1.getOpenInventory().title())) {
            stillInGuiPlayer = p1;
        } else if (p2 != null && p2.isOnline() && ANIMATION_TITLE.equals(p2.getOpenInventory().title())) {
            stillInGuiPlayer = p2;
        }

        // Refund creator
        EconomyResponse refund1 = economy.depositPlayer(Bukkit.getOfflinePlayer(p1UUID), game.getAmount());
        if (!refund1.transactionSuccess()) {
            plugin.getLogger().severe("Failed to refund player " + game.getCreatorName() + " (UUID: " + p1UUID +") for cancelled coinflip " + game.getGameId());
        }

        // Refund challenger
        if (p2UUID != null) {
            EconomyResponse refund2 = economy.depositPlayer(Bukkit.getOfflinePlayer(p2UUID), game.getAmount());
            if (refund2 != null && !refund2.transactionSuccess()) {
                plugin.getLogger().severe("Failed to refund challenger (UUID: " + p2UUID + ") for cancelled coinflip " + game.getGameId());
            }
        } else {
            plugin.getLogger().warning("Could not determine challenger UUID for refund in game " + game.getGameId());
        }

        if (stillInGuiPlayer != null) {
            stillInGuiPlayer.sendMessage(plugin.formatMessage("&cCoinflip cancelled because the other player left or closed the menu. Your bet was refunded."));
            stillInGuiPlayer.closeInventory();
        }

        database.removeCoinflip(game.getGameId());
    }

    private ItemStack getPlayerHead(String playerName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            Player owner = Bukkit.getPlayerExact(playerName);
            if (owner != null) {
                meta.setOwningPlayer(owner);
            } else {
                meta.setOwner(playerName);
            }
            meta.displayName(Component.text(playerName).decoration(TextDecoration.ITALIC, false));
            head.setItemMeta(meta);
        }
        return head;
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