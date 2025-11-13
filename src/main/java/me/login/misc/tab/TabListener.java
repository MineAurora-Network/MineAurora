package me.login.misc.tab;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TabListener implements Listener {

    private final TabManager tabManager;

    public TabListener(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Update all players because someone new joined
        // This ensures the new player sees the right list
        // and everyone else sees the new player (if they should)
        tabManager.updateAllPlayers();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Run update on next tick to ensure player is gone
        tabManager.getPlugin().getServer().getScheduler().runTaskLater(tabManager.getPlugin(), () -> {
            // Update all players because someone left
            tabManager.updateAllPlayers();
        }, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Update all players because someone moved
        // This ensures their old world and new world lists are correct
        tabManager.updateAllPlayers();
    }
}