package me.login.misc.dailyreward;

import me.login.Login;
import me.login.misc.tokens.TokenManager; // Import TokenManager
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class DailyRewardManager {

    private final Login plugin;
    private final DailyRewardDatabase database;
    private final DailyRewardLogger logger;
    private final Economy economy;
    private final TokenManager tokenManager; // CHANGED: Added TokenManager
    private final Component serverPrefix;
    private final MiniMessage miniMessage;

    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(24);
    private final Map<String, Reward> rankRewards = new LinkedHashMap<>();

    public record Reward(String permission, int coins, int tokens, String prettyName) {}

    // CHANGED: Constructor now accepts TokenManager
    public DailyRewardManager(Login plugin, DailyRewardDatabase database, DailyRewardLogger logger, Economy economy, TokenManager tokenManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.economy = economy;
        this.tokenManager = tokenManager; // Store it

        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        this.serverPrefix = miniMessage.deserialize(prefixString + " ");

        rankRewards.put("elite", new Reward("mineaurora.dailyreward.elite", 1750, 2, "<green>Elite</green>"));
        rankRewards.put("ace", new Reward("mineaurora.dailyreward.ace", 2500, 2, "<blue>Ace</blue>"));
        rankRewards.put("overlord", new Reward("mineaurora.dailyreward.overlord", 4000, 4, "<red>Overlord</red>"));
        rankRewards.put("immortal", new Reward("mineaurora.dailyreward.immortal", 5000, 5, "<gold>Immortal</gold>"));
        rankRewards.put("supreme", new Reward("mineaurora.dailyreward.supreme", 6000, 6, "<dark_red>Supreme</dark_red>"));
        rankRewards.put("phantom", new Reward("mineaurora.dailyreward.phantom", 8500, 8, "<dark_purple>Phantom</dark_purple>"));
    }

    public Component getPrefix() { return serverPrefix; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public Map<String, Reward> getRankRewards() { return rankRewards; }
    public DailyRewardDatabase getDatabase() { return database; }

    public void attemptClaim(Player player) {
        if (player.hasPermission("mineaurora.dailyreward.rank")) {
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    plugin.getServer().dispatchCommand(player, "dailyreward gui")
            );
        } else {
            claimDefaultReward(player);
        }
    }

    public long getStartOfCurrentDay() {
        return System.currentTimeMillis() - COOLDOWN_MS;
    }

    private long getCooldownExpiry(long lastClaim) {
        return lastClaim + COOLDOWN_MS;
    }

    public void claimDefaultReward(Player player) {
        database.getLastClaimTime(player.getUniqueId(), "default").thenAccept(lastClaim -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(lastClaim);

            if (now < expiry) {
                long timeLeftMs = expiry - now;
                sendMsg(player, "<red>You have already claimed your daily reward! Come back in " + formatTimeLeft(timeLeftMs) + ".</red>");
                return;
            }

            int coins = 750;
            int tokens = 1;

            economy.depositPlayer(player, coins);

            // CHANGED: Use TokenManager
            tokenManager.addTokens(player.getUniqueId(), tokens);

            database.setLastClaimTime(player.getUniqueId(), now, "default");

            sendMsg(player, "<green>You claimed your daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + coins + " Coins</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + tokens + " Token</white></gray>"));
        });
    }

    public CompletableFuture<Boolean> claimRankedReward(Player player, String rankKey, Reward reward) {
        return database.getLastClaimTime(player.getUniqueId(), rankKey).thenApply(lastClaim -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(lastClaim);

            if (now < expiry) {
                sendMsg(player, "<red>You have already claimed this reward! Come back in " + formatTimeLeft(expiry - now) + ".</red>");
                return false;
            }

            economy.depositPlayer(player, reward.coins);

            // CHANGED: Use TokenManager
            tokenManager.addTokens(player.getUniqueId(), reward.tokens);

            database.setLastClaimTime(player.getUniqueId(), now, rankKey);

            sendMsg(player, "<green>You claimed your " + reward.prettyName + " <green>daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + reward.coins + " Coins</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + reward.tokens + (reward.tokens > 1 ? " Tokens" : " Token") + "</white></gray>"));

            logger.log("`" + player.getName() + "` claimed their " + reward.prettyName + " reward (`" + reward.coins + "` coins, `" + reward.tokens + "` tokens).");

            return true;
        });
    }

    public String formatTimeLeft(long millis) {
        if (millis <= 0) return "0s";
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

    private void sendMsg(Player player, String message) {
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(message)));
    }
}