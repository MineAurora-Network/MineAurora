package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material; // <-- MISSING IMPORT ADDED
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CombatLogManager implements Listener {

    private final Login plugin;
    private final ItemManager itemManager;
    private final LifestealManager lifestealManager;

    private static final long COMBAT_TIME_MS = 10 * 1000; // 10 seconds
    private final Map<UUID, CombatData> combatMap = new HashMap<>();
    private final BukkitTask combatTimerTask;

    public CombatLogManager(Login plugin, ItemManager itemManager, LifestealManager lifestealManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;

        this.combatTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                // Iterate over a copy to avoid ConcurrentModificationException
                new HashMap<>(combatMap).forEach((uuid, data) -> {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null) {
                        combatMap.remove(uuid); // Clean up
                        return;
                    }

                    long timeLeft = (data.lastHitTime + COMBAT_TIME_MS) - currentTime;
                    if (timeLeft <= 0) {
                        // Combat expired
                        combatMap.remove(uuid);
                        player.sendActionBar(itemManager.formatMessage("<green>You are no longer in combat."));
                    } else {
                        // Still in combat
                        player.sendActionBar(itemManager.formatMessage(
                                "<red>In Combat! Do not log out! <white>(" + (timeLeft / 1000 + 1) + "s)"
                        ));
                    }
                });
            }
        }.runTaskTimer(plugin, 20L, 20L); // Run every second
    }

    public void shutdown() {
        if (combatTimerTask != null && !combatTimerTask.isCancelled()) {
            combatTimerTask.cancel();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof Player) {
                attacker = (Player) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.equals(victim)) return; // No attacker or self-harm

        // Tag both players
        tagPlayer(victim, attacker);
        tagPlayer(attacker, victim);
    }

    private void tagPlayer(Player player, Player attacker) {
        combatMap.put(player.getUniqueId(), new CombatData(System.currentTimeMillis(), attacker.getUniqueId()));
        player.sendActionBar(itemManager.formatMessage("<red>You are in combat! Do not log out!"));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player victim = event.getPlayer();
        UUID victimUUID = victim.getUniqueId();

        if (combatMap.containsKey(victimUUID)) {
            // Player combat logged!
            CombatData data = combatMap.remove(victimUUID);

            itemManager.sendLog(victim.getName() + " combat logged! Attacker: " + data.lastAttackerUUID);

            // Punish victim: lose 1 heart
            // This is an offline change, so LifestealManager's file-based system is perfect
            lifestealManager.removeHearts(victimUUID, 1);

            // Reward attacker: gain 1 heart
            lifestealManager.addHearts(data.lastAttackerUUID, 1);

            Player attacker = plugin.getServer().getPlayer(data.lastAttackerUUID);
            if (attacker != null && attacker.isOnline()) {
                attacker.sendMessage(itemManager.formatMessage(
                        "<green>" + victim.getName() + " combat logged! You gained 1 heart."
                ));
            }

            // Drop inventory
            Location dropLocation = victim.getLocation();
            World world = victim.getWorld();
            for (ItemStack item : victim.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    world.dropItemNaturally(dropLocation, item);
                }
            }
            // Clear inventory (so they don't have it on rejoin)
            victim.getInventory().clear();
        }
    }

    // If a dead player logs in, make sure they are still in spectator
    @EventHandler
    public void onDeadPlayerJoin(PlayerJoinEvent event) {
        if (plugin.getDeadPlayerManager() != null && plugin.getDeadPlayerManager().isDead(event.getPlayer().getUniqueId())) {
            event.getPlayer().setGameMode(org.bukkit.GameMode.SPECTATOR);
        }
    }

    // --- Private inner class ---
    private static class CombatData {
        long lastHitTime;
        UUID lastAttackerUUID;

        CombatData(long lastHitTime, UUID lastAttackerUUID) {
            this.lastHitTime = lastHitTime;
            this.lastAttackerUUID = lastAttackerUUID;
        }
    }
}