package me.login.lifesteal;

import me.login.Login;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DeadPlayerManager {

    private final Login plugin;
    private final DatabaseManager databaseManager;

    // Map of <UUID, PlayerName>
    private Map<UUID, String> deadPlayerCache = new HashMap<>();

    public DeadPlayerManager(Login plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        loadDeadPlayers();
    }

    private void loadDeadPlayers() {
        // This is a synchronous call on startup, which is fine.
        this.deadPlayerCache = databaseManager.getDeadPlayersMapSync();
        plugin.getLogger().info("Loaded " + deadPlayerCache.size() + " dead players from database.");
    }

    public void addDeadPlayer(UUID uuid, String name) {
        deadPlayerCache.put(uuid, name);
        // Asynchronously update database
        databaseManager.addDeadPlayer(uuid, name);
    }

    public void removeDeadPlayer(UUID uuid) {
        deadPlayerCache.remove(uuid);
        // Asynchronously update database
        databaseManager.removeDeadPlayer(uuid);
    }

    public boolean isDead(UUID uuid) {
        return deadPlayerCache.containsKey(uuid);
    }

    /**
     * Gets a copy of the dead players map from the cache.
     * @return A Map of <UUID, PlayerName>
     */
    public Map<UUID, String> getDeadPlayers() {
        return new HashMap<>(deadPlayerCache);
    }
}