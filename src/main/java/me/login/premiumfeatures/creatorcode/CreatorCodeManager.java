package me.login.premiumfeatures.creatorcode;

import me.login.Login;
import me.login.premiumfeatures.credits.CreditsDatabase;
import org.bukkit.Bukkit;

import java.util.Collections;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CreatorCodeManager {

    private final Login plugin;
    private final CreditsDatabase database;

    // Cache valid creator codes in memory for fast lookup/tab completion
    private final Set<String> cachedCodes = ConcurrentHashMap.newKeySet();

    public CreatorCodeManager(Login plugin, CreditsDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Loads valid codes from database into cache.
     */
    public void loadCodes() {
        cachedCodes.clear();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            cachedCodes.addAll(database.getCreatorCodes());
            plugin.getLogger().info("Loaded " + cachedCodes.size() + " creator codes from DB.");
        });
    }

    public boolean addCode(String code) {
        String lowerCode = code.toLowerCase();
        if (cachedCodes.contains(lowerCode)) {
            return false;
        }
        cachedCodes.add(lowerCode);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.addCreatorCode(lowerCode));
        return true;
    }

    public boolean removeCode(String code) {
        String lowerCode = code.toLowerCase();
        if (!cachedCodes.contains(lowerCode)) {
            return false;
        }
        cachedCodes.remove(lowerCode);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.removeCreatorCode(lowerCode));
        return true;
    }

    public boolean isCodeValid(String code) {
        return cachedCodes.contains(code.toLowerCase());
    }

    public Set<String> getCodes() {
        return Collections.unmodifiableSet(cachedCodes);
    }

    /**
     * Checks if a player has already applied a creator code in the DB.
     * returns the code if found, null otherwise.
     */
    public String getPlayerCode(UUID uuid) {
        return database.getPlayerCreatorCode(uuid);
    }

    /**
     * Sets the player's supported creator code in the DB.
     */
    public void setPlayerCode(UUID uuid, String code) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> database.setPlayerCreatorCode(uuid, code));
    }
}