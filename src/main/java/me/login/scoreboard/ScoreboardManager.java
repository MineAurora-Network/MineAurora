package me.login.scoreboard;

import me.login.Login;
import me.login.dungeon.game.GameSession;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class ScoreboardManager implements Listener {

    private final Login plugin;
    private final Map<UUID, Scoreboard> scoreboardMap = new HashMap<>();

    private String hubTitle;
    private List<String> hubLines;
    private Set<String> hubWorlds;

    private String lifestealTitle;
    private List<String> lifestealLines;
    private Set<String> lifestealWorlds;

    private String dungeonTitle;
    private List<String> dungeonLines;
    private Set<String> dungeonWorlds;

    public ScoreboardManager(Login plugin) {
        this.plugin = plugin;
        loadConfig();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startUpdater();
    }

    public void loadConfig() {
        this.hubTitle = plugin.getConfig().getString("scoreboard.hub.title", "&cHub");
        this.hubLines = plugin.getConfig().getStringList("scoreboard.hub.lines");
        this.hubWorlds = plugin.getConfig().getStringList("scoreboard.hub.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        this.lifestealTitle = plugin.getConfig().getString("scoreboard.lifesteal.title", "&cLifesteal");
        this.lifestealLines = plugin.getConfig().getStringList("scoreboard.lifesteal.lines");
        this.lifestealWorlds = plugin.getConfig().getStringList("scoreboard.lifesteal.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        this.dungeonTitle = plugin.getConfig().getString("scoreboard.dungeon.title", "&cDungeon");
        this.dungeonLines = plugin.getConfig().getStringList("scoreboard.dungeon.lines");
        this.dungeonWorlds = plugin.getConfig().getStringList("scoreboard.dungeon.worlds")
                .stream().map(String::toLowerCase).collect(Collectors.toSet());

        // Ensure sets are not null if config is missing
        if (hubWorlds == null) hubWorlds = new HashSet<>();
        if (lifestealWorlds == null) lifestealWorlds = new HashSet<>();
        if (dungeonWorlds == null) dungeonWorlds = new HashSet<>();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        scoreboardMap.put(player.getUniqueId(), new Scoreboard(plugin, player, "Loading..."));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Scoreboard sb = scoreboardMap.remove(player.getUniqueId());
        if (sb != null) {
            sb.clear();
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
        }.runTaskTimer(plugin, 0L, 20L);
    }

    public void updateScoreboard(Player player) {
        Scoreboard scoreboard = scoreboardMap.get(player.getUniqueId());
        if (scoreboard == null) {
            scoreboard = new Scoreboard(plugin, player, "Loading...");
            scoreboardMap.put(player.getUniqueId(), scoreboard);
        }

        String worldName = player.getWorld().getName().toLowerCase();
        String title = hubTitle;
        List<String> lines = new ArrayList<>(hubLines);

        if (lifestealWorlds.contains(worldName)) {
            title = lifestealTitle;
            lines = new ArrayList<>(lifestealLines);
        } else if (dungeonWorlds.contains(worldName)) {
            title = dungeonTitle;
            lines = new ArrayList<>(dungeonLines);

            try {
                if (plugin.getDungeonModule() != null && plugin.getDungeonModule().getGameManager() != null) {
                    GameSession session = plugin.getDungeonModule().getGameManager().getSession(player);
                    if (session != null) {
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            line = line.replace("%time_dungeon_started%", session.getTimeLeft());
                            line = line.replace("%mobs_left_in_room%", String.valueOf(session.getMobsLeft()));
                            line = line.replace("%dungeon_current_room%", String.valueOf(session.getCurrentRoomId() + 1));
                            lines.set(i, line);
                        }
                    } else {
                        for (int i = 0; i < lines.size(); i++) {
                            String line = lines.get(i);
                            line = line.replace("%time_dungeon_started%", "--:--");
                            line = line.replace("%mobs_left_in_room%", "0");
                            line = line.replace("%dungeon_current_room%", "N/A");
                            lines.set(i, line);
                        }
                    }
                }
            } catch (Exception ignored) {}
        }

        scoreboard.setTitle(title);
        scoreboard.updateLines(lines);
    }
}