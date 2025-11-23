package me.login.dungeon.game;

import me.login.Login;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.utils.DungeonUtils;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;

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
    }

    public void startDungeon(Player player, int dungeonId) {
        Dungeon dungeon = dungeonManager.getDungeon(dungeonId);
        if (dungeon == null) {
            DungeonUtils.error(player, "Dungeon " + dungeonId + " does not exist.");
            return;
        }

        if (!dungeon.isSetupComplete()) {
            DungeonUtils.error(player, "Dungeon is not fully setup!");
            return;
        }

        player.teleport(dungeon.getSpawnLocation());
        // Fix: Pass 'player' object, not UUID
        GameSession session = new GameSession(player, dungeon);
        sessions.put(player.getUniqueId(), session);
        DungeonUtils.msg(player, "You have entered Dungeon <yellow>" + dungeonId + "</yellow>!");
        DungeonUtils.msg(player, "Click the entry door to begin.");
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

    // Fix: Added missing getter for all sessions
    public Collection<GameSession> getAllSessions() {
        return sessions.values();
    }
}