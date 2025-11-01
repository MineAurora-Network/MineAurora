package me.login.leaderboards;

import me.clip.placeholderapi.PlaceholderAPI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class LeaderboardDisplayManager {

    private final Login plugin;
    // Store UUID -> LeaderboardInfo mapping (in memory)
    private final Map<UUID, LeaderboardInfo> activeLeaderboards = new HashMap<>();

    private File leaderboardsFile; // The leaderboards.yml file object
    private FileConfiguration leaderboardsConfig; // The leaderboards.yml config object

    private final boolean papiEnabled;
    private final NumberFormat currencyFormatter;
    private final MiniMessage miniMessage;

    public LeaderboardDisplayManager(Login plugin) {
        this.plugin = plugin;
        this.papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
        this.currencyFormatter = NumberFormat.getCurrencyInstance(Locale.US);
        this.miniMessage = MiniMessage.miniMessage(); // Default parser
        loadLeaderboards(); // Load data FROM leaderboards.yml into memory
    }

    public void reloadConfigAndUpdateAll() {
        loadLeaderboards(); // Reload data FROM leaderboards.yml
        plugin.reloadConfig(); // Reload main config.yml (for formatting)
        plugin.getLogger().info("Reloading all " + activeLeaderboards.size() + " leaderboards...");
        updateAllDisplays(); // Force update entities
    }

    public void createLeaderboard(Location location, String type, Player creator) {
        location.setPitch(0);

        TextDisplay textDisplay = spawnNewDisplay(location, type);
        if (textDisplay == null) {
            creator.sendMessage(ChatColor.RED + "Failed to spawn leaderboard entity.");
            return;
        }

        // Create the temporary in-memory object
        LeaderboardInfo info = new LeaderboardInfo(
                type.toLowerCase(),
                location.getWorld().getName(),
                location.getX(),
                location.getY(),
                location.getZ()
        );
        // Store it in the map
        activeLeaderboards.put(textDisplay.getUniqueId(), info);

        // Save the data FROM the info object TO leaderboards.yml
        saveLeaderboards();

        creator.sendMessage(ChatColor.GREEN + "Leaderboard created! It will update on the next cycle.");
        updateDisplay(textDisplay.getUniqueId()); // Run initial update
    }

    private TextDisplay spawnNewDisplay(Location location, String type) {
        if (location == null || location.getWorld() == null) {
            plugin.getLogger().warning("Cannot spawn leaderboard: Location or World is invalid.");
            return null;
        }
        TextDisplay textDisplay = (TextDisplay) location.getWorld().spawnEntity(location, EntityType.TEXT_DISPLAY);
        textDisplay.setCustomName("leaderboard");
        textDisplay.setCustomNameVisible(false);
        textDisplay.text(miniMessage.deserialize("<yellow>Loading " + type + "..."));
        textDisplay.setBillboard(Display.Billboard.VERTICAL);
        textDisplay.setViewRange(30);
        textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        textDisplay.setShadowed(true);
        textDisplay.setInvulnerable(true); // Make it resistant to damage
        return textDisplay;
    }

    public void updateAllDisplays() {
        if (activeLeaderboards.isEmpty()) {
            return;
        }
        new HashSet<>(activeLeaderboards.keySet()).forEach(this::updateDisplay);
    }

    private void updateDisplay(UUID uuid) {
        // Get the in-memory info object for this UUID
        LeaderboardInfo info = activeLeaderboards.get(uuid);
        if (info == null) {
            plugin.getLogger().warning("Attempted to update leaderboard with unknown UUID: " + uuid);
            activeLeaderboards.remove(uuid);
            saveLeaderboards(); // Clean up YML
            return;
        }
        String type = info.type(); // Get type from the info object

        Entity currentEntity = Bukkit.getEntity(uuid);
        TextDisplay textDisplay;

        // --- Respawn Logic ---
        if (currentEntity == null || !currentEntity.isValid() || !(currentEntity instanceof TextDisplay)) {
            plugin.getLogger().info("Leaderboard " + type + " (UUID: " + uuid + ") not found or invalid. Respawning...");

            // Use the info object to get the location FROM the YML data
            Location respawnLoc = info.getLocation();
            if (respawnLoc == null) {
                plugin.getLogger().warning("Cannot respawn leaderboard " + type + ": World '" + info.worldName() + "' is not loaded.");
                return; // Skip update cycle, will try again later
            }

            TextDisplay newDisplay = spawnNewDisplay(respawnLoc, type);
            if (newDisplay == null) {
                plugin.getLogger().severe("Failed to respawn leaderboard " + type + " at " + respawnLoc);
                return; // Skip update cycle
            }

            // Update the map: remove old UUID, add new UUID with the SAME info object
            activeLeaderboards.remove(uuid);
            activeLeaderboards.put(newDisplay.getUniqueId(), info);
            saveLeaderboards(); // Save the new UUID mapping TO leaderboards.yml
            plugin.getLogger().info("Respawned leaderboard " + type + " with new UUID: " + newDisplay.getUniqueId());
            textDisplay = newDisplay; // Use the new display for the rest of the update
        } else {
            textDisplay = (TextDisplay) currentEntity; // Entity exists, use it
        }
        // --- End Respawn Logic ---

        // --- Update Text Logic ---
        // (Reads formats from config.yml, gets data, sets text on textDisplay)
        String titleFormat = plugin.getConfig().getString("leaderboards." + type + ".title", "<red>Missing Title");
        String lineFormat = plugin.getConfig().getString("leaderboards." + type + ".line-format", "<gray>#%rank% <white>%player% | <red>%value%");
        String emptyLineFormat = plugin.getConfig().getString("leaderboards." + type + ".empty-line-format", "<gray>#%rank% <white>N/A | N/A");
        String footerFormat = plugin.getConfig().getString("leaderboards." + type + ".footer", "<gray>Refreshes every 60s");
        StringBuilder leaderboardText = new StringBuilder();
        leaderboardText.append(titleFormat).append("%nl%");
        switch (type) {
            case "kills": case "deaths": case "playtime":
                Statistic statType = getStatistic(type); Map<String, Integer> topStats = StatsFetcher.getTopStats(statType, 10); List<Map.Entry<String, Integer>> statEntries = new ArrayList<>(topStats.entrySet()); for (int i = 0; i < 10; i++) { String line; int rank = i + 1; if (i < statEntries.size()) { Map.Entry<String, Integer> entry = statEntries.get(i); String value; if (type.equals("playtime")) { double hours = (double) entry.getValue() / 20.0 / 3600.0; long wholeHours = (long) Math.floor(hours); value = String.valueOf(wholeHours); } else { value = String.valueOf(entry.getValue()); } line = lineFormat.replace("%rank%", String.valueOf(rank)).replace("%player%", entry.getKey()).replace("%value%", value); } else { line = emptyLineFormat.replace("%rank%", String.valueOf(rank)); } leaderboardText.append(line).append("%nl%"); }
                break;
            case "balance":
                Economy econ = plugin.getVaultEconomy(); if (econ == null) { leaderboardText.append("<red>Error: Vault Economy not found."); break; } Map<String, Double> topBalances = StatsFetcher.getTopBalances(econ, 10); List<Map.Entry<String, Double>> balanceEntries = new ArrayList<>(topBalances.entrySet()); for (int i = 0; i < 10; i++) { String line; int rank = i + 1; if (i < balanceEntries.size()) { Map.Entry<String, Double> entry = balanceEntries.get(i); String formattedBalance = currencyFormatter.format(entry.getValue()); line = lineFormat.replace("%rank%", String.valueOf(rank)).replace("%player%", entry.getKey()).replace("%value%", formattedBalance); } else { line = emptyLineFormat.replace("%rank%", String.valueOf(rank)); } leaderboardText.append(line).append("%nl%"); }
                break;
            case "credits": case "lifesteal":
                String varPattern = type.equals("credits") ? "credits.%uuid%" : "lifesteal_level_%uuid%"; Map<String, Double> topSkriptVars = StatsFetcher.getTopSkriptVar(varPattern, 10); List<Map.Entry<String, Double>> skriptEntries = new ArrayList<>(topSkriptVars.entrySet()); for (int i = 0; i < 10; i++) { String line; int rank = i + 1; if (i < skriptEntries.size()) { Map.Entry<String, Double> entry = skriptEntries.get(i); String value; if (type.equals("credits")) { value = currencyFormatter.format(entry.getValue()); } else { value = String.valueOf(entry.getValue().longValue()); } line = lineFormat.replace("%rank%", String.valueOf(rank)).replace("%player%", entry.getKey()).replace("%value%", value); } else { line = emptyLineFormat.replace("%rank%", String.valueOf(rank)); } leaderboardText.append(line).append("%nl%"); }
                break;
            default:
                leaderboardText.append("<red>Invalid Type: ").append(type);
                break;
        }
        String refreshTime = String.valueOf(plugin.getConfig().getLong("leaderboards.refresh-seconds", 60));
        leaderboardText.append(footerFormat.replace("%refresh-seconds%", refreshTime));
        String finalString = leaderboardText.toString();
        if (papiEnabled) {
            finalString = PlaceholderAPI.setPlaceholders(null, finalString);
        }
        finalString = finalString.replace("%nl%", "<newline>");
        Component component = miniMessage.deserialize(finalString);
        textDisplay.text(component);
        // --- End Update Text Logic ---
    }

    private Statistic getStatistic(String type) {
        switch (type) { case "kills": return Statistic.PLAYER_KILLS; case "deaths": return Statistic.DEATHS; case "playtime": return Statistic.PLAY_ONE_MINUTE; default: return Statistic.DEATHS; }
    }

    public int removeLeaderboardsByType(String typeToRemove) {
        int removedCount = 0;
        List<UUID> toRemove = new ArrayList<>();
        for (Map.Entry<UUID, LeaderboardInfo> entry : new HashMap<>(activeLeaderboards).entrySet()) {
            if (typeToRemove.equalsIgnoreCase("all") || entry.getValue().type().equalsIgnoreCase(typeToRemove)) {
                toRemove.add(entry.getKey());
            }
        }
        if (toRemove.isEmpty()) return 0;
        for (UUID uuid : toRemove) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity instanceof TextDisplay && entity.isValid()) {
                entity.remove();
                removedCount++;
            }
            activeLeaderboards.remove(uuid); // Remove from in-memory map
        }
        saveLeaderboards(); // Save changes TO leaderboards.yml
        plugin.getLogger().info("Removed " + removedCount + " leaderboards of type '" + typeToRemove + "'.");
        return removedCount;
    }

    public boolean isManagedLeaderboard(UUID uuid) {
        return activeLeaderboards.containsKey(uuid);
    }

    // --- Persistence Methods (Using leaderboards.yml) ---
    public void loadLeaderboards() {
        leaderboardsFile = new File(plugin.getDataFolder(), "leaderboards.yml");
        if (!leaderboardsFile.exists()) {
            plugin.saveResource("leaderboards.yml", false);
        }
        leaderboardsConfig = YamlConfiguration.loadConfiguration(leaderboardsFile);
        if (leaderboardsConfig == null) {
            plugin.getLogger().severe("FAILED TO LOAD leaderboards.yml! File might be corrupt.");
            leaderboardsConfig = new YamlConfiguration();
        }

        activeLeaderboards.clear(); // Clear map before loading
        ConfigurationSection displaysSection = leaderboardsConfig.getConfigurationSection("displays");
        if (displaysSection != null) {
            for (String uuidString : displaysSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidString);
                    // Read data FROM YML
                    String type = displaysSection.getString(uuidString + ".type");
                    String worldName = displaysSection.getString(uuidString + ".world");
                    double x = displaysSection.getDouble(uuidString + ".x");
                    double y = displaysSection.getDouble(uuidString + ".y");
                    double z = displaysSection.getDouble(uuidString + ".z");

                    if (type == null || worldName == null) {
                        plugin.getLogger().warning("Incomplete leaderboard data for UUID: " + uuidString + " in leaderboards.yml. Skipping.");
                        continue;
                    }

                    // Create in-memory object FROM YML data
                    LeaderboardInfo info = new LeaderboardInfo(type, worldName, x, y, z);
                    activeLeaderboards.put(uuid, info); // Store in map

                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Found invalid UUID in leaderboards.yml: " + uuidString);
                } catch (Exception e) {
                    plugin.getLogger().severe("Error loading leaderboard data for " + uuidString + ": " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + activeLeaderboards.size() + " leaderboard configurations from leaderboards.yml.");
    }

    public void saveLeaderboards() {
        if (leaderboardsConfig == null) {
            plugin.getLogger().severe("Cannot save leaderboards: Config object is null.");
            loadLeaderboards(); // Try reloading
            if (leaderboardsConfig == null) return; // Still failed, abort save.
        }

        leaderboardsConfig.set("displays", null); // Clear old data in config object
        ConfigurationSection displaysSection = leaderboardsConfig.createSection("displays");

        // Loop through in-memory map
        for (Map.Entry<UUID, LeaderboardInfo> entry : activeLeaderboards.entrySet()) {
            UUID uuid = entry.getKey();
            LeaderboardInfo info = entry.getValue(); // Get in-memory object

            // Write data FROM in-memory object TO config object
            ConfigurationSection uuidSection = displaysSection.createSection(uuid.toString());
            uuidSection.set("type", info.type());
            uuidSection.set("world", info.worldName());
            uuidSection.set("x", info.x());
            uuidSection.set("y", info.y());
            uuidSection.set("z", info.z());
        }

        try {
            leaderboardsConfig.save(leaderboardsFile); // Save config object TO leaderboards.yml file
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save leaderboards to file!");
            e.printStackTrace();
        }
    }
    // --- End Persistence Methods ---
}