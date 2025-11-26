package me.login.dungeon.game;

import me.login.Login;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonUtils;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class GameSession {
    private final UUID playerId;
    private final Dungeon dungeon;
    private final Player player;

    private int currentRoomId = 0;
    private boolean hasKey = false;
    private boolean bossDead = false;

    private final List<Entity> activeMobs = new ArrayList<>();
    private final List<Entity> trapMobs = new ArrayList<>();
    private Zombie bossEntity;
    private Zombie bossGuard;
    private final List<Entity> minions = new ArrayList<>();

    // --- Marker Restore System ---
    private static class MarkerData {
        Location loc; int roomId;
        MarkerData(Location l, int r) { loc = l; roomId = r; }
    }
    private final List<MarkerData> storedMarkers = new ArrayList<>();

    private boolean isBossRoom = false;
    private boolean bossPhase1Triggered = false;
    private boolean bossPhase2Triggered = false;
    private boolean isBossInvulnerable = false;

    // Boss cutscene states
    private boolean guardDefeated = false;
    private boolean bossSpawned = false;

    // --- Timer & Key Logic ---
    private ArmorStand keyStand;
    private ArmorStand textStand1;
    private ArmorStand textStand2;
    private BukkitRunnable keyTask;

    private final long startTime;
    private final long endTime;
    private static final long DUNGEON_DURATION_MILLIS = 15 * 60 * 1000;

    // --- Trap Heads & Chests ---
    private final Map<ArmorStand, Location> trapHeads = new HashMap<>();
    private final Set<Location> usedChests = new HashSet<>();
    private ArmorStand buffOrb;
    private BukkitRunnable trapTask;
    private BukkitRunnable buffTask;

    // --- Block Restore System ---
    private final Map<Location, BlockData> changedBlocks = new HashMap<>();

    public GameSession(Player player, Dungeon dungeon) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.dungeon = dungeon;
        this.startTime = System.currentTimeMillis();
        this.endTime = this.startTime + DUNGEON_DURATION_MILLIS;

        dungeon.setOccupied(true);
        resetDoors(Material.COAL_BLOCK);
        startTrapTicker();
    }

    public void recordBlockChange(Block block) {
        if (!changedBlocks.containsKey(block.getLocation())) {
            changedBlocks.put(block.getLocation(), block.getBlockData().clone());
        }
    }

    private void restoreBlocks() {
        for (Map.Entry<Location, BlockData> entry : changedBlocks.entrySet()) {
            entry.getKey().getBlock().setBlockData(entry.getValue());
        }
        changedBlocks.clear();
    }

    public boolean isChestUsed(Location loc) {
        return usedChests.contains(loc);
    }

    public void triggerMiniRewardChest(Location loc) {
        if (usedChests.contains(loc)) {
            DungeonUtils.error(player, "This chest is already looted!");
            return;
        }
        usedChests.add(loc);

        // 30% Chance for 2000 coins
        if (Math.random() < 0.30) {
            // Placeholder for economy logic
            DungeonUtils.msg(player, "<green><b>JACKPOT!</b> <yellow>You found 2,000 Coins!");
            // me.login.misc.tokens.TokenManager.giveTokens(player, 2000); // Example if you have tokens
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } else {
            DungeonUtils.msg(player, "<gray>The chest was empty...");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, 1f, 0.5f);
        }
    }

    public void triggerChest(Location loc) {
        if (usedChests.contains(loc)) {
            DungeonUtils.error(player, "This chest is already looted!");
            return;
        }
        usedChests.add(loc);

        if (Math.random() < 0.50) {
            spawnBuffOrb(loc);
            DungeonUtils.msg(player, "<green><b>A Mysterious Orb appears!</b> <gray>Grab it!");
        } else {
            MobManager.IS_SPAWNING = true;
            try {
                Entity e = MobManager.spawnCryptZombie(loc.clone().add(0.5, 1, 0.5));
                trapMobs.add(e);
            } finally {
                MobManager.IS_SPAWNING = false;
            }
            DungeonUtils.msg(player, "<red><b>IT'S A TRAP!</b> <gray>Crypt Zombie spawned!");
        }
    }

    private void spawnBuffOrb(Location chestLoc) {
        Location spawnLoc = chestLoc.clone().add(0.5, -1.0, 0.5);
        MobManager.IS_SPAWNING = true;
        try {
            buffOrb = (ArmorStand) chestLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
            buffOrb.setVisible(true); buffOrb.setGravity(false); buffOrb.setSmall(true); buffOrb.setBasePlate(false); buffOrb.setInvulnerable(true); buffOrb.setMarker(false);
            ItemStack head = TextureToHead.getHead(MobManager.BUFF_ORB_TEXTURE);
            buffOrb.getEquipment().setHelmet(head);
            buffOrb.setVisible(false);
            buffOrb.setCustomNameVisible(true);
            buffOrb.customName(Component.text("Buff Orb (Right Click)", NamedTextColor.AQUA));
        } finally { MobManager.IS_SPAWNING = false; }
        buffTask = new BukkitRunnable() {
            double ticks = 0; final Location base = spawnLoc.clone();
            @Override public void run() {
                if (buffOrb == null || buffOrb.isDead() || !player.isOnline()) { cancel(); return; }
                float yaw = (buffOrb.getLocation().getYaw() + 10) % 360; double height = Math.min(1.5, ticks * 0.05); double bob = Math.sin(ticks * 0.2) * 0.1;
                Location next = base.clone().add(0, height + bob, 0); next.setYaw(yaw); buffOrb.teleport(next);
                buffOrb.getWorld().spawnParticle(Particle.DUST, buffOrb.getLocation().add(0, 1.5, 0), 1, new Particle.DustOptions(org.bukkit.Color.AQUA, 1));
                ticks++;
            }
        }; buffTask.runTaskTimer(Login.getPlugin(Login.class), 0L, 1L);
    }
    public boolean isBuffOrb(Entity entity) { return buffOrb != null && entity.getUniqueId().equals(buffOrb.getUniqueId()); }
    public void collectBuff() {
        if (buffOrb != null) {
            buffOrb.remove(); if (buffTask != null) buffTask.cancel(); buffOrb = null;
            double currentMax = player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getBaseValue(); player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(currentMax * 1.10);
            float currentSpeed = player.getWalkSpeed(); player.setWalkSpeed(currentSpeed * 1.20f); player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 0, false, false));
            DungeonUtils.msg(player, "<aqua><b>BUFF ACQUIRED!</b> <gray>+10% HP, +20% Speed, +20% Regen!"); player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_BEACON_ACTIVATE, 1f, 2f);
        }
    }

    public Dungeon getDungeon() { return dungeon; }
    public int getCurrentRoomId() { return currentRoomId; }
    public Player getPlayer() { return player; }
    public boolean isBossActive() { return bossEntity != null && !bossEntity.isDead(); }
    public boolean isGuardActive() { return bossGuard != null && !bossGuard.isDead(); }
    public Zombie getBossEntity() { return bossEntity; }
    public boolean isBossInvulnerable() { return isBossInvulnerable; }
    public boolean isBossDead() { return bossDead; }

    public boolean hasMob(Entity entity) {
        return activeMobs.contains(entity) || minions.contains(entity) || trapMobs.contains(entity) || (bossEntity != null && bossEntity.equals(entity)) || (bossGuard != null && bossGuard.equals(entity));
    }

    public boolean isKeyEntity(Entity entity) {
        return keyStand != null && entity.getUniqueId().equals(keyStand.getUniqueId());
    }

    public int getMobsLeft() {
        int count = activeMobs.size();
        if (isBossActive()) count++;
        if (isGuardActive()) count++;
        count += minions.size();
        return count;
    }

    public String getTimeLeft() {
        long now = System.currentTimeMillis();
        long diff = endTime - now;
        if (diff < 0) diff = 0;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        return String.format("%02d:%02d", minutes, seconds % 60);
    }

    public boolean isTimeUp() { return System.currentTimeMillis() > endTime; }

    public void highlightRemainingMobs() {
        int count = 0;
        List<Entity> allMobs = new ArrayList<>(activeMobs);
        allMobs.addAll(minions);
        if (isBossActive()) allMobs.add(bossEntity);
        if (isGuardActive()) allMobs.add(bossGuard);
        for (Entity e : allMobs) {
            if (e instanceof LivingEntity) {
                ((LivingEntity) e).addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0, false, false));
                count++;
            }
        }
        DungeonUtils.msg(player, "<green>Highlighted " + count + " remaining entities for 5 seconds.");
    }

    public void advanceRoom() {
        currentRoomId++;
        hasKey = false;
        if (currentRoomId == 7) isBossRoom = true;
    }

    public void startRoom() {
        if (isBossRoom) {
            if (!bossSpawned && !guardDefeated) {
                startBossSequence();
            }
            return;
        }

        activeMobs.clear();
        Location center = dungeon.getSpawnLocation();
        if (center == null) { DungeonUtils.error(player, "Dungeon spawn not set!"); return; }

        Collection<Entity> nearby = center.getWorld().getNearbyEntities(center, 250, 50, 250);

        int found = 0;
        for (Entity e : nearby) {
            if (e instanceof TextDisplay && e.getPersistentDataContainer().has(MobManager.MARKER_KEY, PersistentDataType.INTEGER)) {
                int markerRoomId = e.getPersistentDataContainer().get(MobManager.MARKER_KEY, PersistentDataType.INTEGER);

                if (markerRoomId == currentRoomId) {
                    Location loc = e.getLocation();
                    storedMarkers.add(new MarkerData(loc.clone(), markerRoomId));
                    e.remove();
                    Entity mob = MobManager.spawnRoomMob(loc, currentRoomId);
                    if (mob != null) activeMobs.add(mob);
                    found++;
                }
            }
        }
    }

    // --- BOSS SEQUENCE LOGIC ---
    private void startBossSequence() {
        if (dungeon.getBossSpawnLocation() == null) return;

        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }

                if (tick == 0) DungeonUtils.msg(player, "&c&lBOSS&7: &fWho dares enter my domain?");
                if (tick == 3) DungeonUtils.msg(player, "&c&lBOSS&7: &fYou think you can challenge me?");
                if (tick == 6) DungeonUtils.msg(player, "&c&lBOSS&7: &fMy loyal guard will dispose of you!");

                if (tick == 8) {
                    DungeonUtils.msg(player, "&4&l(!) &cBoss Guard Spawned!");
                    bossGuard = MobManager.spawnBossGuard(dungeon.getBossSpawnLocation());
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(Login.getPlugin(Login.class), 0L, 40L); // 2 seconds per msg
    }

    public void spawnTrapHead(Location loc) {
        MobManager.IS_SPAWNING = true;
        try {
            ArmorStand stand = (ArmorStand) loc.getWorld().spawnEntity(loc.clone().add(0, -1.4, 0), EntityType.ARMOR_STAND);
            stand.setVisible(true); stand.setGravity(false); stand.setSmall(false); stand.setBasePlate(false); stand.setInvulnerable(true);
            stand.getEquipment().setHelmet(TextureToHead.getHead(MobManager.TRAP_HEAD_TEXTURE)); stand.setVisible(false);
            trapHeads.put(stand, loc);
        } finally { MobManager.IS_SPAWNING = false; }
    }

    private void startTrapTicker() {
        trapTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) { cancel(); return; }
                Iterator<Map.Entry<ArmorStand, Location>> it = trapHeads.entrySet().iterator();
                while (it.hasNext()) {
                    Map.Entry<ArmorStand, Location> entry = it.next();
                    if (entry.getKey().isDead()) { it.remove(); continue; }

                    if (entry.getValue().distanceSquared(player.getLocation()) < 4) {
                        Entity e = MobManager.spawnUndeadSkeleton(entry.getValue());
                        if (e != null) trapMobs.add(e);

                        entry.getKey().remove();
                        it.remove();
                        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_SKELETON_AMBIENT, 1f, 0.5f);
                    }
                }
            }
        };
        trapTask.runTaskTimer(Login.getPlugin(Login.class), 20L, 10L);
    }

    public void handleMobDeath(Entity entity) {
        // Boss Guard Logic
        if (bossGuard != null && entity.equals(bossGuard)) {
            guardDefeated = true;
            bossGuard = null;
            DungeonUtils.msg(player, "&c&lBOSS&7: &fUseless servant! I will do it myself!");

            // Start Boss Spawn Sequence
            new BukkitRunnable() {
                int tick = 0;
                @Override
                public void run() {
                    if (!player.isOnline()) { cancel(); return; }

                    if (tick == 2) DungeonUtils.msg(player, "&c&lBOSS&7: &fPrepare to die!");
                    if (tick == 4) DungeonUtils.msg(player, "&c&lBOSS&7: &fARISE!");

                    if (tick == 5) {
                        spawnBossWithEffects();
                        cancel();
                    }
                    tick++;
                }
            }.runTaskTimer(Login.getPlugin(Login.class), 40L, 40L);
            return;
        }

        if (trapMobs.contains(entity)) {
            trapMobs.remove(entity);
            return;
        }

        if (minions.contains(entity)) {
            minions.remove(entity);
            if (minions.isEmpty() && isBossActive()) {
                isBossInvulnerable = false;
                DungeonUtils.msg(player, "&cBoss Vulnerable! Attack now!");
            }
            return;
        }

        if (activeMobs.remove(entity)) {
            if (Math.random() < 0.60) {
                spawnTrapHead(entity.getLocation());
            }

            if (activeMobs.isEmpty()) {
                if (!isBossRoom) {
                    dropKey(entity.getLocation());
                }
            }
        }
    }

    private void spawnBossWithEffects() {
        Location loc = dungeon.getBossSpawnLocation();
        // Burst Particles
        loc.getWorld().spawnParticle(Particle.DUST, loc.add(0, 1, 0), 50, 1, 1, 1, new Particle.DustOptions(org.bukkit.Color.BLACK, 2));
        loc.getWorld().spawnParticle(Particle.DUST, loc, 50, 1, 1, 1, new Particle.DustOptions(org.bukkit.Color.BLUE, 2));
        loc.getWorld().playSound(loc, org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 0.5f);

        bossEntity = MobManager.spawnBoss(loc);
        bossSpawned = true;
        DungeonUtils.msg(player, "&4&l(!) &cTHE BOSS HAS AWAKENED!");
    }

    public void checkBossHealth() {
        if (bossEntity == null || bossEntity.isDead()) return;
        double health = bossEntity.getHealth();

        // Phase 1: 250 HP
        if (health <= 250 && !bossPhase1Triggered) {
            bossPhase1Triggered = true;
            isBossInvulnerable = true;
            DungeonUtils.msg(player, "&c&lBOSS&7: &fMinions! Shield me!");
            DungeonUtils.msg(player, "&eBoss is invulnerable until minions die!");
            for (int i = 0; i < 3; i++) minions.add(MobManager.spawnWeakMinion(bossEntity.getLocation()));
        }
        // Phase 2: 100 HP
        else if (health <= 100 && !bossPhase2Triggered) {
            bossPhase2Triggered = true;
            isBossInvulnerable = true;
            DungeonUtils.msg(player, "&c&lBOSS&7: &fElite Guards! Destroy them!");
            DungeonUtils.msg(player, "&eBoss is invulnerable until minions die!");
            for (int i = 0; i < 5; i++) minions.add(MobManager.spawnStrongMinion(bossEntity.getLocation()));
        }
    }

    public void handleBossDeath() {
        bossDead = true;
        bossEntity = null;

        DungeonUtils.msg(player, "&c&lBOSS&7: &fImpossible... my power...");
        DungeonUtils.msg(player, "&6&l(!) &eDungeon Cleared!");

        if (dungeon.getRewardDoor() != null) {
            openDoor(dungeon.getRewardDoor());
            DungeonUtils.msg(player, "&aTreasure Room Open!");
        }
        if (dungeon.getRewardChestLocation() != null) {
            dungeon.getRewardChestLocation().getBlock().setType(Material.CHEST);
        }
    }

    private void dropKey(Location deathLoc) {
        cleanupKey(); MobManager.IS_SPAWNING = true;
        try {
            Location spawnLoc = deathLoc.clone().add(0, -1.0, 0);
            keyStand = (ArmorStand) deathLoc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND); keyStand.setGravity(false); keyStand.setSmall(true); keyStand.setBasePlate(false); keyStand.setInvulnerable(true); keyStand.setMarker(false);
            ItemStack head = TextureToHead.getHead(MobManager.KEY_TEXTURE); keyStand.getEquipment().setHelmet(head); keyStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING); keyStand.setVisible(false);
            textStand1 = (ArmorStand) deathLoc.getWorld().spawnEntity(spawnLoc.clone().add(0, 1.5, 0), EntityType.ARMOR_STAND); textStand1.setVisible(false); textStand1.setGravity(false); textStand1.setSmall(true); textStand1.setCustomNameVisible(true); textStand1.customName(Component.text("Room " + (currentRoomId + 1) + " Key", NamedTextColor.GOLD)); textStand1.setMarker(true);
            textStand2 = (ArmorStand) deathLoc.getWorld().spawnEntity(spawnLoc.clone().add(0, 1.25, 0), EntityType.ARMOR_STAND); textStand2.setVisible(false); textStand2.setGravity(false); textStand2.setSmall(true); textStand2.setCustomNameVisible(true); textStand2.customName(Component.text("Click to pick up", NamedTextColor.YELLOW)); textStand2.setMarker(true);
        } finally { MobManager.IS_SPAWNING = false; }
        DungeonUtils.msg(player, "<green>The Key has appeared! Click it to pick it up!");
        keyTask = new BukkitRunnable() {
            double ticks = 0; final Location animBase = deathLoc.clone().add(0, 1.0, 0);
            @Override public void run() {
                if (keyStand == null || keyStand.isDead() || player == null || !player.isOnline()) { this.cancel(); return; }
                float yaw = (keyStand.getLocation().getYaw() + 5) % 360; double yOffset = Math.sin(ticks * 0.1) * 0.75;
                Location newLoc = animBase.clone().add(0, yOffset, 0); newLoc.setYaw(yaw); keyStand.teleport(newLoc);
                if (textStand1 != null) textStand1.teleport(newLoc.clone().add(0, 1.0, 0)); if (textStand2 != null) textStand2.teleport(newLoc.clone().add(0, 0.75, 0));
                keyStand.getWorld().spawnParticle(Particle.CLOUD, keyStand.getLocation().add(0, 1.0, 0), 1, 0, 0, 0, 0.01); ticks++;
            }
        }; keyTask.runTaskTimer(Login.getPlugin(Login.class), 0L, 1L);
    }
    public void pickupKey() { cleanupKey(); hasKey = true; player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f); DungeonUtils.msg(player, "<green><b>You obtained the Virtual Key!</b> <gray>Right-click the door to open it."); }
    private void cleanupKey() { if (keyStand != null && !keyStand.isDead()) keyStand.remove(); if (textStand1 != null && !textStand1.isDead()) textStand1.remove(); if (textStand2 != null && !textStand2.isDead()) textStand2.remove(); if (keyTask != null && !keyTask.isCancelled()) keyTask.cancel(); keyStand = null; textStand1 = null; textStand2 = null; keyTask = null; }
    public boolean hasKey() { return hasKey; }
    public void setHasKey(boolean hasKey) { this.hasKey = hasKey; }
    public void openDoor(me.login.dungeon.model.Cuboid region) { if (region == null) return; modifyRegion(region, Material.AIR); }

    public void cleanup() {
        cleanupKey();
        if (trapTask != null) trapTask.cancel();
        if (buffTask != null) buffTask.cancel();
        trapHeads.keySet().forEach(Entity::remove);
        trapHeads.clear();
        if (buffOrb != null) buffOrb.remove();

        restoreBlocks();

        player.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(20.0);
        player.setWalkSpeed(0.2f);
        player.removePotionEffect(PotionEffectType.REGENERATION);

        player.getWorld().getEntities().stream()
                .filter(e -> e.getPersistentDataContainer().has(MobManager.UNDEAD_KEY, org.bukkit.persistence.PersistentDataType.BYTE))
                .forEach(Entity::remove);

        activeMobs.forEach(Entity::remove);
        activeMobs.clear();

        trapMobs.forEach(Entity::remove);
        trapMobs.clear();

        minions.forEach(Entity::remove);
        minions.clear();
        if (bossEntity != null) bossEntity.remove();
        if (bossGuard != null) bossGuard.remove();

        for (MarkerData data : storedMarkers) {
            MobManager.spawnMarker(data.loc, data.roomId);
        }
        storedMarkers.clear();

        resetDoors(Material.COAL_BLOCK);
        if (dungeon.getRewardChestLocation() != null) {
            dungeon.getRewardChestLocation().getBlock().setType(Material.AIR);
        }
        dungeon.setOccupied(false);
    }

    private void resetDoors(Material mat) {
        if (dungeon.getEntryDoor() != null) modifyRegion(dungeon.getEntryDoor(), mat);
        if (dungeon.getBossRoomDoor() != null) modifyRegion(dungeon.getBossRoomDoor(), mat);
        if (dungeon.getRewardDoor() != null) modifyRegion(dungeon.getRewardDoor(), mat);
        for (DungeonRoom r : dungeon.getRooms().values()) {
            if (r.getDoorRegion() != null) modifyRegion(r.getDoorRegion(), mat);
        }
    }

    private void modifyRegion(me.login.dungeon.model.Cuboid region, Material mat) {
        int minX = (int) Math.floor(region.getMinX());
        int maxX = (int) Math.floor(region.getMaxX());
        int minY = (int) Math.floor(region.getMinY());
        int maxY = (int) Math.floor(region.getMaxY());
        int minZ = (int) Math.floor(region.getMinZ());
        int maxZ = (int) Math.floor(region.getMaxZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block b = region.getWorld().getBlockAt(x, y, z);
                    b.setType(mat);
                }
            }
        }
    }
}