package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

import java.util.UUID;

public class PetCombatListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;

    public PetCombatListener(PetManager petManager, PetsConfig config, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.config = config;
        this.messageHandler = messageHandler;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity killed = event.getEntity();
        if (killed.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) killed.getLastDamageCause();
            Entity damager = damageEvent.getDamager();

            // Check if the killer was a pet
            if (petManager.isPet(damager)) {
                UUID ownerUuid = petManager.getPetOwner(damager);
                if (ownerUuid == null) return;

                Player owner = petManager.getPlugin().getServer().getPlayer(ownerUuid);
                Pet pet = petManager.getPet(ownerUuid, damager.getType());

                if (owner != null && pet != null) {
                    // Get XP from config
                    double xp = config.getXpForKill(killed.getType());
                    if (xp > 0) {
                        petManager.addXp(owner, pet, xp);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        Entity exploder = event.getEntity();

        // Check if it's a CREEPER pet
        if (exploder.getType() == org.bukkit.entity.EntityType.CREEPER && petManager.isPet(exploder)) {
            // It's a pet creeper!

            // 1. Prevent pet from dying
            event.setCancelled(true);

            // 2. Prevent block damage
            event.setYield(0f);

            // 3. Manually create explosion effect and damage
            LivingEntity petCreeper = (LivingEntity) exploder;
            UUID ownerUuid = petManager.getPetOwner(petCreeper);

            // Create visual explosion
            petCreeper.getWorld().createExplosion(petCreeper.getLocation(), 2.0f, false, false); // 2.0f is power

            // Get nearby entities and damage them
            for (Entity entity : petCreeper.getNearbyEntities(4, 4, 4)) {
                if (entity instanceof LivingEntity && entity.getUniqueId() != ownerUuid && entity.getUniqueId() != petCreeper.getUniqueId()) {
                    if (!petManager.isPet(entity)) { // Don't damage other pets
                        LivingEntity target = (LivingEntity) entity;
                        // You'll need to add a "creeper_explosion_damage" to config
                        double damage = config.getCreeperExplosionDamage();
                        target.damage(damage, petCreeper);
                    }
                }
            }

            // Trigger cooldown? (Optional, good idea)
        }
    }
}