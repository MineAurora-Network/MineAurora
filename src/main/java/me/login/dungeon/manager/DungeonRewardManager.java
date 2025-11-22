package me.login.dungeon.manager;

import me.login.Login;
import me.login.dungeon.data.Database;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class DungeonRewardManager {

    private final Login plugin;
    private final Database database;
    private final Map<String, List<RewardItem>> rewards = new HashMap<>();

    // Cache: UUID -> {Runs, SelectedDropId}
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();

    public DungeonRewardManager(Login plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createStatsTable();
        loadRewards();
    }

    private void createStatsTable() {
        try (Connection conn = database.getConnection();
             java.sql.Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE IF NOT EXISTS dungeon_player_stats (" +
                    "uuid TEXT PRIMARY KEY, " +
                    "runs INTEGER DEFAULT 0, " +
                    "selected_drop TEXT" +
                    ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void loadRewards() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) return;

        YamlConfiguration config = YamlConfiguration.loadConfiguration(itemsFile);
        ConfigurationSection section = config.getConfigurationSection("dungeon_rewards");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            // key = common, rare, etc.
            List<RewardItem> items = new ArrayList<>();
            ConfigurationSection tierSection = section.getConfigurationSection(key);
            if (tierSection != null) {
                for (String itemId : tierSection.getKeys(false)) {
                    ItemStack stack = config.getItemStack("dungeon_rewards." + key + "." + itemId);
                    if (stack != null) {
                        items.add(new RewardItem(itemId, stack, key));
                    }
                }
            }
            rewards.put(key.toLowerCase(), items);
        }
        plugin.getLogger().info("Loaded dungeon rewards: " + rewards.size() + " categories.");
    }

    public List<ItemStack> generateChestRewards(UUID playerId) {
        List<ItemStack> loot = new ArrayList<>();
        PlayerStats stats = getPlayerStats(playerId);

        // Logic: 3 slots.
        // Slot 1: High chance Common/Rare
        loot.add(rollItem("common", "rare"));
        // Slot 2: Rare/Epic
        loot.add(rollItem("rare", "epic"));
        // Slot 3: High tier roll (Legendary/Mythic/Omega) based on runs

        String tier = "legendary";
        if (stats.runs >= 35) tier = "omega";
        else if (stats.runs >= 20) tier = "mythic";

        // 50% chance to downgrade tier if runs aren't maxed
        if (Math.random() > 0.5 && !tier.equals("common")) {
            tier = "epic"; // simplified fallback
        }

        loot.add(rollItem(tier, "common")); // fallback to common if empty

        return loot;
    }

    private ItemStack rollItem(String tier, String fallback) {
        List<RewardItem> pool = rewards.getOrDefault(tier, rewards.get(fallback));
        if (pool == null || pool.isEmpty()) return new ItemStack(Material.POTATO);
        return pool.get(new Random().nextInt(pool.size())).stack.clone();
    }

    public List<RewardItem> getAllRewards() {
        List<RewardItem> all = new ArrayList<>();
        rewards.values().forEach(all::addAll);
        return all;
    }

    // --- Stats Logic ---

    public PlayerStats getPlayerStats(UUID uuid) {
        if (playerStats.containsKey(uuid)) return playerStats.get(uuid);

        // Load async recommended, but for simplicity we cache on join/first access
        try (Connection conn = database.getConnection();
             PreparedStatement ps = conn.prepareStatement("SELECT * FROM dungeon_player_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerStats s = new PlayerStats(rs.getInt("runs"), rs.getString("selected_drop"));
                playerStats.put(uuid, s);
                return s;
            }
        } catch (SQLException e) { e.printStackTrace(); }

        PlayerStats newStats = new PlayerStats(0, null);
        playerStats.put(uuid, newStats);
        return newStats;
    }

    public void addRun(UUID uuid) {
        PlayerStats s = getPlayerStats(uuid);
        s.runs++;
        saveStats(uuid, s);
    }

    public void setSelectedDrop(UUID uuid, String dropId) {
        PlayerStats s = getPlayerStats(uuid);
        s.selected_drop = dropId;
        saveStats(uuid, s);
    }

    private void saveStats(UUID uuid, PlayerStats s) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = database.getConnection();
                 PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO dungeon_player_stats (uuid, runs, selected_drop) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, s.runs);
                ps.setString(3, s.selected_drop);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public static class PlayerStats {
        public int runs;
        public String selected_drop;
        public PlayerStats(int r, String s) { runs = r; selected_drop = s; }
    }

    public static class RewardItem {
        public String id;
        public ItemStack stack;
        public String rarity;
        public RewardItem(String id, ItemStack s, String r) { this.id = id; this.stack = s; this.rarity = r; }
    }
}