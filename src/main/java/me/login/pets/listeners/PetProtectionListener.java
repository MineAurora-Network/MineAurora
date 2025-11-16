package me.login.pets.listeners;

import me.login.pets.PetManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.UUID;

public class PetProtectionListener implements Listener {

    private final PetManager petManager;

    public PetProtectionListener(PetManager petManager) {
        this.petManager = petManager;
    }

    @EventHandler
    public void onPetDamage(EntityDamageByEntityEvent event) {
        Entity victim = event.getEntity();
        Entity damager = event.getDamager();

        if (petManager.isPet(victim)) {
            // Case 1: Player is damaging a pet
            if (damager instanceof Player) {
                Player playerDamager = (Player) damager;
                UUID ownerUuid = petManager.getPetOwner(victim);

                if (ownerUuid != null && ownerUuid.equals(playerDamager.getUniqueId())) {
                    // Owner is damaging their own pet
                    event.setCancelled(true);
                }
                // Other players can damage the pet, so we don't cancel
            }
        } else if (petManager.isPet(damager)) {
            // Case 2: Pet is damaging a player
            if (victim instanceof Player) {
                Player playerVictim = (Player) victim;
                UUID ownerUuid = petManager.getPetOwner(damager);

                if (ownerUuid != null && ownerUuid.equals(playerVictim.getUniqueId())) {
                    // Pet is somehow damaging its own owner
                    event.setCancelled(true);
                }
            }
        }

        // Case 3: Pet damaging another pet (optional, but good to prevent)
        if (petManager.isPet(victim) && petManager.isPet(damager)) {
            UUID owner1 = petManager.getPetOwner(victim);
            UUID owner2 = petManager.getPetOwner(damager);

            // If they have the same owner, cancel
            if (owner1 != null && owner1.equals(owner2)) {
                event.setCancelled(true);
            }
        }
    }
}