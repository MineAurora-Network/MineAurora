package me.login.clearlag;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;


public class TPSWatcher extends BukkitRunnable {
    private final Plugin plugin;
    private final double TPS_THRESHOLD = 17.0;
    private final long COOLDOWN = 3 * 60 * 1000;
    private long lastTpsCleanup = 0;

    public TPSWatcher(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        // Get the 1-minute average TPS
        double tps = Bukkit.getServer().getTPS()[0];
        long now = System.currentTimeMillis();

        if (tps < TPS_THRESHOLD && (now - lastTpsCleanup > COOLDOWN)) {
            this.lastTpsCleanup = now;
            plugin.getLogger().warning("Server TPS is low (" + String.format("%.2f", tps) + "). Triggering emergency entity cleanup.");
            // Run the cleanup (sync, as we are already in a task)
            int removed = EntityCleanup.performCleanup(plugin);
        }
    }
}
