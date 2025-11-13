package me.login.pets;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.data.PetsDatabase;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Core logic class for the Pets system.
 * Manages active pets, capture attempts, and player pet data.
 */
public class PetManager {

    private final Login plugin;
    private final PetsDatabase database;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;
    private final PetsLogger logger;

    // Stores all pets for online players. Key: Player UUID, Value: List<Pet>
    private final Cache<UUID, List<Pet>> playerDataCache;

    // Stores the player's currently active/summoned pet. Key: Player UUID, Value: Entity UUID
    private final Map<UUID, UUID> activePets;
    // Stores the follow task for each pet
    private final Map<UUID, BukkitRunnable> followTasks;

    // NamespacedKey to tag summoned pets
    public static NamespacedKey PET_OWNER_KEY;

    public PetManager(Login plugin, PetsDatabase database, PetsConfig config, PetMessageHandler messageHandler, PetsLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.activePets = new ConcurrentHashMap<>();
        this.followTasks = new ConcurrentHashMap<>();
        this.playerDataCache = CacheBuilder.newBuilder()
                .expireAfterAccess(30, TimeUnit.MINUTES)
                .build();

        PET_OWNER_KEY = new NamespacedKey(plugin, "pet-owner-uuid");
    }

    /**
     * Loads a player's pet data from the database into the cache.
     * @param playerUuid The player's UUID.
     */
    public void loadPlayerData(UUID playerUuid) {
        List<Pet> pets = database.getPlayerPets(playerUuid);
        playerDataCache.put(playerUuid, pets);
    }

    /**
     * Clears a player's pet data from the cache.
     * @param playerUuid The player's UUID.
     */
    public void clearPlayerData(UUID playerUuid) {
        playerDataCache.invalidate(playerUuid);
    }

    /**
     * Gets a player's pet data from the cache.
     * @param playerUuid The player's UUID.
     * @return A list of the player's pets.
     */
    public List<Pet> getPlayerData(UUID playerUuid) {
        List<Pet> pets = playerDataCache.getIfPresent(playerUuid);
        if (pets == null) {
            // Data not in cache, load it synchronously
            loadPlayerData(playerUuid);
            pets = playerDataCache.getIfPresent(playerUuid);
        }
        return pets;
    }

    /**
     * Gets a specific Pet object from the cache.
     * @param playerUuid The owner's UUID.
     * @param petType The EntityType of the pet.
     * @return The Pet object, or null if not found.
     */
    public Pet getPet(UUID playerUuid, EntityType petType) {
        List<Pet> pets = getPlayerData(playerUuid);
        if (pets == null) {
            return null;
        }
        return pets.stream()
                .filter(p -> p.getPetType() == petType)
                .findFirst()
                .orElse(null);
    }

    /**
     * Handles the logic for a pet capture attempt.
     * @param player The player attempting the capture.
     * @param entity The entity being captured.
     * @param captureItemName The internal name of the capture item (e.g., "tier1_lead").
     */
    public void attemptCapture(Player player, LivingEntity entity, String captureItemName) {
        EntityType petType = entity.getType();

        // 1. Check if capturable
        if (!config.isCapturable(petType)) {
            messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>This creature cannot be captured.</white>");
            return;
        }

        // 2. Check if player already has this pet
        if (hasPet(player.getUniqueId(), petType)) {
            messageHandler.sendPlayerTitle(player, "<yellow>Already Captured!</yellow>", "<white>You already own one of these.</white>");
            return;
        }

        // 3. Check capture chance
        messageHandler.sendPlayerTitle(player, "<aqua>Capturing...</aqua>", "<white>Throwing the lead...</white>", 250, 1000, 250);
        double chance = config.getCaptureChance(petType, captureItemName);

        if (Math.random() > chance) {
            // Failed
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>The creature broke free!</white>");
            }, 20L); // 1 second later
            return;
        }

        // 4. Success!
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (addPet(player.getUniqueId(), petType)) {
                // Remove the captured entity
                entity.remove();
                messageHandler.sendPlayerTitle(player, "<green>Captured!</green>", "<white>You caught the " + petType.name() + "!</white>");
                logger.logCapture(player.getName(), petType.name(), captureItemName);
            } else {
                messageHandler.sendPlayerTitle(player, "<red>Capture Error!</red>", "<white>An error occurred. Please report this.</white>");
                logger.logError("Failed to save pet " + petType.name() + " for player " + player.getName());
            }
        }, 20L); // 1 second later
    }

    /**
     * Checks if a player has a specific pet type from the cache.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType to check.
     * @return true if the player has the pet, false otherwise.
     */
    public boolean hasPet(UUID playerUuid, EntityType petType) {
        List<Pet> pets = getPlayerData(playerUuid);
        if (pets == null) {
            return false;
        }
        return pets.stream().anyMatch(pet -> pet.getPetType() == petType);
    }

    /**
     * Adds a pet to the cache AND database.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @return true if added, false if it failed (e.g., already exists).
     */
    public boolean addPet(UUID playerUuid, EntityType petType) {
        // Add to database first
        if (!database.addPet(playerUuid, petType)) {
            return false; // Failed to add, probably already exists
        }

        // Add to cache
        List<Pet> pets = getPlayerData(playerUuid);
        if (pets != null) {
            pets.add(new Pet(playerUuid, petType));
        }
        return true;
    }

    /**
     * Removes a pet from the cache AND database.
     * @param playerUuid The player's UUID.
     * @param petType The EntityType of the pet.
     * @return true if removed, false if not found.
     */
    public boolean removePet(UUID playerUuid, EntityType petType) {
        // Remove from database first
        if (!database.removePet(playerUuid, petType)) {
            return false; // Not found
        }

        // Remove from cache
        List<Pet> pets = getPlayerData(playerUuid);
        if (pets != null) {
            pets.removeIf(p -> p.getPetType() == petType);
        }

        // Despawn if it was active
        LivingEntity activePet = getActivePet(playerUuid);
        if (activePet != null && activePet.getType() == petType) {
            despawnPet(playerUuid, false);
        }
        return true;
    }

    /**
     * Summons a pet for the player.
     * @param player The player summoning the pet.
     * @param petType The type of pet to summon.
     */
    public void summonPet(Player player, EntityType petType) {
        Pet petData = getPet(player.getUniqueId(), petType);
        if (petData == null) {
            messageHandler.sendPlayerMessage(player, "<red>Error: You do not own that pet.</red>");
            return;
        }

        if (petData.isOnCooldown()) {
            messageHandler.sendPlayerMessage(player, "<red>That pet is on cooldown for " + petData.getRemainingCooldownSeconds() + "s.</red>");
            return;
        }

        // Despawn old pet
        despawnPet(player.getUniqueId(), false);

        Location loc = player.getLocation();
        World world = player.getWorld();
        Entity entity = world.spawnEntity(loc, petType);

        if (entity instanceof LivingEntity) {
            LivingEntity pet = (LivingEntity) entity;

            // Tag the pet with the owner's UUID
            PersistentDataContainer data = pet.getPersistentDataContainer();
            data.set(PET_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());

            // Set custom name
            pet.setCustomNameVisible(true);
            pet.customName(MiniMessage.miniMessage().deserialize(petData.getDisplayName()));

            // Make the pet non-persistent (don't save with world)
            pet.setRemoveWhenFarAway(true);
            if (!(pet instanceof Monster)) {
                pet.setRemoveWhenFarAway(false);
            }

            // --- UPDATED: Adjust attributes for large mobs ---
            switch (petType) {
                case SLIME:
                    ((Slime) pet).setSize(3); // Set to a reasonable size
                    break;
                case GHAST:
                    // Ghasts are huge, maybe scale them down?
                    // This requires attributes, which is complex.
                    // For now, we'll leave them default size.
                    break;
                case RAVAGER:
                case IRON_GOLEM:
                    // These mobs are large but manageable.
                    break;
                case ENDER_DRAGON:
                case WITHER:
                case WARDEN:
                    // Godly mobs, leave at default size
                    break;
            }

            // Add to active pet list
            activePets.put(player.getUniqueId(), pet.getUniqueId());

            // Start the follow task
            startFollowTask(player, pet);

        } else {
            entity.remove();
            messageHandler.sendPlayerMessage(player, "<red>Error: This pet type cannot be summoned.</red>");
        }
    }

    /**
     * Starts the BukkitRunnable that makes the pet follow the player.
     * @param player The owner.
     * @param pet The pet entity.
     */
    private void startFollowTask(Player player, LivingEntity pet) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (pet == null || !pet.isValid() || player == null || !player.isOnline()) {
                    despawnPet(player.getUniqueId(), false); // Clean up if pet/player is invalid
                    this.cancel();
                    return;
                }

                // Health Action Bar
                double health = pet.getHealth();
                double maxHealth = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
                messageHandler.sendPlayerActionBar(player, "<green>" + pet.getName() + " <white>| <red>" + String.format("%.1f", health) + " / " + String.format("%.1f", maxHealth) + " HP</red></white>");

                // Follow Logic
                if (pet instanceof Mob) {
                    Mob mob = (Mob) pet;
                    if (mob.getTarget() != null) {
                        return; // Pet is in combat, don't interrupt
                    }

                    double distance = pet.getLocation().distanceSquared(player.getLocation());

                    if (distance > 225) { // More than 15 blocks away
                        pet.teleport(player.getLocation().add(1, 0, 1));
                    } else if (distance > 16) { // More than 4 blocks away
                        mob.getPathfinder().moveTo(player, 1.2);
                    } else if (distance < 9) { // Less than 3 blocks
                        mob.getPathfinder().stopPathfinding();
                        // Face the player
                        Location lookLoc = pet.getLocation().setDirection(player.getLocation().toVector().subtract(pet.getLocation().toVector()));
                        pet.teleport(lookLoc);
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 20L); // Run every second
        followTasks.put(player.getUniqueId(), task);
    }

    /**
     * Despawns a player's active pet.
     * @param playerUuid The player's UUID.
     * @param showMessage true to send a "pet despawned" message.
     */
    public void despawnPet(UUID playerUuid, boolean showMessage) {
        // Stop the follow task
        BukkitRunnable task = followTasks.remove(playerUuid);
        if (task != null) {
            task.cancel();
        }

        // Remove the entity
        UUID petUuid = activePets.remove(playerUuid);
        if (petUuid != null) {
            Entity pet = Bukkit.getEntity(petUuid);
            if (pet != null) {
                pet.remove();
            }
            if (showMessage) {
                Player player = Bukkit.getPlayer(playerUuid);
                if (player != null) {
                    messageHandler.sendPlayerMessage(player, "<yellow>Your pet has been despawned.</yellow>");
                }
            }
        }
    }

    /**
     * Called when a pet dies.
     * @param ownerUuid The owner's UUID.
     * @param petType The pet's type.
     */
    public void onPetDeath(UUID ownerUuid, EntityType petType) {
        long cooldownEndTime = System.currentTimeMillis() + (config.getPetCooldownSeconds() * 1000L);

        // Update cache
        Pet pet = getPet(ownerUuid, petType);
        if (pet != null) {
            pet.setCooldownEndTime(cooldownEndTime);
        }

        // Update database
        database.setPetCooldown(ownerUuid, petType, cooldownEndTime);

        // Clean up active pet maps
        activePets.remove(ownerUuid);
        BukkitRunnable task = followTasks.remove(ownerUuid);
        if (task != null) {
            task.cancel();
        }

        Player owner = Bukkit.getPlayer(ownerUuid);
        if (owner != null) {
            messageHandler.sendPlayerMessage(owner, "<red>Your " + petType.name() + " has died! It is on cooldown for " + config.getPetCooldownSeconds() + "s.</red>");
        }
    }

    /**
     * Revives a pet by removing its cooldown.
     * @param ownerUuid The owner's UUID.
     * @param petType The pet's type.
     * @return true if successful, false if pet not found.
     */
    public boolean revivePet(UUID ownerUuid, EntityType petType) {
        Pet pet = getPet(ownerUuid, petType);
        if (pet == null) {
            return false;
        }

        // Update cache
        pet.setCooldownEndTime(0);
        // Update database
        database.setPetCooldown(ownerUuid, petType, 0);
        return true;
    }

    public boolean isPetOnCooldown(UUID ownerUuid, EntityType petType) {
        Pet pet = getPet(ownerUuid, petType);
        return pet != null && pet.isOnCooldown();
    }

    public long getPetCooldownRemaining(UUID ownerUuid, EntityType petType) {
        Pet pet = getPet(ownerUuid, petType);
        return (pet != null) ? pet.getRemainingCooldownSeconds() : 0;
    }

    /**
     * Despawns all active pets on the server (e.g., for plugin disable).
     */
    public void despawnAllActivePets() {
        for (UUID petUuid : activePets.values()) {
            Entity pet = Bukkit.getEntity(petUuid);
            if (pet != null) {
                pet.remove();
            }
        }
        activePets.clear();

        for (BukkitRunnable task : followTasks.values()) {
            task.cancel();
        }
        followTasks.clear();
    }

    /**
     * Checks if a player has an active pet summoned.
     * @param playerUuid The player's UUID.
     * @return true if they have an active pet, false otherwise.
     */
    public boolean hasActivePet(UUID playerUuid) {
        return activePets.containsKey(playerUuid);
    }

    /**
     * Gets the LivingEntity instance of a player's active pet.
     * @param playerUuid The player's UUID.
     * @return The LivingEntity pet, or null if none is active or found.
     */
    public LivingEntity getActivePet(UUID playerUuid) {
        UUID petUuid = activePets.get(playerUuid);
        if (petUuid == null) {
            return null;
        }

        Entity pet = Bukkit.getEntity(petUuid);
        if (pet instanceof LivingEntity && pet.isValid()) {
            return (LivingEntity) pet;
        }

        // Pet entity was lost
        despawnPet(playerUuid, false);
        return null;
    }

    /**
     * Checks if a given entity is an active pet.
     * @param entity The entity to check.
     * @return true if the entity is an active pet, false otherwise.
     */
    public boolean isPet(Entity entity) {
        return entity.getPersistentDataContainer().has(PET_OWNER_KEY, PersistentDataType.STRING);
    }

    public UUID getPetOwner(Entity entity) {
        String uuidStr = entity.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING);
        if (uuidStr == null) {
            return null;
        }
        try {
            return UUID.fromString(uuidStr);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Updates a pet's custom name in the cache, database, and active entity.
     * @param playerUuid The owner's UUID.
     * @param petType The pet's type.
     * @param newName The new name.
     */
    public void updatePetName(UUID playerUuid, EntityType petType, String newName) {
        // Update cache
        Pet petData = getPet(playerUuid, petType);
        if (petData != null) {
            petData.setCustomName(newName);
        }

        // Update database
        database.updatePetName(playerUuid, petType, newName);

        // Update active entity if it's summoned
        LivingEntity activePet = getActivePet(playerUuid);
        if (activePet != null && activePet.getType() == petType) {
            activePet.customName(MiniMessage.miniMessage().deserialize(newName));
        }
    }

    public Login getPlugin() {
        return plugin;
    }
}