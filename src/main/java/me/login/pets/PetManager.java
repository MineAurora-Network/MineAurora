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
import org.bukkit.util.Vector;

import java.util.List;
import java.util.Map;
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
    }

    // --- Data Methods ---
    public void loadPlayerData(UUID playerUuid) { playerDataCache.put(playerUuid, database.getPlayerPets(playerUuid)); }
    public void clearPlayerData(UUID playerUuid) { playerDataCache.invalidate(playerUuid); }
    public List<Pet> getPlayerData(UUID playerUuid) {
        List<Pet> pets = playerDataCache.getIfPresent(playerUuid);
        if (pets == null) { loadPlayerData(playerUuid); pets = playerDataCache.getIfPresent(playerUuid); }
        return pets;
    }
    public Pet getPet(UUID playerUuid, EntityType petType) {
        List<Pet> pets = getPlayerData(playerUuid);
        return (pets == null) ? null : pets.stream().filter(p -> p.getPetType() == petType).findFirst().orElse(null);
    }

    // --- Capture Animation & Logic ---
    public boolean attemptCapture(Player player, LivingEntity entity, String captureItemName) {
        EntityType petType = entity.getType();

        // Cooldown Check (10s)
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

        // Start Animation Task
        startCaptureAnimation(player, entity, captureItemName);
        return true; // Consume Item
    }

    private void startCaptureAnimation(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(false); // Freeze mob
        entity.setGravity(false);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!entity.isValid() || !player.isOnline()) {
                    entity.setAI(true);
                    entity.setGravity(true);
                    this.cancel();
                    return;
                }

                // Move up slightly and rotate
                Location loc = entity.getLocation();
                if (ticks < 20) { // First second move up
                    loc.add(0, 0.05, 0);
                }
                loc.setYaw(loc.getYaw() + 15); // Spin
                entity.teleport(loc);

                entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 0.5, 0), 1);

                ticks++;
                if (ticks >= 100) { // 5 seconds
                    this.cancel();
                    finalizeCapture(player, entity, captureItemName);
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    private void finalizeCapture(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(true);
        entity.setGravity(true);
        EntityType petType = entity.getType();

        double chance = config.getCaptureChance(petType, captureItemName);
        if (Math.random() > chance) {
            // FAILED
            messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>The creature broke free!</white>");
            // Trigger 10s cooldown
            captureCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
        } else {
            // SUCCESS
            if (addPet(player.getUniqueId(), petType)) {
                entity.remove();
                messageHandler.sendPlayerTitle(player, "<green>Captured!</green>", "<white>You caught the " + petType.name() + "!</white>");
                logger.logCapture(player.getName(), petType.name(), captureItemName);
            }
        }
    }

    // --- Summoning with Stats ---
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

        double health = 20.0 + config.getHealthBonus(petData.getLevel());
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

    // --- Leveling System ---
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

    public void handlePetAggression(Player owner, Entity target) {
        if (!hasActivePet(owner.getUniqueId()) || !(target instanceof LivingEntity)) return;
        LivingEntity pet = getActivePet(owner.getUniqueId());
        if (pet instanceof Mob) {
            ((Mob) pet).setTarget((LivingEntity) target);
        }
    }

    private void startFollowTask(Player player, LivingEntity pet) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (pet == null || !pet.isValid() || player == null || !player.isOnline()) {
                    despawnPet(player.getUniqueId(), false);
                    this.cancel(); return;
                }
                double hp = pet.getHealth();
                double max = 20.0;
                if(pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) max = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();

                Pet data = getPet(player.getUniqueId(), pet.getType());
                messageHandler.sendPlayerActionBar(player, "<green>" + pet.getName() + " <white>Lvl " + data.getLevel() + " | <red>" + (int)hp + "/" + (int)max + " HP</red></white>");

                if (pet instanceof Mob && ((Mob)pet).getTarget() == null) {
                    double dist = pet.getLocation().distanceSquared(player.getLocation());
                    if (dist > 400) pet.teleport(player.getLocation());
                    else if (dist > 16) ((Mob)pet).getPathfinder().moveTo(player, 1.2);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 20L);
        followTasks.put(player.getUniqueId(), task);
    }

    public void despawnPet(UUID playerUuid, boolean msg) {
        if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) {
            Entity e = Bukkit.getEntity(activePets.remove(playerUuid));
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
    }

    public boolean hasActivePet(UUID uuid) { return activePets.containsKey(uuid); }
    public LivingEntity getActivePet(UUID uuid) {
        UUID id = activePets.get(uuid);
        return id == null ? null : (LivingEntity) Bukkit.getEntity(id);
    }
    public boolean hasPet(UUID uuid, EntityType type) {
        return getPet(uuid, type) != null;
    }
    public boolean addPet(UUID uuid, EntityType type) { return database.addPet(uuid, type); }
    public boolean removePet(UUID uuid, EntityType type) { return database.removePet(uuid, type); }

    // --- FIXED: Re-added this method to fix compilation error ---
    public boolean revivePet(UUID ownerUuid, EntityType petType) {
        Pet pet = getPet(ownerUuid, petType);
        if (pet == null) {
            return false;
        }
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

    public void onPetDeath(UUID uuid, EntityType type) {
        long cd = System.currentTimeMillis() + (config.getPetCooldownSeconds() * 1000L);
        database.setPetCooldown(uuid, type, cd);
        Pet p = getPet(uuid, type);
        if (p != null) p.setCooldownEndTime(cd);
        despawnPet(uuid, false);
    }

    public boolean isPet(Entity e) { return e.getPersistentDataContainer().has(PET_OWNER_KEY, PersistentDataType.STRING); }
    public UUID getPetOwner(Entity e) {
        try { return UUID.fromString(e.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING)); }
        catch (Exception ex) { return null; }
    }
    public Login getPlugin() { return plugin; }
}