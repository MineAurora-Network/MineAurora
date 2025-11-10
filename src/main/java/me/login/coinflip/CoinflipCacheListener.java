package me.login.coinflip;

import me.login.Login;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.UUID;

public class CoinflipCacheListener implements Listener {

    private final Login plugin;
    private final CoinflipDatabase database;
    private final MessageManager messageManager;

    public CoinflipCacheListener(Login plugin, CoinflipDatabase database, MessageManager messageManager) {
        this.plugin = plugin;
        this.database = database;
        this.messageManager = messageManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        // Load the player's toggle setting asynchronously
        database.loadMessageToggle(uuid).thenAccept(toggleValue -> {
            // When the database responds, add it to the cache
            messageManager.addPlayerToCache(uuid, toggleValue);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Remove the player from the cache to save memory
        messageManager.removePlayerFromCache(event.getPlayer().getUniqueId());
    }
}