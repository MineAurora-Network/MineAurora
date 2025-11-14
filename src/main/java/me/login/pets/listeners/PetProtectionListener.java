package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.PetsConfig;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public class PetProtectionListener implements Listener {

    private final PetManager petManager;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler; // Added for Amethyst Shard messages

    // --- THIS IS THE FIX ---
    public PetProtectionListener(PetManager petManager, PetsConfig config, PetMessageHandler messageHandler) {
        this.petManager = petManager;
        this.config = config;
        this.messageHandler = messageHandler; // <-- Now correctly assigned
    }
    // --- END FIX ---

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (petManager.isCapturing(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (petManager.isPet(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);

            LivingEntity pet = event.getEntity();
            UUID ownerId = petManager.getPetOwner(pet);
            if (ownerId != null) {
                String killerName = null;
                if (pet.getKiller() != null) {
                    killerName = pet.getKiller().getName();
                } else if (pet.getLastDamageCause() != null) {
                    if (pet.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                        Entity damager = ((EntityDamageByEntityEvent) pet.getLastDamageCause()).getDamager();
                        killerName = damager instanceof Player ? ((Player)damager).getName() : damager.getType().name();
                    } else {
                        killerName = pet.getLastDamageCause().getCause().name();
                    }
                }

                petManager.onPetDeath(ownerId, pet.getType(), killerName);
            }
        }
    }

    @EventHandler
    public void onPetTargetOwner(EntityTargetLivingEntityEvent event) {
        if (!petManager.isPet(event.getEntity())) {
            return;
        }
        if (!(event.getTarget() instanceof Player)) {
            return;
        }

        Player target = (Player) event.getTarget();
        UUID ownerId = petManager.getPetOwner(event.getEntity());

        if (target.getUniqueId().equals(ownerId)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // 1. Friendly Fire & Logic
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

            // Case B: Pet hitting Owner -> Cancel
            if (petManager.isPet(damager)) {
                if (victim instanceof Player) {
                    if (petManager.getPetOwner(damager).equals(victim.getUniqueId())) {
                        event.setCancelled(true);
                        return;
                    }
                }

                // Custom Damage Calculation
                if (petManager.isPet(damager)) {
                    var ownerId = petManager.getPetOwner(damager);
                    var petData = petManager.getPet(ownerId, damager.getType());
                    if (petData != null) {
                        event.setDamage(config.getDamage(damager.getType(), petData.getLevel()));

                        if (victim.getHealth() - event.getFinalDamage() <= 0) {
                            Player owner = petManager.getPlugin().getServer().getPlayer(ownerId);
                            if (owner != null) {
                                petManager.addXp(owner, petData, 20.0);
                            }
                        }
                    }
                }
            }
        }

        // 2. Aggression System
        if (event.getDamager() instanceof Player && event.getEntity() instanceof LivingEntity) {
            Player player = (Player) event.getDamager();
            if (petManager.hasActivePet(player.getUniqueId())) {
                petManager.handlePetAggression(player, event.getEntity());
            }
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            Player player = (Player) event.getEntity();
            if (petManager.hasActivePet(player.getUniqueId())) {
                LivingEntity attacker = (LivingEntity) event.getDamager();
                if (!petManager.isPet(attacker)) {
                    petManager.handlePetAggression(player, attacker);
                }
            }
        }
    }

    @EventHandler
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (petManager.isPet(event.getEntity())) {
            event.setCancelled(true);
            event.getLocation().getWorld().createExplosion(event.getLocation(), 4F, false, false, event.getEntity());
            event.getLocation().getNearbyEntities(4, 4, 4).forEach(e -> {
                if (e instanceof LivingEntity && !e.equals(event.getEntity()) && !e.getUniqueId().equals(petManager.getPetOwner(event.getEntity()))) {
                    ((LivingEntity) e).damage(10.0, event.getEntity());
                }
            });
        }
    }

    @EventHandler
    public void onPetFeed(PlayerInteractEntityEvent event) {
        if (!petManager.isPet(event.getRightClicked())) return; // Not a pet

        Player player = event.getPlayer();
        if (petManager.getPetOwner(event.getRightClicked()).equals(player.getUniqueId())) {

            // FIXED: Get item from the hand that clicked
            ItemStack item = (event.getHand() == EquipmentSlot.HAND) ?
                    player.getInventory().getItemInMainHand() :
                    player.getInventory().getItemInOffHand();

            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("mineaurora", "pet_fruit_id"), PersistentDataType.STRING)) {
                    petManager.feedPet(player, item);
                    event.setCancelled(true); // Prevent default behavior
                }
            }
        }
    }

    @EventHandler
    public void onPetTarget(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return; // Only main hand

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        // Check for Amethyst Shard
        if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
            if (item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey("mineaurora", "pet_utility_id"), PersistentDataType.STRING)) {
                String utilId = item.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("mineaurora", "pet_utility_id"), PersistentDataType.STRING);

                if ("amethyst_shard".equals(utilId)) {
                    if (!(event.getRightClicked() instanceof LivingEntity)) {
                        messageHandler.sendPlayerMessage(player, "<red>You can only target living entities.</red>");
                        return;
                    }

                    if (!petManager.hasActivePet(player.getUniqueId())) {
                        messageHandler.sendPlayerMessage(player, "<red>You must have an active pet summoned!</red>");
                        return;
                    }

                    LivingEntity target = (LivingEntity) event.getRightClicked();
                    if (petManager.isPet(target) && petManager.getPetOwner(target).equals(player.getUniqueId())) {
                        messageHandler.sendPlayerMessage(player, "<red>You cannot target your own pet.</red>");
                        return;
                    }

                    // Set pet's target
                    petManager.setPetTarget(player, target);
                    item.setAmount(item.getAmount() - 1);
                    messageHandler.sendPlayerMessage(player, "<green>Your pet is now targeting " + target.getName() + "!</green>");
                    event.setCancelled(true);
                }
            }
        }
    }
}