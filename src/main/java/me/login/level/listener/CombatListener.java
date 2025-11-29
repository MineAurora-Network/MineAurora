package me.login.level.listener;

import me.login.level.LevelManager;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

public class CombatListener implements Listener {

    private final LevelManager manager;

    public CombatListener(LevelManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Player killer = entity.getKiller();

        if (killer == null) return;

        // Kill Player: 5 XP
        if (entity instanceof Player) {
            manager.addXp(killer, 5, "Player Kill");
            return;
        }

        // Kill Mob: Based on HP
        // < 50: 1xp, 50-125: 2xp, > 125: 3xp
        double maxHealth = 20.0;
        if (entity.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            maxHealth = entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        }

        int xp = 1;
        if (maxHealth > 125) {
            xp = 3;
        } else if (maxHealth > 50) {
            xp = 2;
        }

        manager.addXp(killer, xp, "Mob Kill");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // Can be null if natural cause

        int loss = 5; // Default natural
        if (killer != null) {
            loss = 10; // Killed by player
        }

        manager.removeXp(victim, loss);
    }
}