package me.login.pets.listeners;

import me.login.pets.PetManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
// --- BUG FIX: Added missing imports ---
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

public class PetProtectionListener implements Listener {

    private final PetManager petManager;

    public PetProtectionListener(PetManager petManager) {
        this.petManager = petManager;
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (petManager.isPet(victim)) {
            // --- BUG FIX: Prevent pet from being damaged while being captured ---
            if (petManager.isCapturing(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }

            // Case 1: Player is damaging a pet
            if (damager instanceof Player) {
                Player playerDamager = (Player) damager;
                UUID ownerUuid = petManager.getPetOwner(victim);

                if (ownerUuid != null && ownerUuid.equals(playerDamager.getUniqueId())) {
                    // Owner is damaging their own pet
                    event.setCancelled(true);
                }
                // Other players can damage the pet, so we don't cancel
            }
        } else if (petManager.isPet(damager)) {
            // Case 2: Pet is damaging a player
            if (victim instanceof Player) {
                Player playerVictim = (Player) victim;
                UUID ownerUuid = petManager.getPetOwner(damager);

                if (ownerUuid != null && ownerUuid.equals(playerVictim.getUniqueId())) {
                    // Pet is somehow damaging its own owner
                    event.setCancelled(true);
                }
            }
        }

        // Case 3: Pet damaging another pet (optional, but good to prevent)
        if (petManager.isPet(victim) && petManager.isPet(damager)) {
            UUID owner1 = petManager.getPetOwner(victim);
            UUID owner2 = petManager.getPetOwner(damager);

            // If they have the same owner, cancel
            if (owner1 != null && owner1.equals(owner2)) {
                event.setCancelled(true);
            }
        }
    }

    // --- BUG FIX: Added this new method to handle pet aggression ---
    /**
     * Handles pet aggression when the owner is in combat.
     * 1. If owner is attacked, pet attacks the damager.
     * 2. If owner attacks a mob, pet attacks that mob.
     */
    @EventHandler
    public void onOwnerCombat(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        // Case 1: Owner gets attacked
        if (victim instanceof Player) {
            Player owner = (Player) victim;
            // Check if the owner has an active pet AND the damager is a valid target
            if (petManager.hasActivePet(owner.getUniqueId()) && damager instanceof LivingEntity) {
                // Don't make the pet attack itself
                if (damager.getUniqueId().equals(petManager.getActivePet(owner.getUniqueId()).getUniqueId())) {
                    return;
                }
                // Tell pet to attack the damager
                petManager.getTargetSelection().handlePetAggression(owner, damager);
            }
        }
        // Case 2: Owner attacks something
        else if (damager instanceof Player) {
            Player owner = (Player) damager;
            // Check if the owner has an active pet AND the victim is a valid target
            if (petManager.hasActivePet(owner.getUniqueId()) && victim instanceof LivingEntity) {
                // Don't make the pet attack itself
                if (victim.getUniqueId().equals(petManager.getActivePet(owner.getUniqueId()).getUniqueId())) {
                    return;
                }
                // Tell pet to attack the victim
                petManager.getTargetSelection().handlePetAggression(owner, victim);
            }
        }
    }

    // --- BUG FIX: Added handler for environmental damage (sunlight, drowning, etc.) ---
    @EventHandler
    public void onPetEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;

        LivingEntity victim = (LivingEntity) event.getEntity();

        // Only check for pets
        if (petManager.isPet(victim)) {
            EntityDamageEvent.DamageCause cause = event.getCause();

            // Cancel environmental damage
            if (cause == EntityDamageEvent.DamageCause.DROWNING ||
                    cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK || // Sunlight & Fire
                    cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.MELTING || // Snow Golem
                    cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.HOT_FLOOR) // Magma blocks
            {
                event.setCancelled(true);
            }
        }
    }
}