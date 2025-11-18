package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import me.login.pets.data.Pet;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
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
        this.attributeKey = new NamespacedKey(petManager.getPlugin(), "pet_attribute_id");
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity killed = event.getEntity();
        if (killed.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
            EntityDamageByEntityEvent damageEvent = (EntityDamageByEntityEvent) killed.getLastDamageCause();
            Entity damager = damageEvent.getDamager();

            if (petManager.isPet(damager)) {
                UUID ownerUuid = petManager.getPetOwner(damager);
                if (ownerUuid == null) return;

                Player owner = petManager.getPlugin().getServer().getPlayer(ownerUuid);
                Pet pet = petManager.getPet(ownerUuid, damager.getType());

                if (owner != null && pet != null) {
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
        if (exploder.getType() == org.bukkit.entity.EntityType.CREEPER && petManager.isPet(exploder)) {
            event.setCancelled(true);
            event.setYield(0f);

            LivingEntity petCreeper = (LivingEntity) exploder;
            UUID ownerUuid = petManager.getPetOwner(petCreeper);

            petCreeper.getWorld().spawnParticle(Particle.EXPLOSION, petCreeper.getLocation(), 3);

            for (Entity entity : petCreeper.getNearbyEntities(4, 4, 4)) {
                if (entity instanceof LivingEntity && entity.getUniqueId() != ownerUuid && entity.getUniqueId() != petCreeper.getUniqueId()) {
                    if (!petManager.isPet(entity)) {
                        LivingEntity target = (LivingEntity) entity;
                        double damage = config.getCreeperExplosionDamage();
                        target.damage(damage, petCreeper);
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        // --- Pet Dealing Damage (Check for Debuff Shards) ---
        if (petManager.isPet(damager) && victim instanceof LivingEntity) {
            UUID ownerUuid = petManager.getPetOwner(damager);
            if (ownerUuid != null) {
                Pet pet = petManager.getPet(ownerUuid, damager.getType());
                if (pet != null) {
                    String attr = getAttributeId(pet.getAttributeContent());
                    LivingEntity target = (LivingEntity) victim;

                    // Buff: Poison Shard
                    if ("posion_shard".equals(attr)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0)); // 5s
                    }
                    // Buff: Weakness Shard (Weakness 3 = Amplifier 2)
                    else if ("weakness_shard".equals(attr)) {
                        target.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 100, 2)); // 5s
                    }
                }
            }
        }

        if (petManager.isPet(victim)) {
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

        if (petManager.isPet(victim) && petManager.isPet(damager)) {
            UUID owner1 = petManager.getPetOwner(victim);
            UUID owner2 = petManager.getPetOwner(damager);
            if (owner1 != null && owner1.equals(owner2)) {
                event.setCancelled(true);
            }
        }
    }

    // --- NEW: Handle Healer Shard (Owner Taking Damage) ---
    @EventHandler
    public void onOwnerTakeDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;

        Player player = (Player) event.getEntity();

        // If owner has an active pet
        if (petManager.hasActivePet(player.getUniqueId())) {
            LivingEntity activePetEntity = petManager.getActivePet(player.getUniqueId());
            Pet petData = petManager.getPet(player.getUniqueId(), activePetEntity.getType());

            if (petData != null) {
                String attr = getAttributeId(petData.getAttributeContent());
                // Buff: Healer Shard (Reduce damage by 20%)
                if ("healer_shard".equals(attr)) {
                    double originalDamage = event.getDamage();
                    double reducedDamage = originalDamage * 0.8;
                    event.setDamage(reducedDamage);
                    // Optional: visual or healing message?
                    // player.sendMessage("Pet reduced damage!");
                }
            }
        }
    }

    @EventHandler
    public void onOwnerCombat(EntityDamageByEntityEvent event) {
        if (event.isCancelled()) return;
        Entity damager = event.getDamager();
        Entity victim = event.getEntity();
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

    @EventHandler
    public void onPetEnvironmentDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity victim = (LivingEntity) event.getEntity();
        if (petManager.isPet(victim)) {
            EntityDamageEvent.DamageCause cause = event.getCause();
            if (cause == EntityDamageEvent.DamageCause.DROWNING ||
                    cause == EntityDamageEvent.DamageCause.SUFFOCATION ||
                    cause == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    cause == EntityDamageEvent.DamageCause.FIRE ||
                    cause == EntityDamageEvent.DamageCause.MELTING ||
                    cause == EntityDamageEvent.DamageCause.LAVA ||
                    cause == EntityDamageEvent.DamageCause.HOT_FLOOR)
            {
                event.setCancelled(true);
            }
        }
    }

    private String getAttributeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(attributeKey, PersistentDataType.STRING);
    }
}