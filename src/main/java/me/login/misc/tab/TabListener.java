package me.login.misc.tab;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class TabListener implements Listener {

    private final TabManager tabManager;

    public TabListener(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    @EventHandler(priority = EventPriority.MONITOR) // Run after other plugins
    public void onPlayerJoin(PlayerJoinEvent event) {
        // We add a small delay to ensure the player is fully loaded
        // and to let other plugins (like TAB) do their thing first.
        tabManager.getPlugin().getServer().getScheduler().runTaskLater(tabManager.getPlugin(), () -> {
            // Update all players, because their view of the new player
            // and the new player's view of them needs to be set.
            tabManager.updateAllPlayers();
        }, 10L); // 10-tick delay (0.5 seconds)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // When a player quits, we update all other players
        // to remove them from view (e.g., in the hub)
        // Delay is short, just to ensure it's the last thing to happen.
        tabManager.getPlugin().getServer().getScheduler().runTaskLater(tabManager.getPlugin(), () -> {
            tabManager.updateAllPlayers();
        }, 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        // Update the tab for the player who changed worlds
        // We add a small delay
        tabManager.getPlugin().getServer().getScheduler().runTaskLater(tabManager.getPlugin(), () -> {
            // We update *all* players, because this player's
            // visibility has changed for everyone.
            tabManager.updateAllPlayers();
        }, 10L); // 10-tick delay
    }
}