package me.login.misc.milestones;

import me.login.Login;
import me.login.misc.tokens.TokenManager;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MilestoneManager {

    private final Login plugin;
    private final MilestoneDatabase database;
    private final MilestoneLogger logger;
    private final TokenManager tokenManager;

    // Cache
    private final Map<UUID, Integer> killStreaks = new ConcurrentHashMap<>();
    private final Map<UUID, Set<Integer>> claimedMilestones = new ConcurrentHashMap<>();

    // Config
    private final int[] requiredKills = {20, 30, 40, 50, 60, 70, 80}; // Slots 10-16
    private final int REWARD_TOKENS = 20;

    public MilestoneManager(Login plugin, MilestoneDatabase database, MilestoneLogger logger, TokenManager tokenManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.tokenManager = tokenManager;
    }

    public void loadDataCallback(UUID uuid, int streak, Set<Integer> claimed) {
        killStreaks.put(uuid, streak);
        claimedMilestones.put(uuid, claimed);
    }

    public void saveData(UUID uuid) {
        if (killStreaks.containsKey(uuid)) {
            database.savePlayerData(uuid, killStreaks.get(uuid), claimedMilestones.getOrDefault(uuid, new HashSet<>()));
        }
        killStreaks.remove(uuid);
        claimedMilestones.remove(uuid);
    }

    public int getStreak(UUID uuid) {
        return killStreaks.getOrDefault(uuid, 0);
    }

    public void incrementStreak(Player player) {
        UUID uuid = player.getUniqueId();
        int newStreak = getStreak(uuid) + 1;
        killStreaks.put(uuid, newStreak);
        // We save async on quit, but if you want instant save, uncomment below:
        // database.savePlayerData(uuid, newStreak, claimedMilestones.getOrDefault(uuid, new HashSet<>()));
    }

    public void resetStreak(Player player) {
        UUID uuid = player.getUniqueId();
        int oldStreak = getStreak(uuid);
        if (oldStreak > 0) {
            killStreaks.put(uuid, 0);
            player.sendMessage(plugin.getComponentSerializer().deserialize(
                    plugin.getServerPrefix() + "<red>Your kill streak of <yellow>" + oldStreak + "</yellow> has ended!</red>"
            ));
        }
    }

    public boolean isClaimed(UUID uuid, int milestoneIndex) {
        return claimedMilestones.getOrDefault(uuid, Collections.emptySet()).contains(milestoneIndex);
    }

    public boolean canClaim(UUID uuid, int milestoneIndex) {
        if (isClaimed(uuid, milestoneIndex)) return false;
        int req = getRequiredKills(milestoneIndex);
        return getStreak(uuid) >= req;
    }

    public int getRequiredKills(int milestoneIndex) {
        if (milestoneIndex < 0 || milestoneIndex >= requiredKills.length) return 9999;
        return requiredKills[milestoneIndex];
    }

    public void claimMilestone(Player player, int milestoneIndex) {
        UUID uuid = player.getUniqueId();
        if (!canClaim(uuid, milestoneIndex)) {
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>You cannot claim this milestone yet!</red>"));
            return;
        }

        Set<Integer> claimed = claimedMilestones.computeIfAbsent(uuid, k -> new HashSet<>());
        claimed.add(milestoneIndex);

        // Give Reward
        tokenManager.addTokens(uuid, REWARD_TOKENS);

        String msg = plugin.getServerPrefix() + "<green>Claimed Milestone " + (milestoneIndex + 1) + "! Received <gold>" + REWARD_TOKENS + " Tokens</gold>.</green>";
        player.sendMessage(plugin.getComponentSerializer().deserialize(msg));

        // Log to Discord
        logger.logClaim(player.getName(), milestoneIndex + 1, REWARD_TOKENS, getStreak(uuid));

        // Save to DB
        database.savePlayerData(uuid, getStreak(uuid), claimed);
    }
}