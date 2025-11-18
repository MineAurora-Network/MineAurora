package me.login.pets;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.data.PetsDatabase;
import me.login.pets.gui.PetHelmetMenu;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
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

    // --- TargetSelection handler ---
    private final TargetSelection targetSelection;

    // Tracks cooldowns for item usage
    private final Map<UUID, Long> captureCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fruitCooldowns = new ConcurrentHashMap<>();

    // --- Cooldown for particle effect ---
    private final Map<UUID, Long> petInteractCooldowns = new ConcurrentHashMap<>();

    private final NamespacedKey attributeKey;

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
        this.attributeKey = new NamespacedKey(plugin, "pet_attribute_id");

        // --- Initialize TargetSelection ---
        this.targetSelection = new TargetSelection(this, config, logger);
    }

    // --- Data Methods ---
    public void loadPlayerData(UUID playerUuid) {
        playerDataCache.put(playerUuid, database.getPlayerPets(playerUuid));
    }

    public void clearPlayerData(UUID playerUuid) {
        playerDataCache.invalidate(playerUuid);
    }

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

    // --- Summoning & Follow (UPDATED) ---
    public void summonPet(Player player, EntityType petType) {
        Pet petData = getPet(player.getUniqueId(), petType);
        if (petData == null) return;
        if (petData.isOnCooldown()) {
            messageHandler.sendPlayerMessage(player, "<red>Cooldown: " + petData.getRemainingCooldownSeconds() + "s</red>");
            return;
        }

        // --- Check for sunlight sensitivity ---
        if (isSunSensitive(petType)) {
            ItemStack helmet = null;
            if (petData.getArmorContents() != null && petData.getArmorContents().length > 3) {
                helmet = petData.getArmorContents()[3]; // Slot 3 is helmet
            }

            // If no helmet, open the menu and STOP summoning
            if (helmet == null || helmet.getType() == Material.AIR) {
                new PetHelmetMenu(player, petData, plugin).open(player);
                // --- FIXED: Ensure single message sending ---
                messageHandler.sendPlayerMessage(player, "<red>This pet will burn in sunlight! Please equip a helmet.</red>");
                return;
            }
        }
        // -------------------------------------------------

        despawnPet(player.getUniqueId(), false);

        LivingEntity pet = (LivingEntity) player.getWorld().spawnEntity(player.getLocation(), petType);
        pet.getPersistentDataContainer().set(PET_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());

        String displayName = petData.getDisplayName() + " <gray>[Lvl " + petData.getLevel() + "]</gray>";
        pet.customName(MiniMessage.miniMessage().deserialize(displayName));
        pet.setCustomNameVisible(true);

        pet.setRemoveWhenFarAway(false);
        if (pet instanceof Slime) ((Slime) pet).setSize(2);

        // --- Actually equip the armor visual ---
        if (petData.getArmorContents() != null) {
            // Equip Helmet (Slot 3)
            if (petData.getArmorContents().length > 3 && petData.getArmorContents()[3] != null) {
                pet.getEquipment().setHelmet(petData.getArmorContents()[3]);
            }
        }
        // -------------------------------------------------

        double baseHealth = 20.0;
        if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            baseHealth = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue();
        }

        double healthBonus = config.getHealthBonus(petData.getLevel());
        double damageBonus = config.getDamage(petType, petData.getLevel());

        // --- Check Attribute Shard ---
        String attributeId = getAttributeId(petData.getAttributeContent());

        // Buff: Health Shard (+25% to total calculated health)
        double healthMultiplier = 1.0;
        if ("health_shard".equals(attributeId)) {
            healthMultiplier = 1.25;
        }

        // Buff: Damage Shard (+25% to total damage)
        double damageMultiplier = 1.0;
        if ("damage_shard".equals(attributeId)) {
            damageMultiplier = 1.25;
        }

        // Buff: Speed Shard (Speed 4)
        if ("speed_shard".equals(attributeId)) {
            pet.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 3, false, false));
        }
        // -----------------------------

        for (ItemStack armorPiece : petData.getArmorContents()) {
            if (armorPiece != null && armorPiece.hasItemMeta()) {
                PersistentDataContainer pdc = armorPiece.getItemMeta().getPersistentDataContainer();
                NamespacedKey healthKey = new NamespacedKey(plugin, "health_bonus");
                NamespacedKey damageKey = new NamespacedKey(plugin, "damage_bonus");

                if (pdc.has(healthKey, PersistentDataType.STRING)) {
                    try { healthBonus += Double.parseDouble(pdc.get(healthKey, PersistentDataType.STRING)); } catch (NumberFormatException e) {}
                }
                if (pdc.has(damageKey, PersistentDataType.STRING)) {
                    try { damageBonus += Double.parseDouble(pdc.get(damageKey, PersistentDataType.STRING)); } catch (NumberFormatException e) {}
                }
            }
        }

        ItemStack weapon = petData.getWeaponContent();
        if (weapon != null && weapon.hasItemMeta()) {
            PersistentDataContainer pdc = weapon.getItemMeta().getPersistentDataContainer();
            NamespacedKey damageKey = new NamespacedKey(plugin, "damage_bonus");
            if (pdc.has(damageKey, PersistentDataType.STRING)) {
                try { damageBonus += Double.parseDouble(pdc.get(damageKey, PersistentDataType.STRING)); } catch (NumberFormatException e) {}
            }
        }

        // Apply Multipliers (Round to remove decimals)
        double finalHealth = Math.round((baseHealth + healthBonus) * healthMultiplier);
        double finalDamage = Math.round(damageBonus * damageMultiplier);

        if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalHealth);
            pet.setHealth(finalHealth);
        }

        if (pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(finalDamage);
        }

        activePets.put(player.getUniqueId(), pet.getUniqueId());
        startFollowTask(player, pet);
    }

    // --- Helper method for sun sensitivity ---
    private boolean isSunSensitive(EntityType type) {
        return type == EntityType.ZOMBIE ||
                type == EntityType.SKELETON ||
                type == EntityType.STRAY ||
                type == EntityType.PHANTOM ||
                type == EntityType.DROWNED ||
                type == EntityType.ZOMBIE_VILLAGER ||
                type == EntityType.HUSK;
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

                messageHandler.sendPetActionBar(player, pet.customName() != null ? pet.customName() : MiniMessage.miniMessage().deserialize(data.getDefaultName()), data.getLevel(), hp, max);

                targetSelection.handlePetAILogic(player, pet);
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

    // --- addXp (BUFF LOGIC ADDED) ---
    public void addXp(Player player, Pet pet, double amount) {
        // Buff: XP Shard (+25%)
        String attributeId = getAttributeId(pet.getAttributeContent());
        if ("xp_shard".equals(attributeId)) {
            amount = Math.floor(amount * 1.25); // No decimals
        }

        pet.setXp(pet.getXp() + amount);
        double req = config.getXpRequired(pet.getLevel());

        int maxLevel = config.getMaxLevel();

        if (pet.getXp() >= req && pet.getLevel() < maxLevel) {
            pet.setXp(pet.getXp() - req);
            pet.setLevel(pet.getLevel() + 1);
            messageHandler.sendPlayerMessage(player, "<gold>Your pet leveled up to " + pet.getLevel() + "!</gold>");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            database.updatePetStats(player.getUniqueId(), pet.getPetType(), pet.getLevel(), pet.getXp());

            // Re-apply stats if active (simplified logic: just re-summon usually, but here we update values)
            LivingEntity activePet = getActivePet(player.getUniqueId());
            if (activePet != null && activePet.getType() == pet.getPetType()) {
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

    public void despawnPet(UUID playerUuid, boolean msg) {
        if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) {
            UUID petId = activePets.remove(playerUuid);

            // Manually clear target to prevent lingering aggression states
            Entity e = Bukkit.getEntity(petId);
            if (e instanceof Mob) {
                ((Mob) e).setTarget(null);
            }

            if (e != null) e.remove();

            if (msg) messageHandler.sendPlayerMessage(Bukkit.getPlayer(playerUuid), "<yellow>Pet despawned.</yellow>");
        }
    }

    public void despawnAllActivePets() {
        for (UUID petUuid : activePets.values()) {
            Entity pet = Bukkit.getEntity(petUuid);
            // Manually clear target
            if (pet instanceof Mob) {
                ((Mob) pet).setTarget(null);
            }
            if (pet != null) pet.remove();
        }
        activePets.clear();
        for (BukkitRunnable task : followTasks.values()) task.cancel();
        followTasks.clear();
    }

    public void killActivePet(UUID playerUuid) {
        if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) {
            UUID petId = activePets.remove(playerUuid);

            // Manually clear target
            Entity e = Bukkit.getEntity(petId);
            if (e instanceof Mob) {
                ((Mob) e).setTarget(null);
            }

            if (e != null) {
                e.remove();
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
        if (petData != null) petData.setDisplayName(newName);

        database.updatePetName(playerUuid, petType, newName);
        LivingEntity activePet = getActivePet(playerUuid);
        if (activePet != null && activePet.getType() == petType) {
            String displayName = newName + " <gray>[Lvl " + petData.getLevel() + "]</gray>";
            activePet.customName(MiniMessage.miniMessage().deserialize(displayName));
        }
    }
    public boolean isPet(Entity e) { return e.getPersistentDataContainer().has(PET_OWNER_KEY, PersistentDataType.STRING); }
    public UUID getPetOwner(Entity e) {
        try { return UUID.fromString(e.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING)); }
        catch (Exception ex) { return null; }
    }
    public boolean isCapturing(UUID entityUuid) { return capturingEntities.contains(entityUuid); }
    public Login getPlugin() { return plugin; }

    public TargetSelection getTargetSelection() {
        return targetSelection;
    }

    public void showPetParticles(Player player, LivingEntity pet) {
        if (petInteractCooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (petInteractCooldowns.get(player.getUniqueId()) - System.currentTimeMillis());
            if (timeLeft > 0) {
                return; // On cooldown
            }
        }

        pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5);
        petInteractCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000L); // 3 sec cooldown
    }

    public void savePetInventory(Pet pet) {
        String armor = pet.serializeArmor();
        String weapon = pet.serializeWeapon();
        String attribute = pet.serializeAttribute();
        database.updatePetInventory(pet.getOwnerUuid(), pet.getPetType(), armor, weapon, attribute);
    }

    public PetMessageHandler getMessageHandler() {
        return messageHandler;
    }

    public PetsConfig getPetsConfig() {
        return config;
    }

    private String getAttributeId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(attributeKey, PersistentDataType.STRING);
    }
}