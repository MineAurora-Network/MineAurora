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
    public final int DEFAULT_HEARTS = 10; // 10 Hearts = 20 HP (Normal Vanilla)

    // Worlds where Lifesteal logic applies
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

    /**
     * Sets the player's hearts.
     * @param uuid Player UUID
     * @param hearts Amount of hearts
     * @param ignoreLimit If true, allows setting hearts ABOVE the calculated max limit (Operator Bypass).
     * @return The actual amount of hearts set (after potential clamping).
     */
    public int setHearts(UUID uuid, int hearts, boolean ignoreLimit) {
        int max = getMaxHearts(uuid); // Dynamic max based on Prestige
        int clampedHearts;

        if (ignoreLimit) {
            // If ignoring limit (OP/Admin), we only ensure they don't drop below minimum
            clampedHearts = Math.max(MIN_HEARTS, hearts);
        } else {
            // Standard gameplay logic: Clamp between Min and Max
            clampedHearts = Math.max(MIN_HEARTS, Math.min(max, hearts));
        }

        heartCache.put(uuid, clampedHearts);
        databaseManager.setHearts(uuid, clampedHearts);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null && player.isOnline()) {
            updatePlayerHealth(player);
        }
        return clampedHearts;
    }

    // Default overload for gameplay (kills, etc.) which respects limits
    public int setHearts(UUID uuid, int hearts) {
        return setHearts(uuid, hearts, false);
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

        // Sync Logic: Only use Lifesteal hearts in specific worlds
        if (lifestealWorlds.contains(worldName)) {
            hearts = getHearts(player.getUniqueId());
        } else {
            // In other worlds, revert to Normal/Vanilla hearts (10 Hearts / 20 HP)
            hearts = DEFAULT_HEARTS;
        }

        // Calculate standard max health based on prestige
        int maxHeartsLimit = getMaxHearts(player.getUniqueId());

        // --- FIX FOR OP BYPASS PERSISTENCE ---
        // If the player currently has MORE hearts than the standard limit (e.g. from OP bypass or Admin set),
        // we allow the limit to stretch to their current heart count.
        // This ensures the attribute isn't clamped down, preventing the "denying hearts" bug.
        if (hearts > maxHeartsLimit) {
            maxHeartsLimit = hearts;
        }
        // -------------------------------------

        // Safety check to ensure Attribute isn't null
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            // Set the container limit (Base Value)
            // If in non-lifesteal world, hearts is 10, so maxHeartsLimit (if lower) won't matter much
            // as we want to force 20.0 HP.
            // But usually, maxHeartsLimit is at least 25.
            // If in non-lifesteal world, we just set the attribute to match the hearts we want (10).
            if (!lifestealWorlds.contains(worldName)) {
                maxHealthAttr.setBaseValue(DEFAULT_HEARTS * 2.0);
            } else {
                maxHealthAttr.setBaseValue(maxHeartsLimit * 2.0);
            }
        }

        // Ensure current hearts doesn't exceed the limit visually
        // (This is redundant if we adjusted maxHeartsLimit above, but good for safety)
        if (hearts > maxHeartsLimit) {
            hearts = maxHeartsLimit;
        }

        // Apply the health
        double maxHealthVal = (lifestealWorlds.contains(worldName)) ? maxHeartsLimit * 2.0 : DEFAULT_HEARTS * 2.0;

        // Ensure player is alive before setting health
        if (!player.isDead()) {
            // If health is higher than new max, clamp it down.
            // If it is lower, we don't usually heal them automatically to prevent abuse,
            // BUT if we are switching worlds, we might want to ensure consistency.
            // For now, standard Bukkit behavior:
            if (player.getHealth() > maxHealthVal) {
                player.setHealth(maxHealthVal);
            }
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

        // Withdraw uses standard setHearts (respects limits, though we checked min above)
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

        // Check if player is at max AND is NOT an operator
        if (currentHearts >= max && !player.isOp()) {
            player.sendMessage(itemManager.formatMessage("<red>You are already at the maximum heart limit (" + max + ")!"));
            return false;
        }

        // OP Bypass Logic & Normal Redeem
        // If player is OP, ignoreLimit = true. If not, ignoreLimit = false.
        setHearts(player.getUniqueId(), currentHearts + 1, player.isOp());

        if (player.isOp() && currentHearts >= max) {
            player.sendMessage(itemManager.formatMessage("<green>You redeemed a heart (OP Bypass)! New total: " + (currentHearts + 1)));
        } else {
            player.sendMessage(itemManager.formatMessage("<green>You redeemed a heart! New total: " + (currentHearts + 1)));
        }

        if (logger != null) {
            logger.logNormal("Player `" + player.getName() + "` redeemed a heart.");
        }
        return true;
    }
}