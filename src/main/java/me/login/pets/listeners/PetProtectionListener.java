package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.entity.Projectile; // --- FIXED: Added import ---
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.UUID;

/**
 * Listener for handling pet combat behavior.
 * 1. Makes a pet attack its owner's attacker.
 * 2. Prevents a hostile pet from targeting its owner.
 * 3. Handles pet death and cooldowns.
 * 4. Applies custom damage and special abilities.
 */
public class PetProtectionListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig petsConfig;

    public PetProtectionListener(PetManager petManager, PetsConfig petsConfig) {
        this.petManager = petManager;
        this.petsConfig = petsConfig;
    }

    /**
     * Triggers the pet to attack its owner's attacker.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onOwnerDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return; // Owner is not a player
        }

        Player owner = (Player) event.getEntity();
        LivingEntity attacker = null;

        if (event.getDamager() instanceof LivingEntity) {
            attacker = (LivingEntity) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof LivingEntity) {
                attacker = (LivingEntity) projectile.getShooter();
            }
        }

        if (attacker == null || attacker.equals(owner)) {
            return; // Attacker is not something a pet can target
        }

        // Get the owner's active pet
        LivingEntity pet = petManager.getActivePet(owner.getUniqueId());

        if (pet == null || !(pet instanceof Mob) || pet.equals(attacker)) {
            return; // No active pet, or pet is not a Mob, or pet is attacking owner
        }

        // Make the pet target the attacker
        ((Mob) pet).setTarget(attacker);
    }

    /**
     * Prevents a hostile pet from targeting its owner or other pets.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Mob) || !(event.getTarget() instanceof LivingEntity)) {
            return;
        }

        Mob petMob = (Mob) event.getEntity();
        LivingEntity target = event.getTarget();

        // Check if the entity is a pet
        UUID ownerUuid = petManager.getPetOwner(petMob);
        if (ownerUuid == null) {
            return;
        }

        // If the pet's target is its owner, cancel
        if (target.getUniqueId().equals(ownerUuid)) {
            event.setCancelled(true);
            return;
        }

        // If the pet's target is ANOTHER player's pet, cancel
        if (petManager.isPet(target)) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles pet death, applying cooldowns.
     */
    @EventHandler
    public void onPetDeath(EntityDeathEvent event) {
        LivingEntity pet = event.getEntity();
        UUID ownerUuid = petManager.getPetOwner(pet);

        if (ownerUuid == null) {
            return; // Not a pet
        }

        // Prevent drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        // Check if death was by command (e.g., /killall)
        EntityDamageEvent lastDamage = pet.getLastDamageCause();
        if (lastDamage == null || lastDamage.getCause() == EntityDamageEvent.DamageCause.VOID || lastDamage.getCause() == EntityDamageEvent.DamageCause.CUSTOM) {
            // Died by /kill or void, don't apply cooldown
            petManager.despawnPet(ownerUuid, false); // Just clean up maps
            Player owner = Bukkit.getPlayer(ownerUuid);
            if (owner != null) {
                petManager.getPlugin().getLogger().info("Pet " + pet.getType() + " for " + owner.getName() + " was removed without cooldown (command/void).");
            }
            return;
        }

        // It was a "real" death, apply cooldown
        petManager.onPetDeath(ownerUuid, pet.getType());
    }

    /**
     * Handles custom pet damage and special abilities.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPetAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Mob) && !(event.getDamager() instanceof Projectile)) {
            return; // Attacker is not a pet or pet's projectile
        }

        UUID ownerUuid = null;
        EntityType petType = null;
        LivingEntity pet = null;

        if (event.getDamager() instanceof Mob) {
            pet = (Mob) event.getDamager();
            ownerUuid = petManager.getPetOwner(pet);
            petType = pet.getType();
        } else { // It's a projectile
            Projectile projectile = (Projectile) event.getDamager();
            if (projectile.getShooter() instanceof LivingEntity) {
                pet = (LivingEntity) projectile.getShooter();
                ownerUuid = petManager.getPetOwner(pet);
                petType = pet.getType();
            }
        }

        if (ownerUuid == null) {
            return; // Attacker is not a pet
        }

        // It's a pet! Apply custom damage from config
        double damage = petsConfig.getPetDamage(petType);
        event.setDamage(damage);

        // Apply special abilities
        if (event.getDamager() instanceof Mob) { // Only for melee attacks
            handleMeleeAbilities(petType, (LivingEntity) event.getEntity(), pet);
        } else { // Handle projectile abilities
            handleProjectileAbilities(petType, (Projectile) event.getDamager(), pet);
        }
    }

    private void handleMeleeAbilities(EntityType petType, LivingEntity target, LivingEntity pet) {
        switch (petType) {
            case WARDEN:
                // Apply sonic boom particle/sound
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0F, 1.0F);
                target.getWorld().spawnParticle(Particle.SONIC_BOOM, target.getLocation().add(0, 1, 0), 1);
                target.setVelocity(target.getVelocity().add(new Vector(0, 0.5, 0))); // Knockup
                break;
            case WITHER:
                // Apply wither effect
                target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1)); // 5 seconds, level 1
                // --- FIXED: Particle name ---
                target.getWorld().spawnParticle(Particle.LARGE_SMOKE, target.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.01);
                break;
            case ENDER_DRAGON:
                // Knockback and particle effect
                Vector knockback = target.getLocation().toVector().subtract(pet.getLocation().toVector()).normalize().multiply(1.5).setY(0.5);
                target.setVelocity(knockback);
                // --- FIXED: Particle name ---
                target.getWorld().spawnParticle(Particle.EXPLOSION, target.getLocation(), 1);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.0F, 1.0F);
                break;
            case RAVAGER:
                // Stronger knockback and sound
                Vector ravagerKnockback = target.getLocation().toVector().subtract(pet.getLocation().toVector()).normalize().multiply(2.0).setY(0.3);
                target.setVelocity(ravagerKnockback);
                target.getWorld().playSound(target.getLocation(), Sound.ENTITY_RAVAGER_ATTACK, 1.0F, 1.0F);
                break;
        }
    }

    private void handleProjectileAbilities(EntityType petType, Projectile projectile, LivingEntity pet) {
        switch (petType) {
            case BLAZE:
                projectile.setFireTicks(100); // 5 seconds of fire
                projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(), 10, 0.1, 0.1, 0.1, 0.01);
                break;
            case GHAST:
                // --- FIXED: Cast to Fireball ---
                if (projectile instanceof Fireball) {
                    ((Fireball) projectile).setIsIncendiary(true); // Makes it explosive
                }
                projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(), 10, 0.1, 0.1, 0.1, 0.01);
                projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_GHAST_SHOOT, 1.0F, 1.0F);
                break;
            case WITHER:
                // --- FIXED: Cast to WitherSkull ---
                if (projectile instanceof WitherSkull) {
                    ((WitherSkull) projectile).setCharged(true); // Make it a blue, more explosive skull
                }
                // --- FIXED: Particle name ---
                projectile.getWorld().spawnParticle(Particle.SMOKE, projectile.getLocation(), 20, 0.1, 0.1, 0.1, 0.05);
                projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1.0F, 1.0F);
                break;
        }
    }

}