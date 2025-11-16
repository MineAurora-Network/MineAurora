package me.login.pets;

import org.bukkit.entity.*;

/**
 * Handles all Pet AI, including target selection, pathfinding, and aggression logic.
 * This class centralizes the AI logic previously found in PetManager and PetProtectionListener.
 */
public class TargetSelection {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetsLogger logger;

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
        if (pet instanceof Mob) {
            Mob mob = (Mob) pet;
            double distSq = pet.getLocation().distanceSquared(player.getLocation());

            if (distSq > 400) { // 20 blocks
                PetDebug.debugOwner(player, PetDebug.Cat.AI, pet.getType().name() + " teleporting to owner (too far).");
                pet.teleport(player.getLocation());
                mob.setTarget(null);
            }
            else if (distSq > 16) { // 4 blocks
                if (mob.getTarget() == null || distSq > 225) { // 15 blocks
                    PetDebug.debugOwner(player, PetDebug.Cat.AI, pet.getType().name() + " following owner (no target).");
                    mob.setTarget(null);
                    mob.getPathfinder().moveTo(player, 1.2);
                }
            }
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
        if (pet instanceof Mob) {
            Mob mob = (Mob) pet;
            if (mob.getTarget() != null && mob.getTarget().isValid() && !mob.getTarget().isDead()) {
                PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " already has target, not switching.");
                return;
            }

            PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " aggression triggered. New target: " + target.getName());
            mob.setTarget((LivingEntity) target);
        }
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

        if (pet instanceof Mob) {
            PetDebug.debugOwner(owner, PetDebug.Cat.TARGET, pet.getType().name() + " manually setting target to " + target.getName());
            ((Mob) pet).setTarget((LivingEntity) target);
        }
    }
}