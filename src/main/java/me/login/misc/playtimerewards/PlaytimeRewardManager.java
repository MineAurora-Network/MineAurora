package me.login.misc.playtimerewards;

import me.login.Login;
import me.login.misc.dailyreward.DailyRewardDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
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
    private final DailyRewardDatabase dailyRewardDatabase; // For giving tokens
    private final Component serverPrefix;
    private final MiniMessage miniMessage;

    // Caches
    private final List<PlaytimeRewardLevel> rewardLevels;
    private final Map<UUID, PlaytimeRewardDatabase.PlayerPlaytimeData> playerDataCache = new ConcurrentHashMap<>();

    // --- UPDATED to 84 levels (4 pages * 21 levels/page) ---
    public static final int MAX_LEVEL = 84;
    private static final int SAVE_INTERVAL_SECONDS = 60 * 5; // Save data every 5 minutes

    public PlaytimeRewardManager(Login plugin, PlaytimeRewardDatabase database, PlaytimeRewardLogger logger, Economy economy, DailyRewardDatabase dailyRewardDatabase) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.economy = economy;
        this.dailyRewardDatabase = dailyRewardDatabase;

        this.miniMessage = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        this.rewardLevels = new ArrayList<>(MAX_LEVEL);
        generateRewardLevels();

        startPlaytimeTracker();
    }

    /**
     * Pre-calculates all 84 reward levels and stores them.
     */
    private void generateRewardLevels() {
        long currentCoinReward = 0;
        long currentPlaytime = 0;

        // Levels 1-8 (3h gap, +400 coins, 1 token)
        for (int level = 1; level <= 8; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(3); // 3h gap
            currentCoinReward = (level == 1) ? 500 : currentCoinReward + 400; // Lvl 1 starts at 500, then +400
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 1));
        }

        // Levels 9-63 (5h gap, +750 coins, 2 tokens)
        for (int level = 9; level <= 63; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(5); // 5h gap
            currentCoinReward += 750; // +750 coins
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 2));
        }

        // Levels 64-84 (5h gap, +750 coins, 3 tokens)
        for (int level = 64; level <= MAX_LEVEL; level++) {
            currentPlaytime += TimeUnit.HOURS.toSeconds(5); // 5h gap
            currentCoinReward += 750; // +750 coins
            rewardLevels.add(new PlaytimeRewardLevel(level, currentPlaytime, currentCoinReward, 3));
        }
        plugin.getLogger().info("Generated " + rewardLevels.size() + " playtime reward levels.");
    }

    /**
     * Starts the repeating task to track and save playtime.
     */
    private void startPlaytimeTracker() {
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;

                // Every second, check for new reward eligibility
                for (Player player : Bukkit.getOnlinePlayers()) {
                    UUID uuid = player.getUniqueId();
                    PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.get(uuid);
                    if (data == null) continue; // Data not loaded yet

                    // Increment playtime by 1 second
                    long newPlaytime = data.totalPlaytimeSeconds() + 1;

                    // Update cache immediately
                    data = new PlaytimeRewardDatabase.PlayerPlaytimeData(newPlaytime, data.lastClaimedLevel(), data.notifiedLevel());
                    playerDataCache.put(uuid, data);

                    // Check if they are eligible for a notification
                    checkRewardEligibility(player, data);
                }

                // Every SAVE_INTERVAL_SECONDS (e.g., 5 mins), save data
                if (ticks >= (SAVE_INTERVAL_SECONDS * 20)) {
                    ticks = 0;
                    plugin.getLogger().info("Saving all online player playtime data...");
                    saveAllPlayerData();
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second (20 ticks)
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

        // We need to get their *total* playtime from statistics, not just what's in our DB.
        // Statistic.PLAY_ONE_MINUTE is in ticks (1/20th of a second)
        long statPlaytimeSeconds = player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20;

        database.getPlayerPlaytimeData(uuid).thenAccept(data -> {
            // Check if stat playtime is greater than saved playtime (e.g., data wipe)
            long finalPlaytime = Math.max(statPlaytimeSeconds, data.totalPlaytimeSeconds());

            PlaytimeRewardDatabase.PlayerPlaytimeData loadedData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                    finalPlaytime,
                    data.lastClaimedLevel(),
                    data.notifiedLevel()
            );
            playerDataCache.put(uuid, loadedData);

            // Check eligibility on join
            checkRewardEligibility(player, loadedData);
        });
    }

    public void unloadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.remove(uuid);
        if (data != null) {
            // Asynchronously save their final data
            database.savePlayerPlaytimeData(uuid, data.totalPlaytimeSeconds(), data.lastClaimedLevel(), data.notifiedLevel());
        }
    }

    public void saveAllPlayerData() {
        // Save data for all online players
        playerDataCache.forEach((uuid, data) -> {
            database.savePlayerPlaytimeData(uuid, data.totalPlaytimeSeconds(), data.lastClaimedLevel(), data.notifiedLevel());
        });
    }

    /**
     * Checks if a player has unlocked a new reward and notifies them.
     */
    private void checkRewardEligibility(Player player, PlaytimeRewardDatabase.PlayerPlaytimeData data) {
        int nextLevel = data.lastClaimedLevel() + 1;
        if (nextLevel > MAX_LEVEL) return; // Max level reached
        if (nextLevel <= data.notifiedLevel()) return; // Already notified for this level or higher

        PlaytimeRewardLevel levelInfo = getLevelInfo(nextLevel);
        if (levelInfo == null) return;

        // Check if playtime is sufficient
        if (data.totalPlaytimeSeconds() >= levelInfo.timeRequiredSeconds()) {
            // Unlocked a new level they haven't been notified for!
            sendMsg(player, "<green>You have unlocked Playtime Reward Level " + levelInfo.level() + "!</green>");
            sendMsg(player, "<gray>Type <white>/ptrewards</white> to claim it.</gray>");

            // Update cache with new notified level
            PlaytimeRewardDatabase.PlayerPlaytimeData newData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                    data.totalPlaytimeSeconds(),
                    data.lastClaimedLevel(),
                    nextLevel // We have now notified them for this level
            );
            playerDataCache.put(player.getUniqueId(), newData);

            // Save this new notification level to DB
            database.savePlayerPlaytimeData(player.getUniqueId(), newData.totalPlaytimeSeconds(), newData.lastClaimedLevel(), newData.notifiedLevel());
        }
    }

    /**
     * Gets the data for a specific level.
     * @param level The level number (1-148).
     * @return The Level info, or null if invalid.
     */
    public PlaytimeRewardLevel getLevelInfo(int level) {
        if (level < 1 || level > rewardLevels.size()) {
            return null;
        }
        return rewardLevels.get(level - 1);
    }

    /**
     * Gets the player's cached data.
     */
    public PlaytimeRewardDatabase.PlayerPlaytimeData getPlayerData(UUID uuid) {
        return playerDataCache.get(uuid);
    }

    /**
     * Attempts to claim a reward for the player.
     */
    public void claimReward(Player player, int levelToClaim) {
        UUID uuid = player.getUniqueId();
        PlaytimeRewardDatabase.PlayerPlaytimeData data = playerDataCache.get(uuid);

        if (data == null) {
            sendMsg(player, "<red>Your playtime data is not loaded. Please re-log.</red>");
            return;
        }

        PlaytimeRewardLevel levelInfo = getLevelInfo(levelToClaim);
        if (levelInfo == null) {
            sendMsg(player, "<red>An error occurred: Invalid reward level.</red>");
            return;
        }

        // 1. Check if this is the *next* level they should be claiming
        if (levelToClaim != data.lastClaimedLevel() + 1) {
            if (levelToClaim <= data.lastClaimedLevel()) {
                sendMsg(player, "<red>You have already claimed this reward.</red>");
            } else {
                sendMsg(player, "<red>You must claim previous levels first!</red>");
            }
            return;
        }

        // 2. Check if they have enough playtime
        if (data.totalPlaytimeSeconds() < levelInfo.timeRequiredSeconds()) {
            long remaining = levelInfo.timeRequiredSeconds() - data.totalPlaytimeSeconds();
            sendMsg(player, "<red>You have not unlocked this level yet. Time remaining: " + formatPlaytime(remaining) + "</red>");
            return;
        }

        // --- All checks passed! Grant reward ---

        // 1. Give rewards
        economy.depositPlayer(player, levelInfo.coinReward());
        dailyRewardDatabase.addTokens(uuid, levelInfo.tokenReward());

        // 2. Update data in cache
        PlaytimeRewardDatabase.PlayerPlaytimeData newData = new PlaytimeRewardDatabase.PlayerPlaytimeData(
                data.totalPlaytimeSeconds(),
                levelToClaim, // This is the new highest claimed level
                data.notifiedLevel()
        );
        playerDataCache.put(uuid, newData);

        // 3. Save to database (async)
        database.savePlayerPlaytimeData(uuid, newData.totalPlaytimeSeconds(), newData.lastClaimedLevel(), newData.notifiedLevel());

        // 4. Send messages and log
        sendMsg(player, "<green>You claimed reward for Level " + levelInfo.level() + "!</green>");
        sendMsg(player, "<gray>+ <white>" + levelInfo.coinReward() + " Coins</white></gray>");
        sendMsg(player, "<gray>+ <white>" + levelInfo.tokenReward() + " Tokens</white></gray>");

        logger.log("`" + player.getName() + "` claimed Playtime Level `" + levelInfo.level() + "` (`" +
                levelInfo.coinReward() + "` coins, `" + levelInfo.tokenReward() + "` tokens).");

        // 5. Check if they are *also* eligible for the *next* level immediately
        checkRewardEligibility(player, newData);
    }

    /**
     * Formats a duration in seconds to a "Xh Ym" string.
     */
    public String formatPlaytime(long totalSeconds) {
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        return String.format("%dh %dm", hours, minutes);
    }

    /**
     * Helper to send a prefixed message to a player.
     */
    private void sendMsg(Player player, String message) {
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(message)));
    }

    // --- ADDED PUBLIC GETTERS ---

    public Component getPrefix() {
        return serverPrefix;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}