package me.login.misc.tab;

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
        // Immediate update to hide/show correct players
        tabManager.updateAllPlayers();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        tabManager.getPlugin().getServer().getScheduler().runTaskLater(tabManager.getPlugin(), () -> {
            tabManager.updateAllPlayers();
        }, 1L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        boolean wasManaged = tabManager.isManagedWorld(event.getFrom());
        boolean isManaged = tabManager.isManagedWorld(event.getPlayer().getWorld());

        // If player moved FROM Hub/Login TO Lifesteal/etc.
        if (wasManaged && !isManaged) {
            // We must RESET them so they can see players again (and let TAB take over)
            tabManager.resetTabList(event.getPlayer());
        }

        // Update everyone else (e.g., remove this player from Hub tablist)
        tabManager.updateAllPlayers();
    }
}