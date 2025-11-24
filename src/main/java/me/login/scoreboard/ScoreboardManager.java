package me.login.scoreboard;

import me.login.Login;
import me.login.dungeon.game.GameSession;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreboardManager implements Listener {

    private final Login plugin;
    private final Map<UUID, Scoreboard> scoreboardMap = new HashMap<>();

    // Hub Scoreboard Variables
    private String hubTitle;
    private List<String> hubLines;
    private Set<String> hubWorlds;

    // Lifesteal Scoreboard Variables
    private String lifestealTitle;
    private List<String> lifestealLines;
    private Set<String> lifestealWorlds;

    // Dungeon Scoreboard Variables
    private String dungeonTitle;
    private List<String> dungeonLines;
    private Set<String> dungeonWorlds;

    public ScoreboardManager(JavaPlugin plugin) {
        this.plugin = (Login) plugin;
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdater();
    }

    public void loadConfig() {
        // Hub
        this.hubTitle = plugin.getConfig().getString("scoreboard.hub.title", "&cHub Title Not Set");
        this.hubLines = plugin.getConfig().getStringList("scoreboard.hub.lines");
        this.hubWorlds = plugin.getConfig().getStringList("scoreboard.hub.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        // Lifesteal
        this.lifestealTitle = plugin.getConfig().getString("scoreboard.lifesteal.title", "&cLifesteal Title Not Set");
        this.lifestealLines = plugin.getConfig().getStringList("scoreboard.lifesteal.lines");
        this.lifestealWorlds = plugin.getConfig().getStringList("scoreboard.lifesteal.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        // Dungeon
        this.dungeonTitle = plugin.getConfig().getString("scoreboard.dungeon.title", "&cDungeon Title Not Set");
        this.dungeonLines = plugin.getConfig().getStringList("scoreboard.dungeon.lines");
        this.dungeonWorlds = plugin.getConfig().getStringList("scoreboard.dungeon.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());
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
        }.runTaskTimer(plugin, 0, 20L);
    }

    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = scoreboardMap.get(player.getUniqueId());
        if (scoreboard == null) return;

        String worldName = player.getWorld().getName().toLowerCase();
        String title;
        List<String> lines;

        if (hubWorlds.contains(worldName)) {
            title = hubTitle;
            lines = new ArrayList<>(hubLines);
        } else if (lifestealWorlds.contains(worldName)) {
            title = lifestealTitle;
            lines = new ArrayList<>(lifestealLines);
        } else if (dungeonWorlds.contains(worldName)) {
            title = dungeonTitle;
            lines = new ArrayList<>(dungeonLines);

            // DUNGEON PLACEHOLDERS
            try {
                // This call now works because getters exist in Login and DungeonModule
                if (plugin.getDungeonModule() != null && plugin.getDungeonModule().getGameManager() != null) {
                    GameSession session = plugin.getDungeonModule().getGameManager().getSession(player);
                    if (session != null) {
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            if (line.contains("%time_dungeon_started%")) {
                                line = line.replace("%time_dungeon_started%", session.getTimeLeft());
                            }
                            if (line.contains("%mobs_left_in_room%")) {
                                line = line.replace("%mobs_left_in_room%", String.valueOf(session.getMobsLeft()));
                            }
                            if (line.contains("%dungeon_current_room%")) {
                                line = line.replace("%dungeon_current_room%", String.valueOf(session.getCurrentRoomId() + 1));
                            }
                            lines.set(i, line);
                        }
                    } else {
                        // Fallback
                        for (int i = 0; i < lines.size(); i++) {
                            lines.set(i, lines.get(i)
                                    .replace("%time_dungeon_started%", "--:--")
                                    .replace("%mobs_left_in_room%", "0")
                                    .replace("%dungeon_current_room%", "N/A"));
                        }
                    }
                }
            } catch (Exception ignored) {}

        } else {
            scoreboard.clear();
            return;
        }

        if (lines == null || lines.isEmpty()) {
            scoreboard.clear();
            return;
        }

        scoreboard.setTitle(title);
        scoreboard.updateLines(lines);
    }
}