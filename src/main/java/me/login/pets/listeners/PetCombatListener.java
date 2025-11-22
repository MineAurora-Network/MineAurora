package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.UUID;

public class PetCombatListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;
    private final NamespacedKey attributeKey;

    public PetCombatListener(PetManager petManager, PetsConfig config, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.config = config;
        this.messageHandler = messageHandler;
        this.attributeKey = new NamespacedKey("mineaurora", "pet_attribute_id");
    }

    @EventHandler
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (petManager.isPet(event.getEntity())) {
            UUID ownerUuid = petManager.getPetOwner(event.getEntity());
            if (ownerUuid != null && event.getTarget() != null) {
                if (event.getTarget().getUniqueId().equals(ownerUuid)) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity killed = event.getEntity();

        // Owner Death Logic
        if (killed instanceof Player) {
            Player player = (Player) killed;
            if (petManager.hasActivePet(player.getUniqueId())) {
                petManager.despawnPet(player.getUniqueId(), false);
            }
        }

        // XP Logic
        if (killed.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) killed.getLastDamageCause();
            Entity damager = damageEvent.getDamager();

            if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) {
                damager = (Entity) ((Projectile) damager).getShooter();
            }

            Player owner = null;
            Pet pet = null;

            if (petManager.isPet(damager)) {
                UUID ownerUuid = petManager.getPetOwner(damager);
                if (ownerUuid != null) {
                    owner = petManager.getPlugin().getServer().getPlayer(ownerUuid);
                    pet = petManager.getPet(ownerUuid, damager.getType());
                }
            }
            else if (damager instanceof Player) {
                Player playerDamager = (Player) damager;
                if (petManager.hasActivePet(playerDamager.getUniqueId())) {
                    owner = playerDamager;
                    LivingEntity activePetEntity = petManager.getActivePet(owner.getUniqueId());
                    if (activePetEntity != null) {
                        pet = petManager.getPet(owner.getUniqueId(), activePetEntity.getType());
                    }
                }
            }

            if (owner != null && pet != null) {
                double xp = 0;
                if (killed.getType() == EntityType.PLAYER) {
                    xp = 7.0;
                } else if (killed instanceof Mob) {
                    xp = 5.0;
                }

                if (xp > 0) {
                    petManager.addXp(owner, pet, xp);
                }
            }
        }

        if (petManager.isPet(killed)) {
            UUID ownerUuid = petManager.getPetOwner(killed);
            if (ownerUuid != null) {
                String killerName = null;
                if (killed.getKiller() != null) killerName = killed.getKiller().getName();
                petManager.onPetDeath(ownerUuid, killed.getType(), killerName);
                event.getDrops().clear();
                event.setDroppedExp(0);
            }
        }
    }

    // --- FIX: Blaze Rain Damage Prevention ---
    @EventHandler
    public void onPetEnvironmentDamage(EntityDamageEvent event) {
        if (!petManager.isPet(event.getEntity())) return;
        if (event.getEntity().getType() != EntityType.BLAZE) return;

        // Cancel damage from Water/Rain
        if (event.getCause() == EntityDamageEvent.DamageCause.DROWNING) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onCreeperPrime(ExplosionPrimeEvent event) {
        if (petManager.isPet(event.getEntity()) && event.getEntity().getType() == EntityType.CREEPER) {
            event.setCancelled(true);
            Creeper creeper = (Creeper) event.getEntity();
            UUID ownerUuid = petManager.getPetOwner(creeper);

            creeper.getWorld().spawnParticle(Particle.EXPLOSION, creeper.getLocation(), 1);
            creeper.getWorld().playSound(creeper.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);

            double damage = config.getCreeperExplosionDamage();
            for (Entity nearby : creeper.getNearbyEntities(4, 4, 4)) {
                if (nearby instanceof LivingEntity && !nearby.getUniqueId().equals(creeper.getUniqueId())) {
                    if (ownerUuid != null && nearby.getUniqueId().equals(ownerUuid)) continue;
                    if (petManager.isPet(nearby) && ownerUuid != null && ownerUuid.equals(petManager.getPetOwner(nearby))) continue;

                    ((LivingEntity) nearby).damage(damage, creeper);
                }
            }
            creeper.setFuseTicks(0);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        if (petManager.isPet(event.getEntity())) {
            event.setCancelled(true);
            event.setYield(0f);
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) {
            if (petManager.isPet(event.getDamager())) {
                LivingEntity pet = (LivingEntity) event.getDamager();
                if (pet instanceof Mob) {
                    ((Mob) pet).setTarget(null);
                }
            }
            return;
        }

        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) {
            damager = (Entity) ((Projectile) damager).getShooter();
        }

        if (petManager.isPet(damager) && victim instanceof LivingEntity) {
            UUID ownerUuid = petManager.getPetOwner(damager);
            if (ownerUuid != null) {
                Player owner = petManager.getPlugin().getServer().getPlayer(ownerUuid);
                LivingEntity target = (LivingEntity) victim;
                Pet pet = petManager.getPet(ownerUuid, damager.getType());
                LivingEntity petEntity = (LivingEntity) damager;

                if (pet != null) {
                    String attr = getAttributeId(pet.getAttributeContent());
                    // FIX: Updated duration to 3s (60 ticks)
                    if ("posion_shard".equals(attr)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0));
                    } else if ("weakness_shard".equals(attr)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 60, 2)); // Amplifier 2 = Weakness III
                    }
                }

                if (owner != null && owner.isOnline() && pet != null) {
                    petManager.sendPetActionBar(owner, pet, petEntity, target);
                }
            }
        }

        if (petManager.isPet(victim) && petManager.isPet(damager)) {
            UUID owner1 = petManager.getPetOwner(victim);
            UUID owner2 = petManager.getPetOwner(damager);
            if (owner1 != null && owner1.equals(owner2)) {
                event.setCancelled(true);
            }
        } else if (petManager.isPet(victim)) {
            if (petManager.isCapturing(victim.getUniqueId())) {
                event.setCancelled(true);
                return;
            }
            if (damager instanceof Player) {
                Player playerDamager = (Player) damager;
                UUID ownerUuid = petManager.getPetOwner(victim);
                if (ownerUuid != null && ownerUuid.equals(playerDamager.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        } else if (petManager.isPet(damager)) {
            if (victim instanceof Player) {
                Player playerVictim = (Player) victim;
                UUID ownerUuid = petManager.getPetOwner(damager);
                if (ownerUuid != null && ownerUuid.equals(playerVictim.getUniqueId())) {
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onOwnerTakeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();

        if (petManager.hasActivePet(player.getUniqueId())) {
            LivingEntity activePetEntity = petManager.getActivePet(player.getUniqueId());
            Pet petData = petManager.getPet(player.getUniqueId(), activePetEntity.getType());

            if (petData != null) {
                String attr = getAttributeId(petData.getAttributeContent());
                if ("healer_shard".equals(attr)) {
                    double originalDamage = event.getDamage();
                    double reducedDamage = originalDamage * 0.8;
                    event.setDamage(reducedDamage);
                }
            }
        }
    }

    @EventHandler
    public void onOwnerCombat(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (damager instanceof Projectile && ((Projectile) damager).getShooter() instanceof Entity) {
            damager = (Entity) ((Projectile) damager).getShooter();
        }

        if (victim instanceof Player) {
            Player owner = (Player) victim;
            if (petManager.hasActivePet(owner.getUniqueId()) && damager instanceof LivingEntity) {
                if (damager.getUniqueId().equals(petManager.getActivePet(owner.getUniqueId()).getUniqueId())) return;
                petManager.getTargetSelection().handlePetAggression(owner, damager);
            }
        } else if (damager instanceof Player) {
            Player owner = (Player) damager;
            if (petManager.hasActivePet(owner.getUniqueId()) && victim instanceof LivingEntity) {
                if (victim.getUniqueId().equals(petManager.getActivePet(owner.getUniqueId()).getUniqueId())) return;
                petManager.getTargetSelection().handlePetAggression(owner, victim);
            }
        }
    }

    private String getAttributeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(attributeKey, PersistentDataType.STRING);
    }
}