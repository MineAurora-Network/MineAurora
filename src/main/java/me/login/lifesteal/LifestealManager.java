package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class LifestealManager {

    private final Login plugin;
    private final ItemManager itemManager;
    private final DatabaseManager databaseManager;
    private LifestealLogger logger;

    private final Map<UUID, Integer> heartCache = new HashMap<>();
    private final Map<UUID, Integer> prestigeCache = new HashMap<>();

    private final int BASE_MAX_HEARTS = 25; // Default max hearts without prestige
    private final int MIN_HEARTS = 1;
    public final int DEFAULT_HEARTS = 10;

    private final Set<String> lifestealWorlds = Set.of(
            "lifesteal",
            "normal_world",
            "end",
            "nether",
            "dungeon",
            "arena"
    );

    public LifestealManager(Login plugin, ItemManager itemManager, DatabaseManager databaseManager, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.databaseManager = databaseManager;
        this.logger = logger;
    }

    public void loadPlayerData(Player player) {
        UUID uuid = player.getUniqueId();

        // Fetch Hearts
        databaseManager.getHearts(uuid, (hearts) -> {
            if (player.isOnline()) {
                heartCache.put(uuid, hearts);
                updatePlayerHealth(player);
            }
        });

        // Fetch Prestige
        databaseManager.getPrestigeLevel(uuid, (level) -> {
            if (player.isOnline()) {
                prestigeCache.put(uuid, level);
                updatePlayerHealth(player); // Update again to adjust max health based on prestige
            }
        });
    }

    public void savePlayerData(Player player) {
        UUID uuid = player.getUniqueId();
        if (heartCache.containsKey(uuid)) {
            databaseManager.setHearts(uuid, heartCache.get(uuid));
            heartCache.remove(uuid);
        }
        if (prestigeCache.containsKey(uuid)) {
            prestigeCache.remove(uuid);
        }
    }

    public void saveAllOnlinePlayerData() {
        plugin.getLogger().info("Saving all online player lifesteal data...");
        for (Map.Entry<UUID, Integer> entry : heartCache.entrySet()) {
            databaseManager.setHearts(entry.getKey(), entry.getValue());
        }
    }

    // --- PRESTIGE LOGIC ---
    public int getPrestigeLevel(UUID uuid) {
        return prestigeCache.getOrDefault(uuid, 0);
    }

    public void setPrestigeLevel(UUID uuid, int level) {
        prestigeCache.put(uuid, level);
        databaseManager.setPrestigeLevel(uuid, level);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            updatePlayerHealth(player);
        }
    }

    public int getMaxHearts(UUID uuid) {
        // Base 25 + Prestige Level (1 heart per prestige level)
        return BASE_MAX_HEARTS + getPrestigeLevel(uuid);
    }
    // ----------------------

    public int getHearts(UUID uuid) {
        return heartCache.getOrDefault(uuid, databaseManager.getHeartsSync(uuid));
    }

    public int setHearts(UUID uuid, int hearts) {
        int max = getMaxHearts(uuid); // Dynamic max
        // Ensure hearts don't exceed the calculated max
        int clampedHearts = Math.max(MIN_HEARTS, Math.min(max, hearts));

        heartCache.put(uuid, clampedHearts);
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

    public void updatePlayerHealth(Player player) {
        String worldName = player.getWorld().getName();
        int hearts;

        if (lifestealWorlds.contains(worldName)) {
            hearts = getHearts(player.getUniqueId());
        } else {
            hearts = DEFAULT_HEARTS;
        }

        // Calculate standard max health based on prestige
        int maxHeartsLimit = getMaxHearts(player.getUniqueId());

        // --- FIX FOR OP BYPASS PERSISTENCE ---
        // If the player currently has MORE hearts than the standard limit (e.g. from OP bypass),
        // we allow the limit to stretch to their current heart count.
        // This ensures that when they switch worlds, the attribute isn't clamped down to the prestige limit.
        if (hearts > maxHeartsLimit) {
            maxHeartsLimit = hearts;
        }
        // -------------------------------------

        // Safety check to ensure Attribute isn't null
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            // Set the container limit (Base Value)
            maxHealthAttr.setBaseValue(maxHeartsLimit * 2.0);
        }

        // Ensure current hearts doesn't exceed the limit visually
        if (hearts > maxHeartsLimit) {
            hearts = maxHeartsLimit;
        }

        // Set actual health logic
        double maxHealthVal = maxHeartsLimit * 2.0;
        if (player.getHealth() > maxHealthVal) {
            player.setHealth(maxHealthVal);
        }
    }

    public int getMaxHearts() { return BASE_MAX_HEARTS; }
    public int getMinHearts() { return MIN_HEARTS; }

    public Set<String> getLifestealWorlds() {
        return lifestealWorlds;
    }

    public boolean withdrawHearts(Player player, int amount) {
        if (amount <= 0) return false;

        int currentHearts = getHearts(player.getUniqueId());
        if ((currentHearts - amount) < MIN_HEARTS) {
            Component message = Component.text("You don't have enough hearts to withdraw!", NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text("You need to keep at least " + MIN_HEARTS + " heart(s).", NamedTextColor.RED));
            player.sendMessage(itemManager.formatMessage(message));
            return false;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(itemManager.formatMessage("<red>You don't have space in your inventory!"));
            return false;
        }

        setHearts(player.getUniqueId(), currentHearts - amount);
        player.getInventory().addItem(itemManager.getHeartItem(amount));
        player.sendMessage(itemManager.formatMessage("<green>You withdrew " + amount + " heart(s)."));

        if (logger != null) {
            logger.logNormal("Player `" + player.getName() + "` withdrew " + amount + " heart(s).");
        }
        return true;
    }

    public boolean useHeart(Player player) {
        int currentHearts = getHearts(player.getUniqueId());
        int max = getMaxHearts(player.getUniqueId());

        if (currentHearts >= max && !player.isOp()) {
            player.sendMessage(itemManager.formatMessage("<red>You are already at the maximum heart limit (" + max + ")!"));
            return false;
        }

        // OP Bypass Logic
        if (player.isOp() && currentHearts >= max) {
            // Manually update cache and DB to bypass the clamp in setHearts()
            int newAmount = currentHearts + 1;
            heartCache.put(player.getUniqueId(), newAmount);
            databaseManager.setHearts(player.getUniqueId(), newAmount);

            // Update attribute to accommodate the extra heart
            // Note: updatePlayerHealth(player) is better here to keep logic centralized,
            // now that updatePlayerHealth handles the bypass correctly.
            updatePlayerHealth(player);

            player.sendMessage(itemManager.formatMessage("<green>You redeemed a heart (OP Bypass)! New total: " + newAmount));
            return true;
        }

        setHearts(player.getUniqueId(), currentHearts + 1);
        player.sendMessage(itemManager.formatMessage("<green>You redeemed a heart! New total: " + (currentHearts + 1)));

        if (logger != null) {
            logger.logNormal("Player `" + player.getName() + "` redeemed a heart.");
        }
        return true;
    }
}