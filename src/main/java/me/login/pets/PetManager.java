package me.login.pets;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.data.PetsDatabase;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class PetManager {

    private final Login plugin;
    private final PetsDatabase database;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;
    private final PetsLogger logger;

    private final Cache<UUID, List<Pet>> playerDataCache;
    private final Map<UUID, UUID> activePets;
    private final Map<UUID, BukkitRunnable> followTasks;

    // Tracks entities currently being captured to prevent damage
    private final Set<UUID> capturingEntities = ConcurrentHashMap.newKeySet();

    // --- NEW: TargetSelection handler ---
    private final TargetSelection targetSelection;

    // Tracks cooldowns for item usage
    private final Map<UUID, Long> captureCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fruitCooldowns = new ConcurrentHashMap<>();

    public static NamespacedKey PET_OWNER_KEY;

    public PetManager(Login plugin, PetsDatabase database, PetsConfig config, PetMessageHandler messageHandler, PetsLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.activePets = new ConcurrentHashMap<>();
        this.followTasks = new ConcurrentHashMap<>();
        this.playerDataCache = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build();
        PET_OWNER_KEY = new NamespacedKey(plugin, "pet-owner-uuid");

        // --- NEW: Initialize TargetSelection ---
        this.targetSelection = new TargetSelection(this, config, logger);
    }

    // --- Data Methods ---
    public void loadPlayerData(UUID playerUuid) { playerDataCache.put(playerUuid, database.getPlayerPets(playerUuid)); }
    public void clearPlayerData(UUID playerUuid) { playerDataCache.invalidate(playerUuid); }

    public List<Pet> getPlayerData(UUID playerUuid) {
        List<Pet> pets = playerDataCache.getIfPresent(playerUuid);
        if (pets == null) {
            pets = database.getPlayerPets(playerUuid);
            playerDataCache.put(playerUuid, pets);
        }
        return pets;
    }

    public Pet getPet(UUID playerUuid, EntityType petType) {
        List<Pet> pets = getPlayerData(playerUuid);
        return (pets == null) ? null : pets.stream().filter(p -> p.getPetType() == petType).findFirst().orElse(null);
    }

    // --- Capture Animation & Logic ---
    public boolean attemptCapture(Player player, LivingEntity entity, String captureItemName) {
        EntityType petType = entity.getType();

        if (captureCooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (captureCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                messageHandler.sendPlayerMessage(player, "<red>You must wait " + timeLeft + "s before using a lead again.</red>");
                return false;
            }
        }

        if (!config.isCapturable(petType)) {
            messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>Cannot capture this.</white>");
            return false;
        }
        if (hasPet(player.getUniqueId(), petType)) {
            messageHandler.sendPlayerTitle(player, "<yellow>Already Owned!</yellow>", "<white>You have this pet.</white>");
            return false;
        }

        startCaptureAnimation(player, entity, captureItemName);
        return true;
    }

    private void startCaptureAnimation(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(false);
        entity.setGravity(false);
        capturingEntities.add(entity.getUniqueId()); // Protect from damage

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!entity.isValid() || !player.isOnline()) {
                    if (entity.isValid()) {
                        entity.setAI(true);
                        entity.setGravity(true);
                        capturingEntities.remove(entity.getUniqueId());
                    }
                    this.cancel();
                    return;
                }

                Location loc = entity.getLocation();
                if (ticks < 20) {
                    loc.add(0, 0.05, 0);
                }
                loc.setYaw(loc.getYaw() + 15);
                entity.teleport(loc);

                entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 0.5, 0), 1);

                ticks++;
                if (ticks >= 100) {
                    this.cancel();
                    finalizeCapture(player, entity, captureItemName);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalizeCapture(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(true);
        entity.setGravity(true);
        capturingEntities.remove(entity.getUniqueId()); // Stop protecting

        EntityType petType = entity.getType();

        double chance = config.getCaptureChance(petType, captureItemName);
        if (Math.random() > chance) {
            // FAILED
            messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>The creature broke free!</white>");
            messageHandler.sendPlayerMessage(player, "<red>Capture failed! The creature broke free.</red>");
            captureCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
        } else {
            // SUCCESS
            if (addPet(player.getUniqueId(), petType)) {
                entity.remove();
                messageHandler.sendPlayerTitle(player, "<green>Captured!</green>", "<white>You caught the " + petType.name() + "!</white>");
                messageHandler.sendPlayerMessage(player, "<green>You successfully captured the " + petType.name() + "!</green>");
                logger.logCapture(player.getName(), petType.name(), captureItemName);
            } else {
                messageHandler.sendPlayerMessage(player, "<red>An error occurred saving your pet to the database.</red>");
            }
        }
    }

    // --- Summoning & Follow ---
    public void summonPet(Player player, EntityType petType) {
        Pet petData = getPet(player.getUniqueId(), petType);
        if (petData == null) return;
        if (petData.isOnCooldown()) {
            messageHandler.sendPlayerMessage(player, "<red>Cooldown: " + petData.getRemainingCooldownSeconds() + "s</red>");
            return;
        }
        despawnPet(player.getUniqueId(), false);

        LivingEntity pet = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), petType);
        pet.getPersistentDataContainer().set(PET_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());

        String displayName = petData.getDisplayName() + " <gray>[Lvl " + petData.getLevel() + "]</gray>";
        pet.customName(MiniMessage.miniMessage().deserialize(displayName));
        pet.setCustomNameVisible(true);

        pet.setRemoveWhenFarAway(false);
        if (pet instanceof Slime) ((Slime) pet).setSize(2);

        // --- FIXED: Use default mob health as base, not 20 ---
        // This code is correct as per your request.
        double baseHealth = 20.0;
        if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            // Get the mob's default health (e.g., Warden = 500, Zombie = 20)
            baseHealth = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue();
        }
        double health = baseHealth + config.getHealthBonus(petData.getLevel());
        // ---

        if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(health);
            pet.setHealth(health);
        }

        if (pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(config.getDamage(petType, petData.getLevel()));
        }

        activePets.put(player.getUniqueId(), pet.getUniqueId());
        startFollowTask(player, pet);
    }

    private void startFollowTask(Player player, LivingEntity pet) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (pet == null || !pet.isValid() || player == null || !player.isOnline()) {
                    despawnPet(player.getUniqueId(), false);
                    // --- UPDATED: Clear target in new handler ---
                    targetSelection.clearPetTarget(pet.getUniqueId()); // This method is now empty, but safe to call
                    this.cancel(); return;
                }

                double hp = pet.getHealth();
                double max = 20.0;
                if(pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) max = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

                Pet data = getPet(player.getUniqueId(), pet.getType());

                messageHandler.sendPetActionBar(player, pet.customName() != null ? pet.customName() : pet.name(), data.getLevel(), hp, max);

                // --- AI LOGIC (Refactored) ---
                targetSelection.handlePetAILogic(player, pet);
                // --- End of AI Logic ---
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        followTasks.put(player.getUniqueId(), task);
    }

    public void onPetDeath(UUID uuid, EntityType type, String killerName) {
        if (killerName != null) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) messageHandler.sendPlayerMessage(p, "<red>Your " + type.name() + " died fighting " + killerName + "!</red>");
        }
        long cd = System.currentTimeMillis() + (config.getPetCooldownSeconds() * 1000L);
        database.setPetCooldown(uuid, type, cd);
        Pet p = getPet(uuid, type);
        if (p != null) p.setCooldownEndTime(cd);
        despawnPet(uuid, false);
    }

    // --- Standard Methods ---
    public void addXp(Player player, Pet pet, double amount) {
        pet.setXp(pet.getXp() + amount);
        double req = config.getXpRequired(pet.getLevel());
        if (pet.getXp() >= req && pet.getLevel() < config.getMaxLevel()) {
            pet.setXp(pet.getXp() - req);
            pet.setLevel(pet.getLevel() + 1);
            messageHandler.sendPlayerMessage(player, "<gold>Your pet leveled up to " + pet.getLevel() + "!</gold>");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            database.updatePetStats(player.getUniqueId(), pet.getPetType(), pet.getLevel(), pet.getXp());
            if (hasActivePet(player.getUniqueId()) && getActivePet(player.getUniqueId()).getType() == pet.getPetType()) {
                summonPet(player, pet.getPetType());
            }
        } else {
            database.updatePetStats(player.getUniqueId(), pet.getPetType(), pet.getLevel(), pet.getXp());
        }
    }

    public void feedPet(Player player, ItemStack item) {
        if (!hasActivePet(player.getUniqueId())) {
            messageHandler.sendPlayerMessage(player, "<red>Summon a pet first!</red>");
            return;
        }
        if (fruitCooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (fruitCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) {
                messageHandler.sendPlayerActionBar(player, "<red>Wait " + timeLeft + "s to feed again.</red>");
                return;
            }
        }
        String fruitName = null;
        if (item.hasItemMeta()) {
            NamespacedKey key = new NamespacedKey("mineaurora", "pet_fruit_id");
            if (item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.STRING)) {
                fruitName = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
            }
        }
        if (fruitName == null) return;
        LivingEntity entity = getActivePet(player.getUniqueId());
        Pet petData = getPet(player.getUniqueId(), entity.getType());
        double xp = config.getFruitXp(fruitName);
        addXp(player, petData, xp);
        item.setAmount(item.getAmount() - 1);
        messageHandler.sendPlayerActionBar(player, "<green>+ " + xp + " XP</green>");
        fruitCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
    }

    // --- METHODS MOVED to TargetSelection ---
    // - handlePetAggression
    // - setPetTarget

    public void despawnPet(UUID playerUuid, boolean msg) {
        if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) {
            UUID petId = activePets.remove(playerUuid);
            // --- UPDATED: Clear target in new handler ---
            targetSelection.clearPetTarget(petId); // This method is now empty, but safe to call

            Entity e = Bukkit.getEntity(petId);
            if (e != null) e.remove();

            if (msg) messageHandler.sendPlayerMessage(Bukkit.getPlayer(playerUuid), "<yellow>Pet despawned.</yellow>");
        }
    }

    public void despawnAllActivePets() {
        for (UUID petUuid : activePets.values()) {
            Entity pet = Bukkit.getEntity(petUuid);
            if (pet != null) pet.remove();
        }
        activePets.clear();
        for (BukkitRunnable task : followTasks.values()) task.cancel();
        followTasks.clear();
        // --- UPDATED: Clear targets in new handler ---
        targetSelection.clearAllTargets(); // This method is now empty, but safe to call
    }

    /**
     * Kills the active pet for a player *without* triggering the death cooldown.
     * Used for server quit or world change.
     * @param playerUuid The owner's UUID.
     */
    public void killActivePet(UUID playerUuid) {
        if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) {
            UUID petId = activePets.remove(playerUuid);
            targetSelection.clearPetTarget(petId); // Clean up just in case

            Entity e = Bukkit.getEntity(petId);
            if (e != null) {
                e.remove(); // Remove the entity from the world
            }
        }
    }

    public boolean hasActivePet(UUID uuid) { return activePets.containsKey(uuid); }
    public LivingEntity getActivePet(UUID uuid) {
        UUID id = activePets.get(uuid);
        return id == null ? null : (LivingEntity) Bukkit.getEntity(id);
    }
    public boolean hasPet(UUID uuid, EntityType type) {
        return getPet(uuid, type) != null;
    }
    public boolean addPet(UUID uuid, EntityType type) {
        if (database.addPet(uuid, type)) {
            playerDataCache.invalidate(uuid);
            return true;
        }
        return false;
    }
    public boolean removePet(UUID uuid, EntityType type) {
        if (database.removePet(uuid, type)) {
            playerDataCache.invalidate(uuid);
            return true;
        }
        return false;
    }
    public boolean revivePet(UUID ownerUuid, EntityType petType) {
        Pet pet = getPet(ownerUuid, petType);
        if (pet == null) return false;
        pet.setCooldownEndTime(0);
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
    public void updatePetName(UUID playerUuid, EntityType petType, String newName) {
        Pet petData = getPet(playerUuid, petType);
        if (petData != null) petData.setCustomName(newName);
        database.updatePetName(playerUuid, petType, newName);
        LivingEntity activePet = getActivePet(playerUuid);
        if (activePet != null && activePet.getType() == petType) {
            activePet.customName(MiniMessage.miniMessage().deserialize(newName));
        }
    }
    public boolean isPet(Entity e) { return e.getPersistentDataContainer().has(PET_OWNER_KEY, PersistentDataType.STRING); }
    public UUID getPetOwner(Entity e) {
        try { return UUID.fromString(e.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING)); }
        catch (Exception ex) { return null; }
    }
    public boolean isCapturing(UUID entityUuid) { return capturingEntities.contains(entityUuid); }
    public Login getPlugin() { return plugin; }

    // --- NEW: Getter for TargetSelection ---
    public TargetSelection getTargetSelection() {
        return targetSelection;
    }
}