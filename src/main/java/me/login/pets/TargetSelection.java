package me.login.pets;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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

            if (mob.getTarget() != null && mob.getTarget().getUniqueId().equals(player.getUniqueId())) {
                mob.setTarget(null);
            }

            // --- Restricted Movement Logic ---
            // Only move freely if attacking
            if (mob.getTarget() == null) {
                Location playerLoc = player.getLocation();

                // Calculate position 3 blocks behind the player
                // We take direction, reverse it, multiply by 3, and add to player loc
                Vector direction = playerLoc.getDirection().setY(0).normalize().multiply(-3);
                Location targetSpot = playerLoc.clone().add(direction);

                // If distance to that spot > 1.5 blocks, move there
                double distToSpot = pet.getLocation().distanceSquared(targetSpot);
                if (distToSpot > 2.25) { // 1.5^2
                    mob.getPathfinder().moveTo(targetSpot, 1.3);
                }

                // Teleport if too far from player (e.g. 10 blocks)
                if (pet.getLocation().distanceSquared(playerLoc) > 100) {
                    pet.teleport(targetSpot);
                }
            }
            // If attacking (target != null), let default AI/Aggression handle movement
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