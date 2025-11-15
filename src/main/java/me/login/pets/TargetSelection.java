package me.login.pets;

import me.login.pets.data.Pet;
import org.bukkit.Bukkit;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Handles all Pet AI, including target selection, pathfinding, and aggression logic.
 * This class centralizes the AI logic previously found in PetManager and PetProtectionListener.
 */
public class TargetSelection {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetsLogger logger; // Still needed for Discord logging (e.g., in PetManager)

    // --- REMOVED ---
    // private final Map<UUID, UUID> passivePetTargets = new ConcurrentHashMap<>();
    // private final Map<UUID, Long> passivePetAttackCooldowns = new ConcurrentHashMap<>();

    public TargetSelection(PetManager petManager, PetsConfig config, PetsLogger logger) {
        this.petManager = petManager;
        this.config = config;
        this.logger = logger;
    }

    /**
     * Main AI logic loop called by the pet's follow task in PetManager.
     * @param player The owner of the pet.
     * @param pet The pet entity.
     */
    public void handlePetAILogic(Player player, LivingEntity pet) {
        // All listed pets are Mobs, so we only need this logic.
        if (pet instanceof Mob) {
            Mob mob = (Mob) pet;
            double distSq = pet.getLocation().distanceSquared(player.getLocation());

            if (distSq > 400) { // 20 blocks
                // --- UPDATED: Use PetDebug ---
                PetDebug.debugOwner(player, PetDebug.Cat.AI, pet.getType().name() + " teleporting to owner (too far).");
                pet.teleport(player.getLocation());
                mob.setTarget(null);
            }
            else if (distSq > 16) { // 4 blocks
                // Only follow player if the pet has no target, or if it's too far from its target
                if (mob.getTarget() == null || distSq > 225) { // 15 blocks
                    // --- UPDATED: Use PetDebug ---
                    PetDebug.debugOwner(player, PetDebug.Cat.AI, pet.getType().name() + " following owner (no target).");
                    mob.setTarget(null);
                    mob.getPathfinder().moveTo(player, 1.2);
                }
            }
            // --- REMOVED ---
            // else if (pet instanceof Creature) { ... }
            // else { ... }
        }
    }

    /**
     * Handles a pet's aggression when its owner is attacked or attacks.
     * @param owner The owner of the pet.
     * @param target The entity to target.
     */
    public void handlePetAggression(Player owner, Entity target) {
        if (!petManager.hasActivePet(owner.getUniqueId()) || !(target instanceof LivingEntity)) return;
        LivingEntity pet = petManager.getActivePet(owner.getUniqueId());

        // --- NEW: Target Locking ---
        // Only set target if the pet is a Mob AND doesn't already have a target
        if (pet instanceof Mob) {
            Mob mob = (Mob) pet;
            if (mob.getTarget() != null && mob.getTarget().isValid() && !mob.getTarget().isDead()) {
                PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " already has target, not switching.");
                return; // Already locked on a target
            }

            // --- UPDATED: Use PetDebug ---
            PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " aggression triggered. New target: " + target.getName());
            mob.setTarget((LivingEntity) target);
        }
        // --- REMOVED ---
        // else if (pet instanceof Creature) { ... }
    }

    /**
     * Sets a pet's target manually (e.g., via Amethyst Shard).
     * This bypasses the target lock.
     * @param owner The owner of the pet.
     * @param target The entity to target.
     */
    public void setPetTarget(Player owner, Entity target) {
        if (!petManager.hasActivePet(owner.getUniqueId()) || !(target instanceof LivingEntity)) return;
        LivingEntity pet = petManager.getActivePet(owner.getUniqueId());

        // --- UPDATED: Use PetDebug and only check for Mob ---
        if (pet instanceof Mob) {
            PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " manually setting target to " + target.getName());
            ((Mob) pet).setTarget((LivingEntity) target);
        }
        // --- REMOVED ---
        // else if (pet instanceof Creature) { ... }
    }

    /**
     * Clears the target data for a specific pet.
     * (No longer needed as we use the mob's built-in target system)
     * @param petUuid The UUID of the pet.
     */
    public void clearPetTarget(UUID petUuid) {
        // --- REMOVED ---
        // logger.petDebug(this.config, "Clearing target data for pet " + petUuid);
        // passivePetTargets.remove(petUuid);
        // passivePetAttackCooldowns.remove(petUuid);
    }

    /**
     * Clears all target data. Used on shutdown.
     * (No longer needed)
     */
    public void clearAllTargets() {
        // --- REMOVED ---
        // logger.petDebug(this.config, "Clearing all pet target data.");
        // passivePetTargets.clear();
        // passivePetAttackCooldowns.clear();
    }
}