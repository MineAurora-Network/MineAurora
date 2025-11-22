package me.login.leaderboards;

import me.clip.placeholderapi.PlaceholderAPI;
import me.login.Login;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;

public class LeaderboardDisplayManager {

    private final Login plugin;
    private final StatsFetcher fetcher;
    private final Map<UUID, LeaderboardInfo> activeLeaderboards = new HashMap<>();
    private File leaderboardsFile;
    private FileConfiguration leaderboardsConfig;
    private final boolean papiEnabled;
    private final MiniMessage miniMessage;

    public LeaderboardDisplayManager(Login plugin) {
        this.plugin = plugin;
        this.fetcher = new StatsFetcher(plugin);
        this.papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.miniMessage = MiniMessage.miniMessage();
        loadLeaderboards();
    }

    public void reloadConfigAndUpdateAll() {
        loadLeaderboards();
        updateAllDisplays();
    }

    public void createLeaderboard(Location location, String type, Player creator) {
        location.setPitch(0);
        TextDisplay textDisplay = spawnNewDisplay(location, type);
        if (textDisplay == null) {
            ((Audience) creator).sendMessage(miniMessage.deserialize("<red>Failed to spawn entity.</red>"));
            return;
        }

        LeaderboardInfo info = new LeaderboardInfo(
                type.toLowerCase(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
        activeLeaderboards.put(textDisplay.getUniqueId(), info);
        saveLeaderboards();

        ((Audience) creator).sendMessage(miniMessage.deserialize("<green>Leaderboard created.</green>"));
        updateDisplay(textDisplay.getUniqueId());
    }

    private TextDisplay spawnNewDisplay(Location location, String type) {
        if (location == null || location.getWorld() == null) return null;

        // Force Chunk Load
        Chunk chunk = location.getChunk();
        if (!chunk.isLoaded()) chunk.load();
        chunk.setForceLoaded(true);

        TextDisplay textDisplay = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        textDisplay.setCustomName("leaderboard");
        textDisplay.setCustomNameVisible(false);
        textDisplay.text(miniMessage.deserialize("<yellow>Loading " + type + "..."));
        textDisplay.setBillboard(Display.Billboard.VERTICAL);
        textDisplay.setViewRange(30);
        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setShadowed(true);
        textDisplay.setInvulnerable(true);
        return textDisplay;
    }

    public void updateAllDisplays() {
        if (activeLeaderboards.isEmpty()) return;
        new HashSet<>(activeLeaderboards.keySet()).forEach(this::updateDisplay);
    }

    private void updateDisplay(UUID uuid) {
        LeaderboardInfo info = activeLeaderboards.get(uuid);
        if (info == null) {
            activeLeaderboards.remove(uuid);
            saveLeaderboards();
            return;
        }
        String type = info.type();
        Location loc = info.getLocation();

        if (loc == null) return; // World not loaded

        // Ensure chunk is force loaded
        if (!loc.getChunk().isForceLoaded()) {
            loc.getChunk().load();
            loc.getChunk().setForceLoaded(true);
        }

        Entity entity = Bukkit.getEntity(uuid);
        TextDisplay textDisplay;

        if (entity == null || !entity.isValid() || !(entity instanceof TextDisplay)) {
            plugin.getLogger().info("Respawning leaderboard " + type);
            textDisplay = spawnNewDisplay(loc, type);
            if (textDisplay == null) return;
            activeLeaderboards.remove(uuid);
            activeLeaderboards.put(textDisplay.getUniqueId(), info);
            saveLeaderboards();
        } else {
            textDisplay = (TextDisplay) entity;
        }

        updateText(textDisplay, type);
    }

    private void updateText(TextDisplay display, String type) {
        // Handle "token" vs "tokens" mismatch by normalizing
        String configKey = type.equals("tokens") ? "token" : type;

        String titleFormat = plugin.getConfig().getString("leaderboards." + configKey + ".title", "<red>Title Missing (" + type + ")");
        String lineFormat = plugin.getConfig().getString("leaderboards." + configKey + ".line-format", "<white>%rank% %player% %value%");
        String emptyLineFormat = plugin.getConfig().getString("leaderboards." + configKey + ".empty-line-format", "<gray>Empty");
        String footerFormat = plugin.getConfig().getString("leaderboards." + configKey + ".footer", "");

        StringBuilder text = new StringBuilder();
        text.append(titleFormat).append("%nl%");

        Map<String, Double> stats;
        switch (type.toLowerCase()) {
            case "kills": stats = fetcher.getTopStats(Statistic.PLAYER_KILLS, 10); break;
            case "deaths": stats = fetcher.getTopStats(Statistic.DEATHS, 10); break;
            case "playtime": stats = fetcher.getTopStats(Statistic.PLAY_ONE_MINUTE, 10); break;
            case "balance": stats = fetcher.getTopBalances(10); break;
            case "credits": stats = fetcher.getTopCredits(10); break;
            case "token": case "tokens": stats = fetcher.getTopTokens(10); break;
            case "parkour": stats = fetcher.getTopParkour(10); break;
            case "lifesteal": stats = fetcher.getTopSkriptVar("lifesteal_level_%uuid%", 10); break;
            default: stats = new HashMap<>();
        }

        List<Map.Entry<String, Double>> entries = new ArrayList<>(stats.entrySet());

        for (int i = 0; i < 10; i++) {
            String line;
            int rank = i + 1;
            if (i < entries.size()) {
                Map.Entry<String, Double> entry = entries.get(i);
                String value;

                // Standard full format (e.g. 1,500)
                String fullValue = LeaderboardFormatter.formatNoDecimal(entry.getValue());
                // Suffix format (e.g. 1.5k)
                String smallValue = LeaderboardFormatter.formatSuffix(entry.getValue());

                if (type.equals("playtime")) {
                    long hours = (long) (entry.getValue() / 20 / 3600);
                    value = String.valueOf(hours);
                } else if (type.equals("balance") || type.equals("credits")) {
                    // Use short format as default %value% for these economy types
                    value = smallValue;
                } else {
                    value = fullValue;
                }

                line = lineFormat
                        .replace("%rank%", String.valueOf(rank))
                        .replace("%player%", entry.getKey())
                        .replace("%value%", value)
                        .replace("%small_balance%", smallValue)
                        .replace("%full_balance%", fullValue);
            } else {
                line = emptyLineFormat.replace("%rank%", String.valueOf(rank));
            }
            text.append(line).append("%nl%");
        }

        String refresh = String.valueOf(plugin.getConfig().getLong("leaderboards.refresh-seconds", 60));
        text.append(footerFormat.replace("%refresh-seconds%", refresh));

        String finalStr = text.toString();
        if (papiEnabled) finalStr = PlaceholderAPI.setPlaceholders(null, finalStr);

        display.text(miniMessage.deserialize(finalStr.replace("%nl%", "<newline>")));
    }

    public int removeLeaderboardsByType(String type) {
        int count = 0;
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, LeaderboardInfo> entry : activeLeaderboards.entrySet()) {
            if (type.equalsIgnoreCase("all") || entry.getValue().type().equalsIgnoreCase(type)) {
                toRemove.add(entry.getKey());
            }
        }
        for (UUID uuid : toRemove) {
            Entity e = Bukkit.getEntity(uuid);
            if (e != null) {
                e.getChunk().setForceLoaded(false); // Unforce chunk
                e.remove();
            }
            activeLeaderboards.remove(uuid);
            count++;
        }
        saveLeaderboards();
        return count;
    }

    public void removeAll() {
        removeLeaderboardsByType("all");
    }

    public boolean isManagedLeaderboard(UUID uuid) {
        return activeLeaderboards.containsKey(uuid);
    }

    private void loadLeaderboards() {
        File dbDir = new File(plugin.getDataFolder(), "database");
        if (!dbDir.exists()) dbDir.mkdirs();
        leaderboardsFile = new File(dbDir, "leaderboards.yml");

        if (!leaderboardsFile.exists()) {
            try (InputStream in = plugin.getResource("leaderboards.yml")) {
                if (in != null) Files.copy(in, leaderboardsFile.toPath());
                else leaderboardsFile.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        leaderboardsConfig = YamlConfiguration.loadConfiguration(leaderboardsFile);

        activeLeaderboards.clear();
        ConfigurationSection sec = leaderboardsConfig.getConfigurationSection("displays");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String type = sec.getString(key + ".type");
                    String world = sec.getString(key + ".world");
                    double x = sec.getDouble(key + ".x");
                    double y = sec.getDouble(key + ".y");
                    double z = sec.getDouble(key + ".z");
                    activeLeaderboards.put(uuid, new LeaderboardInfo(type, world, x, y, z));
                } catch (Exception e) {
                    plugin.getLogger().warning("Error loading leaderboard: " + key);
                }
            }
        }
    }

    private void saveLeaderboards() {
        if (leaderboardsConfig == null) return;
        leaderboardsConfig.set("displays", null);
        ConfigurationSection sec = leaderboardsConfig.createSection("displays");
        for (Map.Entry<UUID, LeaderboardInfo> entry : activeLeaderboards.entrySet()) {
            String uuid = entry.getKey().toString();
            LeaderboardInfo info = entry.getValue();
            sec.set(uuid + ".type", info.type());
            sec.set(uuid + ".world", info.worldName());
            sec.set(uuid + ".x", info.x());
            sec.set(uuid + ".y", info.y());
            sec.set(uuid + ".z", info.z());
        }
        try {
            leaderboardsConfig.save(leaderboardsFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}