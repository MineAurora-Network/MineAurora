package me.login.misc.tab;

import org.bukkit.scheduler.BukkitRunnable;

public class TabUpdater extends BukkitRunnable {

    private final TabManager tabManager;

    public TabUpdater(TabManager tabManager) {
        this.tabManager = tabManager;
    }

    @Override
    public void run() {
        // This task periodically refreshes the tab list for all players
        // This ensures that player visibility (hide/show) is correctly
        // maintained as players move between worlds.
        try {
            tabManager.updateAllPlayers();
        } catch (Exception e) {
            tabManager.getPlugin().getLogger().warning("Error occurred during TabUpdater task:");
            e.printStackTrace();
        }
    }
}