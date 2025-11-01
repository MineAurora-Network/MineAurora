package me.login;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DamageIndicator implements Listener {

    private final Login plugin;
    private final Map<UUID, BossBar> activeBars = new HashMap<>();
    private final Map<UUID, BukkitTask> removalTasks = new HashMap<>();
    private final long DISPLAY_DURATION_TICKS = 60L; // 3 seconds

    public DamageIndicator(Login plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamageByPlayer(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (damager.equals(victim)) return;

        UUID damagerUUID = damager.getUniqueId();

        // Use scheduler to get health *after* damage is applied
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            // --- MODIFIED LOGIC ---
            double currentHealth;
            double maxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            double progress;
            boolean isDead = !victim.isValid() || victim.isDead() || victim.getHealth() <= 0; // Check if dead or health is zero

            if (isDead) {
                currentHealth = 0;
                progress = 0.0;
            } else {
                currentHealth = Math.max(0, victim.getHealth()); // Ensure health isn't negative
                progress = Math.max(0.0, Math.min(1.0, currentHealth / maxHealth)); // Clamp progress
            }
            // --- END MODIFIED LOGIC ---


            String victimName = victim.getName();
            if (victim.getCustomName() != null && !victim.getCustomName().isEmpty()) {
                victimName = ChatColor.translateAlternateColorCodes('&', victim.getCustomName());
            } else {
                String typeName = victim.getType().toString().toLowerCase().replace("_", " ");
                victimName = typeName.substring(0, 1).toUpperCase() + typeName.substring(1);
            }

            String title = String.format("%s " + ChatColor.RED + "❤ " + ChatColor.WHITE + "%.1f / %.1f",
                    victimName, currentHealth, maxHealth); // Show 0 if dead

            // Cancel previous removal task
            BukkitTask oldTask = removalTasks.remove(damagerUUID);
            if (oldTask != null) oldTask.cancel();

            // Get or create BossBar
            BossBar bossBar = activeBars.get(damagerUUID);
            if (bossBar == null) {
                // Only create if player is still online
                if (!damager.isOnline()) return;
                bossBar = Bukkit.createBossBar(title, BarColor.RED, BarStyle.SOLID);
                activeBars.put(damagerUUID, bossBar);
                bossBar.addPlayer(damager);
            } else {
                if (damager.isOnline()) {
                    bossBar.setTitle(title);
                    bossBar.setColor(BarColor.RED);
                    bossBar.setStyle(BarStyle.SOLID);
                } else {
                    removeBossBar(damagerUUID); // Clean up if player logged off
                    return;
                }
            }

            bossBar.setProgress(progress); // Set progress (will be 0 if dead)
            bossBar.setVisible(true);

            // Schedule removal
            BukkitTask newTask = new BukkitRunnable() {
                @Override
                public void run() { removeBossBar(damagerUUID); }
            }.runTaskLater(plugin, DISPLAY_DURATION_TICKS);
            removalTasks.put(damagerUUID, newTask);

        }, 1L); // Run 1 tick later
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        removeBossBar(event.getPlayer().getUniqueId());
    }

    private void removeBossBar(UUID playerUUID) {
        BukkitTask task = removalTasks.remove(playerUUID);
        if (task != null && !task.isCancelled()) task.cancel();

        BossBar bar = activeBars.remove(playerUUID);
        if (bar != null) {
            bar.setVisible(false);
            bar.removeAll();
        }
    }
}