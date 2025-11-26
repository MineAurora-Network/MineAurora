package me.login.misc.playtimerewards;

import me.login.Login;
import me.login.misc.tokens.TokenManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PlaytimeRewardManager implements Listener {

    private final Login plugin;
    private final PlaytimeRewardDatabase database;
    private final PlaytimeRewardLogger logger;
    private final Economy economy;
    private final TokenManager tokenManager;
    private final Component serverPrefix;
    private final MiniMessage miniMessage;

    private final List<PlaytimeRewardLevel> rewardLevels;
    private final Map<UUID, PlaytimeRewardDatabase.PlayerPlaytimeData> playerDataCache = new ConcurrentHashMap<>();

    public static final int MAX_LEVEL = 84;
    private static final int SAVE_INTERVAL_SECONDS = 60 * 5;

    public PlaytimeRewardManager(Login plugin, PlaytimeRewardDatabase database, PlaytimeRewardLogger logger, Economy economy, TokenManager tokenManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.economy = economy;
        this.tokenManager = tokenManager;

        this.miniMessage = MiniMessage.miniMessage();

        // FIX: Robust Prefix Handling (Supports MiniMessage AND Legacy)
        String p1Raw = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b>");
        String p2Raw = plugin.getConfig().getString("server_prefix_2", "<white>:");

        Component p1 = parseMixedContent(p1Raw);
        Component p2 = parseMixedContent(p2Raw);

        this.serverPrefix = p1.append(p2).append(Component.text(" "));

        this.rewardLevels = new ArrayList<>(MAX_LEVEL);
        generateRewardLevels();

        startPlaytimeTracker();
    }

    // Helper to handle Strings that might contain BOTH MiniMessage tags AND legacy (&/§) codes
    private Component parseMixedContent(String input) {
        if (input == null) return Component.empty();

        // 1. If it contains legacy codes (& or §), convert them to MiniMessage format first OR use LegacySerializer
        // Using LegacyComponentSerializer to deserialize legacy parts into a Component
        if (input.contains("&") || input.contains("§")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }

        // 2. Otherwise, treat as standard MiniMessage
        try {
            return miniMessage.deserialize(input);
        } catch (Exception e) {
            // Fallback for broken MiniMessage syntax
            return Component.text(input);
        }
    }

    private void generateRewardLevels() {
        long currentCoinReward = 0;
        long currentPlaytime = 0;

        for (int level = 1; level <= 8; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(3);
            currentCoinReward = (level == 1) ? 500 : currentCoinReward + 400;
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 1));
        }

        for (int level = 9; level <= 63; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(5);
            currentCoinReward += 750;
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 2));
        }

        for (int level = 64; level <= MAX_LEVEL; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(5);
            currentCoinReward += 750;
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 3));
        }
    }

    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.get(uuid);
                    if (data == null) continue;

                    long newPlaytime = data.totalPlaytimeSeconds() + 1;

                    data = new PlaytimeRewardDatabase.PlayerPlaytimeData(newPlaytime, data.lastClaimedLevel(), data.notifiedLevel());
                    playerDataCache.put(uuid, data);
                    checkRewardEligibility(player, data);
                }

                if (ticks >= (SAVE_INTERVAL_SECONDS * 20)) {
                    ticks = 0;
                    saveAllPlayerData();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        loadPlayerData(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unloadPlayerData(event.getPlayer());
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        long statPlaytimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;

        database.getPlayerPlaytimeData(uuid).thenAccept(data -> {
            long finalPlaytime = Math.max(statPlaytimeSeconds, data.totalPlaytimeSeconds());

            PlaytimeRewardDatabase.PlayerPlaytimeData loadedData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                    finalPlaytime,
                    data.lastClaimedLevel(),
                    data.notifiedLevel()
            );
            playerDataCache.put(uuid, loadedData);
            checkRewardEligibility(player, loadedData);
        });
    }

    public void unloadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.remove(uuid);
        if (data != null) {
            database.savePlayerPlaytimeData(uuid, data.totalPlaytimeSeconds(), data.lastClaimedLevel(), data.notifiedLevel());
        }
    }

    public void saveAllPlayerData() {
        playerDataCache.forEach((uuid, data) -> {
            database.savePlayerPlaytimeData(uuid, data.totalPlaytimeSeconds(), data.lastClaimedLevel(), data.notifiedLevel());
        });
    }

    public void saveAllPlayerDataSync() {
        playerDataCache.forEach((uuid, data) -> {
            database.savePlayerPlaytimeDataSync(uuid, data.totalPlaytimeSeconds(), data.lastClaimedLevel(), data.notifiedLevel());
        });
    }

    private void checkRewardEligibility(Player player, PlaytimeRewardDatabase.PlayerPlaytimeData data) {
        int nextLevel = data.lastClaimedLevel() + 1;
        if (nextLevel > MAX_LEVEL) return;
        if (nextLevel <= data.notifiedLevel()) return;

        PlaytimeRewardLevel levelInfo = getLevelInfo(nextLevel);
        if (levelInfo == null) return;

        if (data.totalPlaytimeSeconds() >= levelInfo.timeRequiredSeconds()) {
            String message = "<green>You have unlocked Playtime Reward Level " + levelInfo.level() + "!</green><newline>" +
                    "<gray>Type <white>/ptrewards</white> to claim it.</gray>";
            sendMsg(player, message);

            PlaytimeRewardDatabase.PlayerPlaytimeData newData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                    data.totalPlaytimeSeconds(),
                    data.lastClaimedLevel(),
                    nextLevel
            );
            playerDataCache.put(player.getUniqueId(), newData);
            database.savePlayerPlaytimeData(player.getUniqueId(), newData.totalPlaytimeSeconds(), newData.lastClaimedLevel(), newData.notifiedLevel());
        }
    }

    public PlaytimeRewardLevel getLevelInfo(int level) {
        if (level < 1 || level > rewardLevels.size()) {
            return null;
        }
        return rewardLevels.get(level - 1);
    }

    public PlaytimeRewardDatabase.PlayerPlaytimeData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    public boolean claimReward(Player player, int levelToClaim) {
        UUID uuid = player.getUniqueId();
        PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.get(uuid);

        if (data == null) {
            sendMsg(player, "<red>Your playtime data is not loaded. Please re-log.</red>");
            return false;
        }

        PlaytimeRewardLevel levelInfo = getLevelInfo(levelToClaim);
        if (levelInfo == null) {
            sendMsg(player, "<red>An error occurred: Invalid reward level.</red>");
            return false;
        }

        if (levelToClaim != data.lastClaimedLevel() + 1) {
            if (levelToClaim <= data.lastClaimedLevel()) {
                sendMsg(player, "<red>You have already claimed this reward.</red>");
            } else {
                sendMsg(player, "<red>You must claim previous levels first!</red>");
            }
            return false;
        }

        if (data.totalPlaytimeSeconds() < levelInfo.timeRequiredSeconds()) {
            long remaining = levelInfo.timeRequiredSeconds() - data.totalPlaytimeSeconds();
            sendMsg(player, "<red>You have not unlocked this level yet. Time remaining: " + formatPlaytime(remaining) + "</red>");
            return false;
        }

        economy.depositPlayer(player, levelInfo.coinReward());
        tokenManager.addTokens(uuid, levelInfo.tokenReward());

        PlaytimeRewardDatabase.PlayerPlaytimeData newData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                data.totalPlaytimeSeconds(),
                levelToClaim,
                data.notifiedLevel()
        );
        playerDataCache.put(uuid, newData);

        database.savePlayerPlaytimeData(uuid, newData.totalPlaytimeSeconds(), newData.lastClaimedLevel(), newData.notifiedLevel());

        String message = "<green>You claimed reward for Level " + levelInfo.level() + "!</green><newline>" +
                "<gray>+ <white>" + levelInfo.coinReward() + " Coins</white></gray><newline>" +
                "<gray>+ <white>" + levelInfo.tokenReward() + " Tokens</white></gray>";
        sendMsg(player, message);

        logger.logClaim(player.getName(), levelInfo.level(), (int) levelInfo.coinReward(), levelInfo.tokenReward());

        return true;
    }

    public String formatPlaytime(long totalSeconds) {
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        return String.format("%dh %dm", hours, minutes);
    }

    private void sendMsg(Player player, String message) {
        // Combine our safe prefix with the message
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(message)));
    }

    public Component getPrefix() { return serverPrefix; }
    public MiniMessage getMiniMessage() { return miniMessage; }
}