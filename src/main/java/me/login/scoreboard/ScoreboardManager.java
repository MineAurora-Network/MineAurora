package me.login.scoreboard;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet; // Use HashSet for faster lookups
import java.util.List;
import java.util.Map;
import java.util.Set; // Import Set
import java.util.UUID;
import java.util.stream.Collectors; // For converting list to lowercase set

public class ScoreboardManager implements Listener {

    private final Login plugin;
    private final Map<UUID, Scoreboard> scoreboardMap = new HashMap<>();

    // Variables to hold the designs from the config
    private String hubTitle;
    private List<String> hubLines;
    private Set<String> hubWorlds; // <-- NEW: Use Set for efficiency
    private String lifestealTitle;
    private List<String> lifestealLines;
    private Set<String> lifestealWorlds; // <-- NEW: Use Set for efficiency

    public ScoreboardManager(JavaPlugin plugin) {
        this.plugin = (Login) plugin;
        loadConfig(); // Load the design from config on startup
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdater();
    }

    /**
     * Loads or reloads the scoreboard format from the config.yml.
     */
    public void loadConfig() {
        // Load Hub scoreboard
        this.hubTitle = plugin.getConfig().getString("scoreboard.hub.title", "&cHub Title Not Set");
        this.hubLines = plugin.getConfig().getStringList("scoreboard.hub.lines");
        // Convert world list to lowercase set for fast, case-insensitive checks
        this.hubWorlds = plugin.getConfig().getStringList("scoreboard.hub.worlds")
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet()); // <-- NEW

        // Load Lifesteal scoreboard
        this.lifestealTitle = plugin.getConfig().getString("scoreboard.lifesteal.title", "&cLifesteal Title Not Set");
        this.lifestealLines = plugin.getConfig().getStringList("scoreboard.lifesteal.lines");
        // Convert world list to lowercase set
        this.lifestealWorlds = plugin.getConfig().getStringList("scoreboard.lifesteal.worlds")
                .stream()
                .map(String::toLowerCase)
                .collect(Collectors.toSet()); // <-- NEW
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scoreboardMap.put(player.getUniqueId(), new Scoreboard(player, "Loading..."));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Scoreboard scoreboard = scoreboardMap.remove(player.getUniqueId());
        if (scoreboard != null) {
            scoreboard.clear();
        }
    }

    private void startUpdater() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateScoreboard(player);
                }
            }
        }.runTaskTimer(plugin, 0, 20L); // 20L = 1 second
    }

    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = scoreboardMap.get(player.getUniqueId());
        if (scoreboard == null) {
            return;
        }

        String worldName = player.getWorld().getName().toLowerCase(); // Already lowercase
        String title;
        List<String> lines;

        // --- UPDATED LOGIC ---
        // Check if the current world is in the hub worlds list
        if (hubWorlds.contains(worldName)) {
            title = hubTitle;
            lines = hubLines;
            // Check if the current world is in the lifesteal worlds list
        } else if (lifestealWorlds.contains(worldName)) {
            title = lifestealTitle;
            lines = lifestealLines;
            // Otherwise, clear the scoreboard
        } else {
            scoreboard.clear();
            return;
        }
        // --- END UPDATED LOGIC ---

        // If for some reason the config lines are empty, clear the board.
        if (lines == null || lines.isEmpty()) { // Added null check
            scoreboard.clear();
            return;
        }

        scoreboard.setTitle(title);
        scoreboard.updateLines(new ArrayList<>(lines)); // Use a copy to prevent modification issues
    }
}