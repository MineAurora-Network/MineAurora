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

            // --- FIX: Relaxed Movement Logic ---
            // Only move if too far away to prevent snapping behind player constantly
            if (mob.getTarget() == null) {
                Location playerLoc = player.getLocation();
                double distanceSquared = pet.getLocation().distanceSquared(playerLoc);

                // Teleport if extremely far (stuck or new chunk)
                if (distanceSquared > 400) { // 20 blocks
                    pet.teleport(playerLoc);
                    return;
                }

                // Calculate "sweet spot" behind player
                Vector direction = playerLoc.getDirection().setY(0).normalize().multiply(-3);
                Location targetSpot = playerLoc.clone().add(direction);

                // Only start moving if distance to player is > 5 blocks
                // This allows the player to walk up to the pet and right-click it
                if (distanceSquared > 25) {
                    mob.getPathfinder().moveTo(targetSpot, 1.3);
                }
                // If within 5 blocks, just idle/look at player (handled by vanilla 'look at player' goal usually)
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