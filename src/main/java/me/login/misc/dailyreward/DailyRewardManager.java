package me.login.misc.dailyreward;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DailyRewardManager {

    private final Login plugin;
    private final DailyRewardDatabase database;
    private final DailyRewardLogger logger;
    private final Economy economy;
    private final Component serverPrefix;
    private final MiniMessage miniMessage;

    // Cooldown: 24 hours
    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(24);

    // Rank definitions
    // Using LinkedHashMap to preserve insertion order for permission checking
    private final Map<String, Reward> rankRewards = new LinkedHashMap<>();

    // A simple struct-like class for rewards
    public record Reward(String permission, int coins, int tokens, String prettyName) {}

    public DailyRewardManager(Login plugin, DailyRewardDatabase database, DailyRewardLogger logger, Economy economy) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.economy = economy;

        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        this.serverPrefix = miniMessage.deserialize(prefixString + " ");

        // --- Ranks ---
        rankRewards.put("elite", new Reward("mineaurora.dailyreward.elite", 1750, 2, "<green>Elite</green>"));
        rankRewards.put("ace", new Reward("mineaurora.dailyreward.ace", 2500, 2, "<blue>Ace</blue>"));
        rankRewards.put("overlord", new Reward("mineaurora.dailyreward.overlord", 4000, 4, "<red>Overlord</red>"));
        rankRewards.put("immortal", new Reward("mineaurora.dailyreward.immortal", 5000, 5, "<gold>Immortal</gold>"));
        rankRewards.put("supreme", new Reward("mineaurora.dailyreward.supreme", 6000, 6, "<dark_red>Supreme</dark_red>"));
        rankRewards.put("phantom", new Reward("mineaurora.dailyreward.phantom", 8500, 8, "<dark_purple>Phantom</dark_purple>"));
    }

    /**
     * Gets the server prefix (Kyori Component).
     */
    public Component getPrefix() {
        return serverPrefix;
    }

    /**
     * Gets the MiniMessage instance.
     */
    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    /**
     * Gets the map of defined rank rewards.
     */
    public Map<String, Reward> getRankRewards() {
        return rankRewards;
    }

    public DailyRewardDatabase getDatabase() {
        return database;
    }

    /**
     * Main entry point for claiming a reward (from command or NPC).
     * This will either open the GUI for ranked players or claim directly for defaults.
     */
    public void attemptClaim(Player player) {
        // Check if player is ranked
        if (player.hasPermission("mineaurora.dailyreward.rank")) {
            // Open the ranked GUI
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getServer().dispatchCommand(player, "dailyreward gui")
            );
        } else {
            // Try to claim the default reward
            claimDefaultReward(player);
        }
    }

    /**
     * Gets the player's highest available rank reward.
     * @param player The player.
     * @return The Reward object, or null if they have no rank permission.
     */
    public Reward getBestRankReward(Player player) {
        Reward best = null;
        for (Reward reward : rankRewards.values()) {
            if (player.hasPermission(reward.permission)) {
                best = reward; // Keep updating to the "last" one in the map
            }
        }
        return best;
    }

    /**
     * Gets the start of the "day" for rewards (24h cooldown).
     */
    private long getCooldownExpiry(long lastClaim) {
        return lastClaim + COOLDOWN_MS;
    }

    public long getStartOfCurrentDay() {
        return System.currentTimeMillis() - COOLDOWN_MS;
    }


    /**
     * Handles claiming the default (non-ranked) reward.
     */
    public void claimDefaultReward(Player player) {
        // Check cooldown for "default" rank key
        database.getLastClaimTime(player.getUniqueId(), "default").thenAccept(lastClaim -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(lastClaim);

            if (now < expiry) {
                // Cooldown active
                long timeLeftMs = expiry - now;
                String timeLeftStr = formatTimeLeft(timeLeftMs);
                sendMsg(player, "<red>You have already claimed your daily reward! Come back in " + timeLeftStr + ".</red>");
                return;
            }

            // Cooldown is over, grant reward
            int coins = 750;
            int tokens = 1;

            economy.depositPlayer(player, coins);
            addTokens(player.getUniqueId(), tokens);

            database.setLastClaimTime(player.getUniqueId(), now, "default");

            // --- FIX: Split messages to prevent multiple prefixes ---
            sendMsg(player, "<green>You claimed your daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + coins + " Coins</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + tokens + " Token</white></gray>"));
            // --- END FIX ---
        });
    }

    /**
     * Handles claiming a specific ranked reward from the GUI.
     * @return CompletableFuture<Boolean> true if successful, false if on cooldown.
     */
    public CompletableFuture<Boolean> claimRankedReward(Player player, String rankKey, Reward reward) {
        // Check cooldown for the specific rankKey
        return database.getLastClaimTime(player.getUniqueId(), rankKey).thenApply(lastClaim -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(lastClaim);

            if (now < expiry) {
                // Cooldown is active, check
                long timeLeftMs = expiry - now;
                String timeLeftStr = formatTimeLeft(timeLeftMs);
                sendMsg(player, "<red>You have already claimed this reward! Come back in " + timeLeftStr + ".</red>");
                return false; // Indicate failure
            }

            // Grant reward
            economy.depositPlayer(player, reward.coins);
            addTokens(player.getUniqueId(), reward.tokens);

            // Set cooldown
            database.setLastClaimTime(player.getUniqueId(), now, rankKey);

            // --- FIX: Split messages to prevent multiple prefixes ---
            sendMsg(player, "<green>You claimed your " + reward.prettyName + " <green>daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + reward.coins + " Coins</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + reward.tokens + (reward.tokens > 1 ? " Tokens" : " Token") + "</white></gray>"));
            // --- END FIX ---

            // Log to Discord
            logger.log("`" + player.getName() + "` claimed their " + reward.prettyName + " reward (`" + reward.coins + "` coins, `" + reward.tokens + "` tokens).");

            return true; // Indicate success
        });
    }

    /**
     * Adds tokens to the player's database balance.
     */
    private void addTokens(UUID uuid, int amount) {
        if (amount <= 0) return;
        database.addTokens(uuid, amount);
    }

    /**
     * Formats milliseconds into a human-readable string.
     */
    public String formatTimeLeft(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append("s");

        return sb.toString().trim();
    }

    /**
     * Helper to send a prefixed MiniMessage to a player.
     */
    private void sendMsg(Player player, String message) {
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(message)));
    }
}