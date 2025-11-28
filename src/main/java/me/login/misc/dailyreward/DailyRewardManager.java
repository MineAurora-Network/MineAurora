package me.login.misc.dailyreward;

import me.login.Login;
import me.login.misc.tokens.TokenManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private final TokenManager tokenManager;
    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(24);
    private static final long STREAK_WINDOW_MS = TimeUnit.HOURS.toMillis(30);

    private final Map<String, Reward> rankRewards = new LinkedHashMap<>();

    public record Reward(String permission, int coins, int tokens, String prettyName) {}

    public DailyRewardManager(Login plugin, DailyRewardDatabase database, DailyRewardLogger logger, Economy economy, TokenManager tokenManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.economy = economy;
        this.tokenManager = tokenManager;

        this.miniMessage = MiniMessage.miniMessage();

        // FIX: Only load 'server_prefix' to prevent double prefixes.
        // Ignored 'server_prefix_2' intentionally.
        String p1Raw = plugin.getConfig().getString("server_prefix");
        if (p1Raw == null) {
            p1Raw = "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b> <dark_gray>•</dark_gray>";
        }

        // Append a single space after the prefix
        this.serverPrefix = parseMixedContent(p1Raw).append(Component.text(" "));

        rankRewards.put("elite", new Reward("mineaurora.dailyreward.elite", 1750, 2, "<green>Elite</green>"));
        rankRewards.put("ace", new Reward("mineaurora.dailyreward.ace", 2500, 2, "<blue>Ace</blue>"));
        rankRewards.put("overlord", new Reward("mineaurora.dailyreward.overlord", 4000, 4, "<red>Overlord</red>"));
        rankRewards.put("immortal", new Reward("mineaurora.dailyreward.immortal", 5000, 5, "<gold>Immortal</gold>"));
        rankRewards.put("supreme", new Reward("mineaurora.dailyreward.supreme", 6000, 6, "<dark_red>Supreme</dark_red>"));
        rankRewards.put("phantom", new Reward("mineaurora.dailyreward.phantom", 8500, 8, "<dark_purple>Phantom</dark_purple>"));
    }

    private Component parseMixedContent(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (input.contains("&") || input.contains("§")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }
        try {
            return miniMessage.deserialize(input);
        } catch (Exception e) {
            return Component.text(input);
        }
    }

    public Component getPrefix() { return serverPrefix; }
    public MiniMessage getMiniMessage() { return miniMessage; }
    public Map<String, Reward> getRankRewards() { return rankRewards; }
    public DailyRewardDatabase getDatabase() { return database; }

    public void attemptClaim(Player player) {
        if (player.hasPermission("mineaurora.dailyreward.rank")) {
            sendMsg(player, "<red>Ranked players should use the Daily Reward Menu (/dailyreward gui).</red>");
        } else {
            claimDefaultReward(player);
        }
    }

    public String getHighestRankKey(Player player) {
        String highest = "default";
        for (Map.Entry<String, Reward> entry : rankRewards.entrySet()) {
            if (player.hasPermission(entry.getValue().permission())) {
                highest = entry.getKey();
            }
        }
        return highest;
    }

    private long getCooldownExpiry(long lastClaim) {
        return lastClaim + COOLDOWN_MS;
    }

    public void claimDefaultReward(Player player) {
        database.getClaimData(player.getUniqueId(), "default").thenAccept(data -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(data.lastClaimTime());

            if (now < expiry) {
                long timeLeftMs = expiry - now;
                sendMsg(player, "<red>You have already claimed your daily reward! Come back in " + formatTimeLeft(timeLeftMs) + ".</red>");
                return;
            }

            int streak = calculateNewStreak(data.lastClaimTime(), now, data.streak());

            int baseCoins = 750;
            int tokens = 1;

            double multiplier = 1.0 + (streak * 0.10);
            int finalCoins = (int) (baseCoins * multiplier);

            economy.depositPlayer(player, finalCoins);
            tokenManager.addTokens(player.getUniqueId(), tokens);

            database.saveClaim(player.getUniqueId(), "default", now, streak);

            sendMsg(player, "<green>You claimed your daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + finalCoins + " Coins</white> <gray>(" + (int)(streak * 10) + "% Streak Bonus)</gray></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + tokens + " Token</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<yellow>Current Streak: <white>" + streak + " Days</white></yellow>"));

            logger.logDefault(player.getName(), finalCoins, tokens, streak);
        });
    }

    public CompletableFuture<Boolean> claimRankedReward(Player player, String rankKey, Reward reward) {
        return database.getClaimData(player.getUniqueId(), rankKey).thenApply(data -> {
            long now = System.currentTimeMillis();
            long expiry = getCooldownExpiry(data.lastClaimTime());

            if (now < expiry) {
                sendMsg(player, "<red>You have already claimed this reward! Come back in " + formatTimeLeft(expiry - now) + ".</red>");
                return false;
            }

            int streak = calculateNewStreak(data.lastClaimTime(), now, data.streak());

            double multiplier = 1.0 + (streak * 0.10);
            int finalCoins = (int) (reward.coins() * multiplier);

            economy.depositPlayer(player, finalCoins);
            tokenManager.addTokens(player.getUniqueId(), reward.tokens());

            database.saveClaim(player.getUniqueId(), rankKey, now, streak);

            sendMsg(player, "<green>You claimed your " + reward.prettyName() + " <green>daily reward!</green>");
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + finalCoins + " Coins</white> <gray>(" + (int)(streak * 10) + "% Streak Bonus)</gray></gray>"));
            player.sendMessage(miniMessage.deserialize("<gray>+ <white>" + reward.tokens() + (reward.tokens() > 1 ? " Tokens" : " Token") + "</white></gray>"));
            player.sendMessage(miniMessage.deserialize("<yellow>Current Streak: <white>" + streak + " Days</white></yellow>"));

            logger.logRanked(player.getName(), reward.prettyName(), finalCoins, reward.tokens(), streak);

            return true;
        });
    }

    private int calculateNewStreak(long lastClaimTime, long now, int currentStreak) {
        if (lastClaimTime == 0) return 1;

        long timeSince = now - lastClaimTime;

        if (timeSince <= STREAK_WINDOW_MS) {
            return currentStreak + 1;
        } else {
            return 1;
        }
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