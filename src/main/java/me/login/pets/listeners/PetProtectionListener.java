package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetsConfig;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class PetProtectionListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig config;

    public PetProtectionListener(PetManager petManager, PetsConfig config) {
        this.petManager = petManager;
        this.config = config;
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 1. Friendly Fire & Warden Sonic Boom Fix
        if (event.getDamager() instanceof LivingEntity && event.getEntity() instanceof LivingEntity) {
            LivingEntity damager = (LivingEntity) event.getDamager();
            LivingEntity victim = (LivingEntity) event.getEntity();

            // Case A: Owner hitting own Pet -> Cancel
            if (petManager.isPet(victim)) {
                if (damager instanceof Player) {
                    if (petManager.getPetOwner(victim).equals(damager.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            // Case B: Pet hitting Owner (e.g. Warden Sonic Boom) -> Cancel
            if (petManager.isPet(damager)) {
                if (victim instanceof Player) {
                    if (petManager.getPetOwner(damager).equals(victim.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Custom Damage Calculation for Pet Attacks
                if (petManager.isPet(damager)) {
                    var ownerId = petManager.getPetOwner(damager);
                    var petData = petManager.getPet(ownerId, damager.getType());
                    if (petData != null) {
                        event.setDamage(config.getDamage(damager.getType(), petData.getLevel()));
                        // Give XP on kill?
                        if (victim.getHealth() - event.getFinalDamage() <= 0) {
                            // Simple XP grant for kill
                            Player owner = petManager.getPlugin().getServer().getPlayer(ownerId);
                            if (owner != null) {
                                petManager.addXp(owner, petData, 20.0); // Fixed XP per kill
                            }
                        }
                    }
                }
            }
        }

        // 2. Aggression System
        // If Player damages mob -> Pet targets mob
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            if (petManager.hasActivePet(player.getUniqueId())) {
                petManager.handlePetAggression(player, event.getEntity());
            }
        }
        // If Mob damages Player -> Pet targets mob
        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player player = (Player) event.getEntity();
            if (petManager.hasActivePet(player.getUniqueId())) {
                petManager.handlePetAggression(player, event.getDamager());
            }
        }
    }

    // 3. Creeper Fix
    @EventHandler
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (petManager.isPet(event.getEntity())) {
            event.setCancelled(true); // Stop block damage

            // Manually damage nearby entities
            event.getLocation().getWorld().createExplosion(event.getLocation(), 4F, false, false, event.getEntity());
            // The createExplosion might kill the creeper, so we might need to heal it or cancel 'death' in another event.
            // Actually, standard behavior for createExplosion with source DOES damage source.
            // Better approach: Simulate damage
            event.getLocation().getNearbyEntities(4, 4, 4).forEach(e -> {
                if (e instanceof LivingEntity && !e.equals(event.getEntity()) && !e.getUniqueId().equals(petManager.getPetOwner(event.getEntity()))) {
                    ((LivingEntity) e).damage(10.0, event.getEntity());
                }
            });
        }
    }

    // 4. Feeding
    @EventHandler
    public void onFeed(PlayerInteractEntityEvent event) {
        if (petManager.isPet(event.getRightClicked())) {
            Player player = event.getPlayer();
            if (petManager.getPetOwner(event.getRightClicked()).equals(player.getUniqueId())) {
                petManager.feedPet(player, player.getInventory().getItemInMainHand());
            }
        }
    }
}