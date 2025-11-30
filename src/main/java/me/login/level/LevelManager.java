package me.login.level;

import me.login.Login;
import me.login.lifesteal.ItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LevelManager {

    private final Login plugin;
    private final LevelDatabase database;
    private final LevelLogger logger;
    private final MiniMessage mm;
    private final LuckPerms luckPerms;

    private final Map<UUID, Integer> levelCache = new HashMap<>();
    private final Map<UUID, Integer> xpCache = new HashMap<>();

    private final int MAX_LEVEL = 130;
    private final int BASE_XP_REQ = 100;
    private final int XP_INCREMENT = 50;

    public LevelManager(Login plugin, LevelDatabase database, LevelLogger logger, LuckPerms luckPerms) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.luckPerms = luckPerms;
        this.mm = MiniMessage.miniMessage();
    }

    // --- XP & Level Logic ---

    public int getXpRequiredForNextLevel(int currentLevel) {
        return BASE_XP_REQ + (currentLevel * XP_INCREMENT);
    }

    public void addXp(Player player, int baseAmount, String source) {
        if (getLevel(player) >= MAX_LEVEL) return;

        double multiplier = getMultiplier(player);
        int finalAmount = (int) Math.round(baseAmount * multiplier);
        if (finalAmount <= 0) finalAmount = 1;

        UUID uuid = player.getUniqueId();
        int currentXp = xpCache.getOrDefault(uuid, 0);
        int currentLevel = levelCache.getOrDefault(uuid, 0);
        int xpNeeded = getXpRequiredForNextLevel(currentLevel);

        int newXp = currentXp + finalAmount;

        // Check for Level Up
        if (newXp >= xpNeeded) {
            newXp -= xpNeeded;
            int newLevel = currentLevel + 1;
            setLevel(player, newLevel);
            setXp(player, newXp);

            // Level Up Effects
            handleLevelUp(player, newLevel);
        } else {
            setXp(player, newXp);
        }
    }

    public void removeXp(Player player, int amount) {
        UUID uuid = player.getUniqueId();
        int currentXp = xpCache.getOrDefault(uuid, 0);
        int newXp = currentXp - amount;

        if (newXp < 0) newXp = 0;

        setXp(player, newXp);
    }

    private void handleLevelUp(Player player, int newLevel) {
        String legacyPrefix = plugin.getConfig().getString("server_prefix_2", "&cServer: ");

        Component mainTitle = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyPrefix + "&aLevel Up!");
        Component subTitle = mm.deserialize("<gray>You reached <gold>Level " + newLevel + "</gold>!");

        Title title = Title.title(mainTitle, subTitle, Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000)));
        player.showTitle(title);

        player.sendMessage(ItemManager.fromLegacy(legacyPrefix + " &aYou are now Lifesteal Level &e" + newLevel + "&a!"));
        player.sendMessage(ItemManager.fromLegacy(legacyPrefix + " &7Next Level Requirement: &b" + getXpRequiredForNextLevel(newLevel) + " XP"));

        updateTabName(player);
        logger.logLevelUp(player.getName(), newLevel);

        saveData(player.getUniqueId());
    }

    // --- Data Handling ---

    public void loadData(UUID uuid) {
        database.loadPlayerData(uuid, (level, xp) -> {
            levelCache.put(uuid, level);
            xpCache.put(uuid, xp);
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) updateTabName(p);
        });
    }

    public void saveData(UUID uuid) {
        if (levelCache.containsKey(uuid)) {
            database.savePlayerData(uuid, levelCache.get(uuid), xpCache.get(uuid));
        }
    }

    public void unloadData(UUID uuid) {
        saveData(uuid);
        levelCache.remove(uuid);
        xpCache.remove(uuid);
    }

    public int getLevel(Player player) {
        return getLevel(player.getUniqueId());
    }

    public int getLevel(UUID uuid) {
        return levelCache.getOrDefault(uuid, 0);
    }

    public int getXp(Player player) {
        return xpCache.getOrDefault(player.getUniqueId(), 0);
    }

    public void setLevel(Player player, int level) {
        if (level > MAX_LEVEL) level = MAX_LEVEL;
        if (level < 0) level = 0;
        levelCache.put(player.getUniqueId(), level);
        updateTabName(player);
    }

    public void setXp(Player player, int xp) {
        xpCache.put(player.getUniqueId(), xp);
    }

    // --- Utilities ---

    private double getMultiplier(Player player) {
        double multiplier = 1.0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            String perm = info.getPermission();
            if (perm.startsWith("lifesteallevel.multiplier.")) {
                try {
                    String val = perm.substring("lifesteallevel.multiplier.".length());
                    double found = Double.parseDouble(val);
                    if (found > multiplier) multiplier = found;
                } catch (NumberFormatException ignored) {}
            }
        }
        return multiplier;
    }

    // Format: %player's_rank% %playername% %player_level%
    public void updateTabName(Player player) {
        Component rankPrefix = getRankPrefixComponent(player);
        Component levelSuffix = getLevelPrefixComponent(player); // Reusing the "[Lvl]" component as suffix logic for Tab

        // We construct: Rank + Space + Name + Space + Level
        Component tabName = rankPrefix
                .append(Component.space())
                .append(player.displayName())
                .append(Component.space())
                .append(levelSuffix);

        player.playerListName(tabName);
    }

    public Component getLevelPrefixComponent(Player player) {
        int level = getLevel(player);
        String color = getLevelColor(level);
        // Chat Format logic uses this: %player_level% (e.g. "[10]")
        return LegacyComponentSerializer.legacyAmpersand().deserialize("&8[" + color + level + "&8]");
    }

    public Component getRankPrefixComponent(Player player) {
        if (luckPerms == null) return Component.empty();
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return Component.empty();

        String prefix = user.getCachedData().getMetaData().getPrefix();
        if (prefix == null) return Component.empty();

        return LegacyComponentSerializer.legacyAmpersand().deserialize(prefix);
    }

    public String getLevelColor(int level) {
        if (level <= 10) return "&f";
        if (level <= 20) return "&e"; // Yellow
        if (level <= 30) return "&a"; // Green
        if (level <= 40) return "&2"; // Dark Green
        if (level <= 50) return "&b"; // Aqua
        if (level <= 60) return "&3"; // Cyan
        if (level <= 70) return "&9"; // Blue
        if (level <= 80) return "&d"; // Pink
        if (level <= 90) return "&5"; // Purple
        if (level <= 100) return "&6"; // Gold
        if (level <= 115) return "&c"; // Red
        return "&2"; // Dark Green (116-130)
    }
}