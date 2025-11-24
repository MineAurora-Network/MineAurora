package me.login.dungeon.game;

import me.login.Login;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.utils.DungeonUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class GameManager {
    private final Login plugin;
    private final DungeonManager dungeonManager;
    private final Map<UUID, GameSession> sessions = new HashMap<>();

    public GameManager(Login plugin, DungeonManager dungeonManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        startDungeonTicker();
    }

    private void startDungeonTicker() {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (GameSession session : new ArrayList<>(sessions.values())) {
                    if (session.isTimeUp()) {
                        failDungeon(session.getPlayer(), "<red>Time Limit Reached (15 mins)!");
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void startDungeon(Player player, int dungeonId) {
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon == null) {
            DungeonUtils.error(player, "Dungeon " + dungeonId + " does not exist.");
            return;
        }

        if (sessions.containsKey(player.getUniqueId())) {
            DungeonUtils.error(player, "You are already in a dungeon session!");
            return;
        }

        // Feature 4: Prevent joining occupied dungeons
        if (dungeon.isOccupied()) {
            DungeonUtils.error(player, "All dungeons are full or this dungeon is active. Try again later.");
            return;
        }

        if (!dungeon.isSetupComplete()) {
            DungeonUtils.error(player, "Dungeon is not fully setup!");
            return;
        }

        player.teleport(dungeon.getSpawnLocation());
        GameSession session = new GameSession(player, dungeon);
        sessions.put(player.getUniqueId(), session);

        // Feature 2: Consolidated message
        DungeonUtils.msg(player, "You have entered Dungeon <yellow>" + dungeonId + "</yellow>!%nl%" +
                "You have <red>15 minutes</red> to clear it!%nl%" +
                "Click the entry door to begin.");
    }

    public void failDungeon(Player player, String reason) {
        if (player != null) {
            // Feature 2: Consolidated message
            DungeonUtils.msg(player, "<red><b>Dungeon Failed:</b> " + reason + "%nl%<gray>Teleporting to spawn...");

            World lifesteal = Bukkit.getWorld("lifesteal");
            if (lifesteal != null) {
                Location tpLoc = new Location(lifesteal, 231.5, 57, 77.5);
                player.teleport(tpLoc);
            } else {
                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
            }
        }
        endSession(player);
    }

    public GameSession getSessionByMob(Entity entity) {
        for (GameSession session : sessions.values()) {
            if (session.hasMob(entity)) return session;
        }
        return null;
    }

    public void endSession(Player player) {
        GameSession session = sessions.remove(player.getUniqueId());
        if (session != null) {
            session.cleanup();
        }
    }

    public GameSession getSession(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public Collection<GameSession> getAllSessions() {
        return sessions.values();
    }
}