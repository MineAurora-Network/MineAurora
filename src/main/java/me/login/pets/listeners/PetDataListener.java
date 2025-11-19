package me.login.pets.listeners;

import me.login.pets.PetManager;
import me.login.pets.PetMessageHandler;
import me.login.pets.data.PetsDatabase;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Manages loading and unloading pet data when players join or quit.
 */
public class PetDataListener implements Listener {

    private final PetManager petManager;
    private final PetsDatabase database;

    public PetDataListener(PetManager petManager, PetsDatabase database) {
        this.petManager = petManager;
        this.database = database;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        database.getPlugin().getServer().getScheduler().runTaskAsynchronously(database.getPlugin(), () -> {
            petManager.loadPlayerData(player.getUniqueId());
            long endedCooldowns = petManager.getPlayerData(player.getUniqueId()).stream()
                    .filter(pet -> pet.getCooldownEndTime() > 0 && pet.getCooldownEndTime() < System.currentTimeMillis())
                    .peek(pet -> database.setPetCooldown(player.getUniqueId(), pet.getPetType(), 0))
                    .count();

            if (endedCooldowns > 0) {
                petManager.loadPlayerData(player.getUniqueId());
                PetMessageHandler messageHandler = petManager.getPlugin().getPetsModule().getMessageHandler();
                if (messageHandler != null) {
                    database.getPlugin().getServer().getScheduler().runTask(database.getPlugin(), () -> {
                        messageHandler.sendPlayerMessage(player, "<green>One or more of your pets have finished their cooldown!</green>");
                    });
                }
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // --- NEW: Save data before clearing ---
        petManager.saveAllPets(player.getUniqueId());

        petManager.killActivePet(player.getUniqueId());
        petManager.clearPlayerData(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        petManager.killActivePet(player.getUniqueId());
    }
}