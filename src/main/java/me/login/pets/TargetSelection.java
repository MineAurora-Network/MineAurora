package me.login.pets;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Handles all Pet AI, including target selection, pathfinding, and aggression logic.
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

    public void handlePetAILogic(Player player, LivingEntity pet) {
        if (pet instanceof Mob) {
            Mob mob = (Mob) pet;

            // Prevent pet from targeting its owner
            if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(player.getUniqueId())) {
                mob.setTarget(null);
            }

            // --- FIX: Improved Follow Logic ---
            if (mob.getTarget() == null) {
                Location playerLoc = player.getLocation();
                double distanceSquared = pet.getLocation().distanceSquared(playerLoc);

                // Teleport if stuck or too far (15 blocks / 225 sq)
                if (distanceSquared > 225) {
                    pet.teleport(playerLoc);
                    return;
                }

                // Follow if distance > 3 blocks (9 sq)
                // We move directly to the player location to prevent "lagging behind" issues
                if (distanceSquared > 9) {
                    mob.getPathfinder().moveTo(playerLoc, 1.35); // Slightly increased speed for responsiveness
                }
            }
        }
    }

    public void handlePetAggression(Player owner, Entity target) {
        if (!petManager.hasActivePet(owner.getUniqueId()) || !(target instanceof LivingEntity)) return;

        LivingEntity pet = petManager.getActivePet(owner.getUniqueId());
        if (!(pet instanceof Mob)) return;

        Mob mob = (Mob) pet;

        if (target.getUniqueId().equals(owner.getUniqueId())) {
            mob.setTarget(null);
            return;
        }

        if (mob.getTarget() != null && mob.getTarget().isValid() && !mob.getTarget().isDead()) {
            return;
        }

        mob.setTarget((LivingEntity) target);
    }

    public void setPetTarget(Player owner, Entity target) {
        if (!petManager.hasActivePet(owner.getUniqueId()) || !(target instanceof LivingEntity)) return;

        LivingEntity pet = petManager.getActivePet(owner.getUniqueId());
        if (!(pet instanceof Mob)) return;

        Mob mob = (Mob) pet;
        if (target.getUniqueId().equals(owner.getUniqueId())) {
            mob.setTarget(null);
            return;
        }

        mob.setTarget((LivingEntity) target);
    }
}