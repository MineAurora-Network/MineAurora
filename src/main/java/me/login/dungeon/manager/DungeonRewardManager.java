package me.login.dungeon.manager;

import me.login.Login;
import me.login.dungeon.data.Database;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DungeonRewardManager {

    private final Login plugin;
    private final Database database;
    private final List<RewardItem> rewards = new ArrayList<>();
    private final Map<UUID, PlayerStats> playerStats = new HashMap<>();
    private final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public DungeonRewardManager(Login plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        createTables();
        loadRewards();
    }

    private void createTables() {
        try {
            database.getConnection().createStatement().execute("CREATE TABLE IF NOT EXISTS dungeon_player_stats (" +
                    "uuid VARCHAR(36) PRIMARY KEY, " +
                    "runs INTEGER DEFAULT 0, " +
                    "selected_drop VARCHAR(64)" +
                    ")");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void loadRewards() {
        rewards.clear();
        File file = new File(plugin.getDataFolder(), "items.yml");
        if (!file.exists()) plugin.saveResource("items.yml", false);

        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = config.getConfigurationSection("dungeon_rewards");

        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection itemSec = section.getConfigurationSection(key);
            if (itemSec == null) continue;

            String matName = itemSec.getString("material", "STONE");
            double chance = itemSec.getDouble("chance", 0.0);
            String rarity = itemSec.getString("rarity", "COMMON");
            String name = itemSec.getString("name", "&fUnknown");
            List<String> lore = itemSec.getStringList("lore");
            int amount = itemSec.getInt("amount", 1);
            String texture = itemSec.getString("texture", null);

            ItemStack stack;

            if (matName.equalsIgnoreCase("PLAYER_HEAD") && texture != null && !texture.isEmpty()) {
                stack = TextureToHead.getHead(texture);
            } else {
                Material mat = Material.getMaterial(matName.toUpperCase());
                if (mat == null) mat = Material.STONE;
                stack = new ItemStack(mat);
            }

            stack.setAmount(amount);
            ItemMeta meta = stack.getItemMeta();

            // Remove italics from name
            meta.displayName(LEGACY.deserialize(name).decoration(TextDecoration.ITALIC, false));

            List<Component> finalLore = new ArrayList<>();
            for (String l : lore) {
                // Remove italics from lore lines
                finalLore.add(LEGACY.deserialize(l).decoration(TextDecoration.ITALIC, false));
            }
            finalLore.add(Component.empty());
            finalLore.add(getRarityComponent(rarity));

            meta.lore(finalLore);

            List<String> enchants = itemSec.getStringList("enchantments");
            for (String enchLine : enchants) {
                String[] parts = enchLine.split(":");
                Enchantment ench = Enchantment.getByName(parts[0]);
                if (ench != null) {
                    meta.addEnchant(ench, Integer.parseInt(parts[1]), true);
                }
            }

            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);

            // Store ID for RNG Meter tracking
            meta.getPersistentDataContainer().set(me.login.dungeon.gui.DungeonGUI.ITEM_ID_KEY, PersistentDataType.STRING, key);

            stack.setItemMeta(meta);
            rewards.add(new RewardItem(key, stack, chance, rarity));
        }
        plugin.getLogger().info("Loaded " + rewards.size() + " dungeon rewards.");
    }

    private Component getRarityComponent(String rarity) {
        String color = switch (rarity.toUpperCase()) {
            case "COMMON" -> "&f&l";
            case "UNCOMMON" -> "&a&l";
            case "RARE" -> "&9&l";
            case "EPIC" -> "&5&l";
            case "LEGENDARY" -> "&6&l";
            case "MYTHIC" -> "&d&l";
            case "OMEGA" -> "&c&l";
            default -> "&7&l";
        };
        // Remove italics from rarity tag
        return LEGACY.deserialize(color + rarity.toUpperCase()).decoration(TextDecoration.ITALIC, false);
    }

    public List<ItemStack> generateChestRewards(UUID playerId) {
        List<ItemStack> chestLoot = new ArrayList<>();
        PlayerStats stats = getPlayerStats(playerId);

        for (RewardItem reward : rewards) {
            double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
            double finalChance = reward.chance;

            if (stats.selected_drop != null && stats.selected_drop.equalsIgnoreCase(reward.id)) {
                finalChance *= 3.0;
            }

            if (roll <= finalChance) {
                chestLoot.add(reward.stack.clone());
            }
        }

        // Guarantee at least one item if empty
        if (chestLoot.isEmpty() && !rewards.isEmpty()) {
            chestLoot.add(rewards.get(0).stack.clone());
        }

        Collections.shuffle(chestLoot);

        return chestLoot;
    }

    public PlayerStats getPlayerStats(UUID uuid) {
        if (playerStats.containsKey(uuid)) return playerStats.get(uuid);

        try (PreparedStatement ps = database.getConnection().prepareStatement("SELECT * FROM dungeon_player_stats WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                PlayerStats stats = new PlayerStats(rs.getInt("runs"), rs.getString("selected_drop"));
                playerStats.put(uuid, stats);
                return stats;
            }
        } catch (SQLException e) { e.printStackTrace(); }

        return new PlayerStats(0, null);
    }

    public void addRun(UUID uuid) {
        PlayerStats stats = getPlayerStats(uuid);
        stats.runs++;
        playerStats.put(uuid, stats);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (PreparedStatement ps = database.getConnection().prepareStatement("INSERT OR REPLACE INTO dungeon_player_stats (uuid, runs, selected_drop) VALUES (?, ?, ?)")) {
                ps.setString(1, uuid.toString());
                ps.setInt(2, stats.runs);
                ps.setString(3, stats.selected_drop);
                ps.executeUpdate();
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void setSelectedDrop(UUID uuid, String dropId) {
        PlayerStats stats = getPlayerStats(uuid);
        stats.selected_drop = dropId;
        playerStats.put(uuid, stats);
        addRun(uuid);
    }

    public List<RewardItem> getAllRewards() {
        return rewards;
    }

    public static class RewardItem {
        public String id;
        public ItemStack stack;
        public double chance;
        public String rarity;

        public RewardItem(String id, ItemStack stack, double chance, String rarity) {
            this.id = id;
            this.stack = stack;
            this.chance = chance;
            this.rarity = rarity;
        }
    }

    public static class PlayerStats {
        public int runs;
        public String selected_drop;

        public PlayerStats(int runs, String selected_drop) {
            this.runs = runs;
            this.selected_drop = selected_drop;
        }
    }
}