package me.login.clearlag;

import me.login.Login; // <-- CHANGED
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

public class TPSWatcher extends BukkitRunnable {
    private final Login plugin; // <-- CHANGED
    private final LagClearLogger logger; // <-- NEW
    private final double TPS_THRESHOLD = 17.0;
    private final long COOLDOWN = 3 * 60 * 1000;
    private long lastTpsCleanup = 0;

    public TPSWatcher(Login plugin) { // <-- CHANGED
        this.plugin = plugin;
        this.logger = plugin.getLagClearLogger(); // <-- NEW
    }

    @Override
    public void run() {
        double tps = Bukkit.getServer().getTPS()[0];
        long now = System.currentTimeMillis();

        if (tps < TPS_THRESHOLD && (now - lastTpsCleanup > COOLDOWN)) {
            this.lastTpsCleanup = now;

            String warning = "Server TPS is low (" + String.format("%.2f", tps) + "). Triggering emergency entity cleanup.";
            if (logger != null) logger.sendLog(warning); else plugin.getLogger().warning(warning); // <-- CHANGED

            int removed = EntityCleanup.performCleanup(plugin); // <-- CHANGED
        }
    }
}