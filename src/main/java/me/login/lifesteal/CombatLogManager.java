package me.login.lifesteal;

import me.login.Login;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.ExperienceOrb;
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
    private final DeadPlayerManager deadPlayerManager; // <-- Field for new logic
    private LifestealLogger logger; // <-- ADDED FIELD

    private static final long COMBAT_TIME_MS = 10 * 1000; // 10 seconds
    private final Map<UUID, CombatData> combatMap = new HashMap<>();
    private final BukkitTask combatTimerTask;

    // --- CONSTRUCTOR UPDATED ---
    public CombatLogManager(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, DeadPlayerManager deadPlayerManager, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.deadPlayerManager = deadPlayerManager; // <-- STORE
        this.logger = logger; // <-- STORE LOGGER

        this.combatTimerTask = new BukkitRunnable() {
            // ... (run method remains the same) ...
            @Override
            public void run() {
                long currentTime = System.currentTimeMillis();
                new HashMap<>(combatMap).forEach((uuid, data) -> {
                    Player player = plugin.getServer().getPlayer(uuid);
                    if (player == null) {
                        combatMap.remove(uuid);
                        return;
                    }

                    long timeLeft = (data.lastHitTime + COMBAT_TIME_MS) - currentTime;
                    if (timeLeft <= 0) {
                        combatMap.remove(uuid);
                        player.sendActionBar(itemManager.formatMessage("<green>You are no longer in combat."));
                    } else {
                        player.sendActionBar(itemManager.formatMessage(
                                "<red>In Combat! Do not log out! <white>(" + (timeLeft / 1000 + 1) + "s)"
                        ));
                    }
                });
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    // --- (shutdown, onPlayerDamage, tagPlayer... remain the same) ---

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

        if (attacker == null || attacker.equals(victim)) return;

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
            CombatData data = combatMap.remove(victimUUID);

            // --- LOGGING UPDATED ---
            if (logger != null) {
                logger.logCombat("Player `" + victim.getName() + "` combat logged! Last attacker: `" + data.lastAttackerUUID + "`");
            }

            int currentHearts = lifestealManager.getHearts(victimUUID);
            if (currentHearts <= lifestealManager.getMinHearts()) {
                deadPlayerManager.addDeadPlayer(victimUUID, victim.getName());
                // --- LOGGING UPDATED ---
                if (logger != null) {
                    logger.logCombat("`" + victim.getName() + "` combat logged at " + currentHearts + " heart(s) and is now DEAD.");
                }
            } else {
                lifestealManager.removeHearts(victimUUID, 1);
            }

            lifestealManager.addHearts(data.lastAttackerUUID, 1);

            Player attacker = plugin.getServer().getPlayer(data.lastAttackerUUID);
            if (attacker != null && attacker.isOnline()) {
                attacker.sendMessage(itemManager.formatMessage(
                        "<green>" + victim.getName() + " combat logged! You gained 1 heart."
                ));
            }

            Location dropLocation = victim.getLocation();
            World world = victim.getWorld();
            for (ItemStack item : victim.getInventory().getContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    world.dropItemNaturally(dropLocation, item);
                }
            }
            victim.getInventory().clear();

            int xp = victim.getTotalExperience();
            if (xp > 0) {
                world.spawn(dropLocation, ExperienceOrb.class).setExperience(xp);
                victim.setTotalExperience(0);
            }
        }
    }

    private static class CombatData {
        long lastHitTime;
        UUID lastAttackerUUID;

        CombatData(long lastHitTime, UUID lastAttackerUUID) {
            this.lastHitTime = lastHitTime;
            this.lastAttackerUUID = lastAttackerUUID;
        }
    }
}