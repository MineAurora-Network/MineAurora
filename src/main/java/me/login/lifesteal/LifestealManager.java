package me.login.lifesteal;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set; // <-- IMPORT ADDED
import java.util.UUID;

public class LifestealManager {

    private final Login plugin;
    private final ItemManager itemManager;
    private final DatabaseManager databaseManager;

    private final Map<UUID, Integer> heartCache = new HashMap<>();
    private final int MAX_HEARTS = 25;
    private final int MIN_HEARTS = 1;
    public final int DEFAULT_HEARTS = 10; // Made public for revive command

    // --- MODIFICATION (Request 2) ---
    private final Set<String> lifestealWorlds = Set.of(
            "lifesteal",
            "normal_world",
            "end",
            "nether",
            "arena"
    );
    // --- END MODIFICATION ---

    public LifestealManager(Login plugin, ItemManager itemManager, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.databaseManager = databaseManager;
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();

        // Asynchronously fetch hearts from DB
        databaseManager.getHearts(uuid, (hearts) -> {
            if (player.isOnline()) { // Check if player is still online
                heartCache.put(uuid, hearts);
                updatePlayerHealth(player);
            }
        });
    }

    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        if (heartCache.containsKey(uuid)) {
            // Asynchronously save to DB
            databaseManager.setHearts(uuid, heartCache.get(uuid));
            // Remove from cache
            heartCache.remove(uuid);
        }
        // --- MODIFICATION (Request 2) ---
        // Ensure player health is reset to default if they log out from a non-lifesteal world
        // (This is a safeguard, as updatePlayerHealth should handle it on next join)
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(DEFAULT_HEARTS * 2.0);
        // --- END MODIFICATION ---
    }

    public void saveAllOnlinePlayerData() {
        plugin.getLogger().info("Saving all online player lifesteal data to database...");
        // This will save all cached (online) players to the DB
        for (Map.Entry<UUID, Integer> entry : heartCache.entrySet()) {
            databaseManager.setHearts(entry.getKey(), entry.getValue());
        }
        plugin.getLogger().info("Lifesteal data save complete.");
    }

    public int getHearts(UUID uuid) {
        // For online players, this will be in the cache
        // For offline players, this will hit the DB synchronously
        return heartCache.getOrDefault(uuid,
                databaseManager.getHeartsSync(uuid) // Sync call for commands
        );
    }

    /**
     * Sets a player's hearts. Clamps between MIN and MAX.
     * @param uuid The player's UUID
     * @param hearts The amount to set
     * @return The actual amount of hearts set after clamping
     */
    public int setHearts(UUID uuid, int hearts) {
        int clampedHearts = Math.max(MIN_HEARTS, Math.min(MAX_HEARTS, hearts));

        // Update cache
        heartCache.put(uuid, clampedHearts);

        // Asynchronously update database
        databaseManager.setHearts(uuid, clampedHearts);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updatePlayerHealth(player);
        }
        return clampedHearts;
    }

    public int addHearts(UUID uuid, int amount) {
        int currentHearts = getHearts(uuid);
        return setHearts(uuid, currentHearts + amount);
    }

    public int removeHearts(UUID uuid, int amount) {
        int currentHearts = getHearts(uuid);
        return setHearts(uuid, currentHearts - amount);
    }

    // --- MODIFICATION (Request 2) ---
    public void updatePlayerHealth(Player player) {
        String worldName = player.getWorld().getName();
        int hearts;

        if (lifestealWorlds.contains(worldName)) {
            // Player is in a lifesteal world, use their stored hearts
            hearts = getHearts(player.getUniqueId());
        } else {
            // Player is in a non-lifesteal world, use default hearts
            hearts = DEFAULT_HEARTS;
        }

        // Max health is hearts * 2
        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(hearts * 2.0);

        // Heal them if their health is lower than new max
        if (player.getHealth() < (hearts * 2.0)) {
            player.setHealth(hearts * 2.0);
        }
    }
    // --- END MODIFICATION ---

    public int getMaxHearts() {
        return MAX_HEARTS;
    }

    // --- FIX ---
    public int getMinHearts() {
        return MIN_HEARTS;
    }
    // --- END FIX ---

    public boolean withdrawHearts(Player player, int amount) {
        if (amount <= 0) return false;

        int currentHearts = getHearts(player.getUniqueId());
        if ((currentHearts - amount) < MIN_HEARTS) {
            player.sendMessage(itemManager.formatMessage("<red>You don't have enough hearts to withdraw!"));
            player.sendMessage(itemManager.formatMessage("<red>You need to keep at least " + MIN_HEARTS + " heart(s)."));
            return false;
        }

        // Check for inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(itemManager.formatMessage("<red>You don't have space in your inventory!"));
            return false;
        }

        // All checks passed
        setHearts(player.getUniqueId(), currentHearts - amount);
        player.getInventory().addItem(itemManager.getHeartItem(amount));
        player.sendMessage(itemManager.formatMessage("<green>You withdrew " + amount + " heart(s)."));
        itemManager.sendLog(player.getName() + " withdrew " + amount + " heart(s).");
        return true;
    }

    public boolean useHeart(Player player) {
        int currentHearts = getHearts(player.getUniqueId());
        if (currentHearts >= MAX_HEARTS) {
            player.sendMessage(itemManager.formatMessage("<red>You are already at the maximum heart limit!"));
            return false;
        }

        setHearts(player.getUniqueId(), currentHearts + 1);
        player.sendMessage(itemManager.formatMessage("<green>You redeemed a heart! New total: " + (currentHearts + 1)));
        itemManager.sendLog(player.getName() + " redeemed a heart.");
        return true;
    }
}