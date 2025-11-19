package me.login.pets.listeners;

import me.login.pets.PetManager;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.persistence.PersistentDataType;

public class PetProtectionListener implements Listener {

    private final PetManager petManager;

    public PetProtectionListener(PetManager petManager) {
        this.petManager = petManager;
    }

    // --- NEW: Force Allow Pet Spawns (WorldGuard Bypass) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        // Because we set the PDC tag in the spawn() consumer, it is present here!
        if (event.isCancelled()) {
            if (event.getEntity().getPersistentDataContainer().has(PetManager.PET_OWNER_KEY, PersistentDataType.STRING)) {
                event.setCancelled(false);
            }
        }
    }

    @EventHandler
    public void onEntityCombust(EntityCombustEvent event) {
        if (petManager.isPet(event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageEvent event) {
        Entity entity = event.getEntity();
        if (!petManager.isPet(entity)) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION ||
                event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
        }
    }
}