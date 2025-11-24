package me.login.pets;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.data.PetsDatabase;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
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
    private final PetFruitShop fruitShop;

    private final Cache<UUID, List<Pet>> playerDataCache;
    private final Map<UUID, UUID> activePets;
    private final Map<UUID, BukkitRunnable> followTasks;

    private final Set<UUID> capturingEntities = ConcurrentHashMap.newKeySet();
    private final TargetSelection targetSelection;

    private final Map<UUID, Long> captureCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fruitCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> petInteractCooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, Long> lastCombatActionBar = new ConcurrentHashMap<>();

    private final NamespacedKey attributeKey;
    private final NamespacedKey fruitXpKey;
    public static NamespacedKey PET_OWNER_KEY;

    public PetManager(Login plugin, PetsDatabase database, PetsConfig config, PetMessageHandler messageHandler, PetsLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.config = config;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.fruitShop = new PetFruitShop(plugin, config, messageHandler);
        this.activePets = new ConcurrentHashMap<>();
        this.followTasks = new ConcurrentHashMap<>();
        this.playerDataCache = CacheBuilder.newBuilder().expireAfterAccess(30, TimeUnit.MINUTES).build();
        PET_OWNER_KEY = new NamespacedKey(plugin, "pet-owner-uuid");
        this.attributeKey = new NamespacedKey("mineaurora", "pet_attribute_id");
        this.fruitXpKey = new NamespacedKey("mineaurora", "fruit_xp_amount");

        this.targetSelection = new TargetSelection(this, config, logger);
    }

    // --- Action Bar ---
    public void sendPetActionBar(Player player, Pet petData, LivingEntity petEntity, LivingEntity target) {
        lastCombatActionBar.put(player.getUniqueId(), System.currentTimeMillis());

        double petHp = petEntity.getHealth();
        double petMax = petEntity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        double hunger = petData.getHunger();

        String petInfo = "<gold>" + petData.getDisplayName() +
                " <red>" + String.format("%.1f", petHp) + "/" + String.format("%.0f", petMax) + "❤ " +
                "<yellow>" + String.format("%.1f", hunger) + "/20.0\uD83C\uDF56";

        String finalMsg = petInfo;

        if (target != null && target.isValid() && !target.isDead()) {
            double targetHp = target.getHealth();
            double targetMax = 20.0;
            if (target.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
                targetMax = target.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
            }

            String targetName = target.getName();
            if (target instanceof Player) targetName = ((Player) target).getDisplayName();
            else if (target.getCustomName() != null) targetName = target.getCustomName();

            // FIX 3: Strip legacy colors to prevent MiniMessage ParsingException
            targetName = ChatColor.stripColor(targetName);

            String targetInfo = " <gray>|| <red>⚔ <white>" + targetName + " <red>" + String.format("%.1f", targetHp) + "/" + String.format("%.0f", targetMax) + "❤";
            finalMsg += targetInfo;
        }

        messageHandler.sendPlayerActionBar(player, finalMsg);
    }

    // ... (rest of the file remains unchanged, omitted for brevity but methods must be preserved)
    public void loadPlayerData(UUID playerUuid) { playerDataCache.put(playerUuid, database.getPlayerPets(playerUuid)); }
    public void clearPlayerData(UUID playerUuid) { playerDataCache.invalidate(playerUuid); }
    public void saveAllPets(UUID playerUuid) {
        List<Pet> pets = playerDataCache.getIfPresent(playerUuid);
        if (pets != null) {
            for (Pet pet : pets) {
                if (activePets.containsKey(playerUuid) && activePets.get(playerUuid) != null) {
                    Entity entity = Bukkit.getEntity(activePets.get(playerUuid));
                    if (entity instanceof LivingEntity && entity.getType() == pet.getPetType()) {
                        pet.setHealth(((LivingEntity) entity).getHealth());
                    }
                }
                savePetInventory(pet);
                database.updatePetStats(playerUuid, pet.getPetType(), pet.getLevel(), pet.getXp(), pet.getHunger(), pet.getHealth());
            }
        }
    }
    public List<Pet> getPlayerData(UUID playerUuid) {
        List<Pet> pets = playerDataCache.getIfPresent(playerUuid);
        if (pets == null) { pets = database.getPlayerPets(playerUuid); playerDataCache.put(playerUuid, pets); }
        return pets;
    }
    public Pet getPet(UUID playerUuid, EntityType petType) {
        List<Pet> pets = getPlayerData(playerUuid);
        return (pets == null) ? null : pets.stream().filter(p -> p.getPetType() == petType).findFirst().orElse(null);
    }
    public boolean attemptCapture(Player player, LivingEntity entity, String captureItemName) {
        EntityType petType = entity.getType();
        if (captureCooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (captureCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) { messageHandler.sendPlayerMessage(player, "<red>You must wait " + timeLeft + "s before using a lead again.</red>"); return false; }
        }
        if (!config.isCapturable(petType)) { messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>Cannot capture this.</white>"); return false; }
        if (hasPet(player.getUniqueId(), petType)) { messageHandler.sendPlayerTitle(player, "<yellow>Already Owned!</yellow>", "<white>You have this pet.</white>"); return false; }
        startCaptureAnimation(player, entity, captureItemName);
        return true;
    }
    private void startCaptureAnimation(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(false); entity.setGravity(false); capturingEntities.add(entity.getUniqueId());
        entity.getWorld().spawnParticle(Particle.CLOUD, entity.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.2);
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!entity.isValid() || !player.isOnline()) {
                    if (entity.isValid()) { entity.setAI(true); entity.setGravity(true); capturingEntities.remove(entity.getUniqueId()); }
                    this.cancel(); return;
                }
                Location loc = entity.getLocation(); if (ticks < 20) loc.add(0, 0.05, 0); loc.setYaw(loc.getYaw() + 15); entity.teleport(loc);
                entity.getWorld().spawnParticle(Particle.WITCH, entity.getLocation().add(0, 0.5, 0), 1);
                int percent = ticks; String bar = getProgressBar(percent, 100, 20, "|", "<green>", "<gray>");
                Title progress = Title.title(MiniMessage.miniMessage().deserialize("<gold>Capturing...</gold>"), MiniMessage.miniMessage().deserialize(bar + " <yellow>" + percent + "%</yellow>"), Title.Times.times(Duration.ofMillis(0), Duration.ofMillis(500), Duration.ofMillis(0)));
                player.showTitle(progress);
                ticks++;
                if (ticks >= 100) { this.cancel(); finalizeCapture(player, entity, captureItemName); }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }
    private String getProgressBar(int current, int max, int totalBars, String symbol, String colorCompleted, String colorNotCompleted) {
        float percent = (float) current / max; int progressBars = (int) (totalBars * percent);
        StringBuilder sb = new StringBuilder(); sb.append(colorCompleted); for (int i = 0; i < progressBars; i++) sb.append(symbol);
        sb.append(colorNotCompleted); for (int i = progressBars; i < totalBars; i++) sb.append(symbol); return sb.toString();
    }
    private void finalizeCapture(Player player, LivingEntity entity, String captureItemName) {
        entity.setAI(true); entity.setGravity(true); capturingEntities.remove(entity.getUniqueId());
        EntityType petType = entity.getType(); double chance = config.getCaptureChance(petType, captureItemName);
        if (Math.random() > chance) {
            messageHandler.sendPlayerTitle(player, "<red>Capture Failed!</red>", "<white>The creature broke free!</white>");
            messageHandler.sendPlayerMessage(player, "<red>Capture failed! The creature broke free.</red>");
            captureCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 10000L);
        } else {
            if (addPet(player.getUniqueId(), petType)) {
                entity.getWorld().spawnParticle(Particle.HEART, entity.getLocation().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.1); entity.remove();
                messageHandler.sendPlayerTitle(player, "<green>Captured!</green>", "<white>You caught the " + petType.name() + "!</white>");
                messageHandler.sendPlayerMessage(player, "<green>You successfully captured the " + petType.name() + "!</green>");
                logger.logCapture(player.getName(), petType.name(), captureItemName);
            } else { messageHandler.sendPlayerMessage(player, "<red>An error occurred saving your pet to the database.</red>"); }
        }
    }
    public void summonPet(Player player, EntityType petType) {
        Pet petData = getPet(player.getUniqueId(), petType); if (petData == null) return;
        if (petData.isOnCooldown()) { messageHandler.sendPlayerMessage(player, "<red>Cooldown: " + petData.getRemainingCooldownSeconds() + "s</red>"); return; }
        despawnPet(player.getUniqueId(), false);
        for (Entity e : player.getWorld().getEntities()) { if (isPet(e) && player.getUniqueId().equals(getPetOwner(e))) { e.remove(); } }
        Location spawnLoc = player.getLocation().clone().add(player.getLocation().getDirection().setY(0).normalize().multiply(-2));
        if (spawnLoc.getBlock().getType().isSolid()) spawnLoc = player.getLocation();
        LivingEntity pet = (LivingEntity) player.getWorld().spawn(spawnLoc, (Class<? extends Entity>) petType.getEntityClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, entity -> {
            entity.getPersistentDataContainer().set(PET_OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        });
        if (pet == null || !pet.isValid()) { messageHandler.sendPlayerMessage(player, "<red>Failed to summon pet here.</red>"); return; }
        if (pet instanceof Ageable) ((Ageable) pet).setAdult(); if (pet instanceof Zombie) ((Zombie) pet).setBaby(false);
        if (isSunSensitive(petType)) pet.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false));
        String displayName = petData.getDisplayName() + " <gray>[Lvl " + petData.getLevel() + "]</gray>";
        pet.customName(MiniMessage.miniMessage().deserialize(displayName)); pet.setCustomNameVisible(true); pet.setRemoveWhenFarAway(false);
        if (pet instanceof Slime) ((Slime) pet).setSize(2);
        if (petData.getArmorContents() != null) pet.getEquipment().setArmorContents(petData.getArmorContents());
        if (petData.getWeaponContent() != null) pet.getEquipment().setItemInMainHand(petData.getWeaponContent());
        double baseHealth = 20.0; if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) { baseHealth = pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getDefaultValue(); }
        double healthBonus = config.getHealthBonus(petData.getLevel()); double damageBonus = config.getDamage(petType, petData.getLevel());
        String attributeId = getAttributeId(petData.getAttributeContent()); double healthMultiplier = 1.0; double damageMultiplier = 1.0;
        if ("health_shard".equals(attributeId)) healthMultiplier = 1.25; if ("damage_shard".equals(attributeId)) damageMultiplier = 1.25;
        if ("speed_shard".equals(attributeId)) { pet.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, Integer.MAX_VALUE, 4, false, false)); }
        for (ItemStack armorPiece : petData.getArmorContents()) {
            if (armorPiece != null && armorPiece.hasItemMeta()) {
                PersistentDataContainer pdc = armorPiece.getItemMeta().getPersistentDataContainer();
                NamespacedKey healthKey = new NamespacedKey(plugin, "health_bonus"); NamespacedKey damageKey = new NamespacedKey(plugin, "damage_bonus");
                if (pdc.has(healthKey, PersistentDataType.STRING)) { try { healthBonus += Double.parseDouble(pdc.get(healthKey, PersistentDataType.STRING)); } catch (Exception e) {} }
                if (pdc.has(damageKey, PersistentDataType.STRING)) { try { damageBonus += Double.parseDouble(pdc.get(damageKey, PersistentDataType.STRING)); } catch (Exception e) {} }
            }
        }
        double finalMaxHealth = Math.round((baseHealth + healthBonus) * healthMultiplier); double finalDamage = Math.round(damageBonus * damageMultiplier);
        if (pet.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) { pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(finalMaxHealth); }
        if (pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) { pet.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(finalDamage); }
        if (petData.getHealth() <= 0.0) { pet.setHealth(finalMaxHealth); petData.setHealth(finalMaxHealth); petData.setHunger(20.0); } else { pet.setHealth(Math.min(petData.getHealth(), finalMaxHealth)); }
        activePets.put(player.getUniqueId(), pet.getUniqueId()); startFollowTask(player, pet);
    }
    private boolean isSunSensitive(EntityType type) { return type == EntityType.ZOMBIE || type == EntityType.SKELETON || type == EntityType.STRAY || type == EntityType.PHANTOM || type == EntityType.DROWNED || type == EntityType.ZOMBIE_VILLAGER || type == EntityType.HUSK; }
    private void startFollowTask(Player player, LivingEntity pet) {
        BukkitRunnable task = new BukkitRunnable() {
            int tickCounter = 0;
            @Override
            public void run() {
                if (pet == null || !pet.isValid() || player == null || !player.isOnline()) { despawnPet(player.getUniqueId(), false); this.cancel(); return; }
                Pet data = getPet(player.getUniqueId(), pet.getType()); if (data == null) return;
                tickCounter++;
                if (tickCounter % 100 == 0) {
                    double currentHunger = data.getHunger();
                    if (currentHunger > 0) {
                        currentHunger = Math.max(0, currentHunger - 0.1); data.setHunger(currentHunger);
                        if (currentHunger < 2.0 && currentHunger > 1.9) { messageHandler.sendPlayerMessage(player, "<red><bold>WARNING!</bold> Your pet is starving! Feed it soon or it will die!</red>"); }
                    } else { this.cancel(); messageHandler.sendPlayerMessage(player, "<dark_red>Your pet died of starvation!</dark_red>"); onPetDeath(player.getUniqueId(), pet.getType(), "Starvation"); return; }
                }
                if (tickCounter % 20 == 0) {
                    if (pet.getHealth() < pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue()) {
                        if (data.getHunger() >= 0.2) { double hungerCost = 0.2; double healthGain = hungerCost / 0.7; pet.setHealth(Math.min(pet.getHealth() + healthGain, pet.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue())); data.setHunger(Math.max(0, data.getHunger() - hungerCost)); }
                    }
                }
                if (lastCombatActionBar.containsKey(player.getUniqueId())) {
                    if (System.currentTimeMillis() - lastCombatActionBar.get(player.getUniqueId()) < 2000) { targetSelection.handlePetAILogic(player, pet); return; }
                }
                LivingEntity target = null; if (pet instanceof Mob) target = ((Mob) pet).getTarget();
                sendPetActionBar(player, data, pet, target);
                targetSelection.handlePetAILogic(player, pet);
            }
        };
        task.runTaskTimer(plugin, 0L, 1L); followTasks.put(player.getUniqueId(), task);
    }
    public void onPetDeath(UUID uuid, EntityType type, String killerName) {
        if (killerName != null) { Player p = Bukkit.getPlayer(uuid); if (p != null) messageHandler.sendPlayerMessage(p, "<red>Your " + type.name() + " died fighting " + killerName + "!</red>"); }
        long cd = System.currentTimeMillis() + (config.getPetCooldownSeconds() * 1000L); database.setPetCooldown(uuid, type, cd);
        Pet p = getPet(uuid, type); if (p != null) { p.setCooldownEndTime(cd); p.setHealth(0.0); }
        killActivePet(uuid);
    }
    public void addXp(Player player, Pet pet, double amount) {
        int maxLevel = 25; if (pet.getLevel() >= maxLevel) return;
        String attributeId = getAttributeId(pet.getAttributeContent()); if ("xp_shard".equals(attributeId)) amount = Math.floor(amount * 1.25);
        pet.setXp(pet.getXp() + amount); double req = config.getXpRequired(pet.getLevel());
        if (pet.getXp() >= req) {
            pet.setXp(pet.getXp() - req); pet.setLevel(pet.getLevel() + 1);
            messageHandler.sendPlayerMessage(player, "<gold>Your pet leveled up to " + pet.getLevel() + "!</gold>");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
            database.updatePetStats(player.getUniqueId(), pet.getPetType(), pet.getLevel(), pet.getXp(), pet.getHunger(), pet.getHealth());
            LivingEntity activePet = getActivePet(player.getUniqueId()); if (activePet != null && activePet.getType() == pet.getPetType()) { summonPet(player, pet.getPetType()); }
        } else { database.updatePetStats(player.getUniqueId(), pet.getPetType(), pet.getLevel(), pet.getXp(), pet.getHunger(), pet.getHealth()); }
    }
    public void feedPet(Player player, ItemStack item) {
        if (!hasActivePet(player.getUniqueId())) { messageHandler.sendPlayerMessage(player, "<red>Summon a pet first!</red>"); return; }
        if (fruitCooldowns.containsKey(player.getUniqueId())) {
            long timeLeft = (fruitCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()) / 1000;
            if (timeLeft > 0) { messageHandler.sendPlayerActionBar(player, "<red>Wait " + timeLeft + "s to feed again.</red>"); return; }
        }
        if (!item.hasItemMeta()) return; PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        double xpAmount = 0; if (pdc.has(fruitXpKey, PersistentDataType.DOUBLE)) { xpAmount = pdc.get(fruitXpKey, PersistentDataType.DOUBLE); }
        LivingEntity entity = getActivePet(player.getUniqueId()); Pet petData = getPet(player.getUniqueId(), entity.getType());
        if (xpAmount > 0) { addXp(player, petData, xpAmount); messageHandler.sendPlayerActionBar(player, "<green>+ " + (int)xpAmount + " XP</green>"); } else { double current = petData.getHunger(); double newHunger = Math.min(20.0, current + 5.0); petData.setHunger(newHunger); messageHandler.sendPlayerActionBar(player, "<gold>+5 Hunger Restored</gold>"); }
        item.setAmount(item.getAmount() - 1); fruitCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
    }
    public void despawnPet(UUID playerUuid, boolean msg) {
        saveAllPets(playerUuid); if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel();
        if (activePets.containsKey(playerUuid)) { UUID petId = activePets.remove(playerUuid); Entity e = Bukkit.getEntity(petId); if (e instanceof Mob) ((Mob) e).setTarget(null); if (e != null) e.remove(); if (msg && Bukkit.getPlayer(playerUuid) != null) messageHandler.sendPlayerMessage(Bukkit.getPlayer(playerUuid), "<yellow>Pet despawned.</yellow>"); }
    }
    public void despawnAllActivePets() {
        for (UUID uuid : activePets.keySet()) saveAllPets(uuid);
        for (UUID petUuid : activePets.values()) { Entity pet = Bukkit.getEntity(petUuid); if (pet instanceof Mob) ((Mob) pet).setTarget(null); if (pet != null) pet.remove(); }
        activePets.clear(); for (BukkitRunnable task : followTasks.values()) task.cancel(); followTasks.clear();
    }
    public void killActivePet(UUID playerUuid) { if (followTasks.containsKey(playerUuid)) followTasks.remove(playerUuid).cancel(); if (activePets.containsKey(playerUuid)) { UUID petId = activePets.remove(playerUuid); Entity e = Bukkit.getEntity(petId); if (e instanceof Mob) ((Mob) e).setTarget(null); if (e != null) e.remove(); } }
    public boolean hasActivePet(UUID uuid) { return activePets.containsKey(uuid); }
    public LivingEntity getActivePet(UUID uuid) { UUID id = activePets.get(uuid); return id == null ? null : (LivingEntity) Bukkit.getEntity(id); }
    public boolean hasPet(UUID uuid, EntityType type) { return getPet(uuid, type) != null; }
    public boolean addPet(UUID uuid, EntityType type) { if (database.addPet(uuid, type)) { playerDataCache.invalidate(uuid); return true; } return false; }
    public boolean removePet(UUID uuid, EntityType type) { if (database.removePet(uuid, type)) { playerDataCache.invalidate(uuid); return true; } return false; }
    public boolean revivePet(UUID ownerUuid, EntityType petType) { Pet pet = getPet(ownerUuid, petType); if (pet == null) return false; pet.setCooldownEndTime(0); database.setPetCooldown(ownerUuid, petType, 0); return true; }
    public boolean isPetOnCooldown(UUID ownerUuid, EntityType petType) { Pet pet = getPet(ownerUuid, petType); return pet != null && pet.isOnCooldown(); }
    public long getPetCooldownRemaining(UUID ownerUuid, EntityType petType) { Pet pet = getPet(ownerUuid, petType); return (pet != null) ? pet.getRemainingCooldownSeconds() : 0; }
    public void updatePetName(UUID playerUuid, EntityType petType, String newName) { Pet petData = getPet(playerUuid, petType); if (petData != null) petData.setDisplayName(newName); database.updatePetName(playerUuid, petType, newName); LivingEntity activePet = getActivePet(playerUuid); if (activePet != null && activePet.getType() == petType) { String displayName = newName + " <gray>[Lvl " + petData.getLevel() + "]</gray>"; activePet.customName(MiniMessage.miniMessage().deserialize(displayName)); } }
    public boolean isPet(Entity e) { return e.getPersistentDataContainer().has(PET_OWNER_KEY, PersistentDataType.STRING); }
    public UUID getPetOwner(Entity e) { try { return UUID.fromString(e.getPersistentDataContainer().get(PET_OWNER_KEY, PersistentDataType.STRING)); } catch (Exception ex) { return null; } }
    public boolean isCapturing(UUID entityUuid) { return capturingEntities.contains(entityUuid); }
    public Login getPlugin() { return plugin; }
    public TargetSelection getTargetSelection() { return targetSelection; }
    public void showPetParticles(Player player, LivingEntity pet) { if (petInteractCooldowns.containsKey(player.getUniqueId())) { long timeLeft = (petInteractCooldowns.get(player.getUniqueId()) - System.currentTimeMillis()); if (timeLeft > 0) return; } pet.getWorld().spawnParticle(Particle.HEART, pet.getLocation().add(0, 1, 0), 3, 0.5, 0.5, 0.5); petInteractCooldowns.put(player.getUniqueId(), System.currentTimeMillis() + 3000L); }
    public void savePetInventory(Pet pet) { String armor = pet.serializeArmor(); String weapon = pet.serializeWeapon(); String attribute = pet.serializeAttribute(); database.updatePetInventory(pet.getOwnerUuid(), pet.getPetType(), armor, weapon, attribute); }
    public PetMessageHandler getMessageHandler() { return messageHandler; }
    public PetsConfig getPetsConfig() { return config; }
    public PetFruitShop getFruitShop() { return fruitShop; }
    private String getAttributeId(ItemStack item) { if (item == null || !item.hasItemMeta()) return null; return item.getItemMeta().getPersistentDataContainer().get(attributeKey, PersistentDataType.STRING); }
}