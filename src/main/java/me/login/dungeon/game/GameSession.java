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
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.EulerAngle;

import java.util.*;

public class GameSession {
    private final UUID playerId;
    private final Dungeon dungeon;
    private final Player player;

    private int currentRoomId = 0;
    private boolean hasKey = false;
    private boolean bossDead = false;

    private final List<Entity> activeMobs = new ArrayList<>();
    private Zombie bossEntity;
    private final List<Entity> minions = new ArrayList<>();

    private boolean isBossRoom = false;
    private boolean bossPhase1Triggered = false;
    private boolean bossPhase2Triggered = false;
    private boolean isBossInvulnerable = false;

    private final Random random = new Random();

    // --- New Key Logic ---
    private ArmorStand keyStand;     // Visual Head
    private ArmorStand textStand1;   // "Room X Key"
    private ArmorStand textStand2;   // "Click to pick up"
    private BukkitRunnable keyTask;
    private long startTime;

    public GameSession(Player player, Dungeon dungeon) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.dungeon = dungeon;
        this.startTime = System.currentTimeMillis();
        dungeon.setOccupied(true);
        resetDoors(Material.COAL_BLOCK);
    }

    public Dungeon getDungeon() { return dungeon; }
    public int getCurrentRoomId() { return currentRoomId; }
    public Player getPlayer() { return player; }
    public boolean isBossActive() { return bossEntity != null && !bossEntity.isDead(); }
    public Zombie getBossEntity() { return bossEntity; }
    public boolean isBossInvulnerable() { return isBossInvulnerable; }
    public boolean isBossDead() { return bossDead; }

    public boolean hasMob(Entity entity) {
        return activeMobs.contains(entity) || minions.contains(entity) || (bossEntity != null && bossEntity.equals(entity));
    }

    public boolean isKeyEntity(Entity entity) {
        return keyStand != null && entity.getUniqueId().equals(keyStand.getUniqueId());
    }

    // Getters for Scoreboard
    public int getMobsLeft() {
        int count = activeMobs.size();
        if (isBossActive()) count++;
        count += minions.size();
        return count;
    }

    public String getTimeElapsed() {
        long diff = System.currentTimeMillis() - startTime;
        long seconds = diff / 1000;
        long minutes = seconds / 60;
        return String.format("%02d:%02d", minutes, seconds % 60);
    }

    public void advanceRoom() {
        currentRoomId++;
        hasKey = false;
        if (currentRoomId == 7) isBossRoom = true;
    }

    public void startRoom() {
        DungeonRoom room = dungeon.getRoom(currentRoomId);
        if (room == null) return;
        List<Location> spawns = room.getMobSpawnLocations();
        if (spawns.isEmpty()) {
            DungeonUtils.error(player, "No spawns for Room " + currentRoomId);
            return;
        }

        activeMobs.clear();
        for (Location loc : spawns) {
            Entity e = MobManager.spawnRoomMob(loc, currentRoomId);
            if (e != null) activeMobs.add(e);
        }
        DungeonUtils.msg(player, "Spawned " + activeMobs.size() + " mobs.");
    }

    public void handleMobDeath(Entity entity) {
        if (minions.contains(entity)) {
            minions.remove(entity);
            if (minions.isEmpty() && isBossActive()) {
                isBossInvulnerable = false;
                DungeonUtils.msg(player, "<red>Boss Vulnerable!");
            }
            return;
        }

        if (activeMobs.remove(entity)) {
            DungeonUtils.msg(player, "<gray>Mob died. Remaining: <yellow>" + activeMobs.size());

            if (activeMobs.isEmpty()) {
                if (!isBossRoom) {
                    dropKey(entity.getLocation());
                }
                if (isBossRoom && bossEntity == null) {
                    spawnBoss();
                }
            }
        }
    }

    private void spawnBoss() {
        if (dungeon.getBossSpawnLocation() == null) return;
        DungeonUtils.msg(player, "<dark_red><b>BOSS SPAWNED!</b>");
        bossEntity = MobManager.spawnBoss(dungeon.getBossSpawnLocation());
    }

    public void checkBossHealth() {
        if (bossEntity == null || bossEntity.isDead()) return;
        double health = bossEntity.getHealth();
        if (health <= 200 && !bossPhase1Triggered) {
            bossPhase1Triggered = true;
            triggerMinionPhase(5);
        } else if (health <= 75 && !bossPhase2Triggered) {
            bossPhase2Triggered = true;
            triggerMinionPhase(7);
        }
    }

    private void triggerMinionPhase(int count) {
        isBossInvulnerable = true;
        DungeonUtils.msg(player, "<yellow>Boss Invulnerable! Kill minions!");
        for (int i = 0; i < count; i++) {
            minions.add(MobManager.spawnMinion(bossEntity.getLocation()));
        }
    }

    public void handleBossDeath() {
        bossDead = true;
        DungeonUtils.msg(player, "<gold><b>Dungeon Cleared!</b></gold>");
        if (dungeon.getRewardDoor() != null) {
            openDoor(dungeon.getRewardDoor());
            DungeonUtils.msg(player, "<green>Treasure Room Open!");
        }
        if (dungeon.getRewardChestLocation() != null) {
            dungeon.getRewardChestLocation().getBlock().setType(Material.CHEST);
        }
    }

    private void dropKey(Location loc) {
        cleanupKey();

        Location spawnLoc = loc.clone().add(0, 0.5, 0);

        // 1. Visual Head Stand
        keyStand = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc, EntityType.ARMOR_STAND);
        keyStand.setVisible(false);
        keyStand.setGravity(false);
        keyStand.setSmall(true);
        keyStand.setBasePlate(false);
        keyStand.setInvulnerable(true);

        ItemStack head = TextureToHead.getHead(MobManager.KEY_TEXTURE);
        keyStand.getEquipment().setHelmet(head);
        keyStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);

        // 2. Hologram Text 1
        textStand1 = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc.clone().add(0, 0.25, 0), EntityType.ARMOR_STAND);
        textStand1.setVisible(false);
        textStand1.setGravity(false);
        textStand1.setSmall(true);
        textStand1.setCustomNameVisible(true);
        textStand1.customName(Component.text("Room " + (currentRoomId + 1) + " Key", NamedTextColor.GOLD));
        textStand1.setMarker(true);

        // 3. Hologram Text 2
        textStand2 = (ArmorStand) loc.getWorld().spawnEntity(spawnLoc.clone().add(0, 0.0, 0), EntityType.ARMOR_STAND);
        textStand2.setVisible(false);
        textStand2.setGravity(false);
        textStand2.setSmall(true);
        textStand2.setCustomNameVisible(true);
        textStand2.customName(Component.text("Click to pick up", NamedTextColor.YELLOW));
        textStand2.setMarker(true);

        DungeonUtils.msg(player, "<green>The Key has appeared! Click it to pick it up!");

        // Animation Task
        keyTask = new BukkitRunnable() {
            double ticks = 0;
            final Location baseLoc = spawnLoc.clone();

            @Override
            public void run() {
                if (keyStand == null || keyStand.isDead() || player == null || !player.isOnline()) {
                    this.cancel();
                    return;
                }

                // 1. Rotation (Spin on axis)
                float yaw = (keyStand.getLocation().getYaw() + 5) % 360;

                // 2. Bobbing (2 blocks range: -1 to +1)
                double yOffset = Math.sin(ticks * 0.1) * 1.0;

                Location newLoc = baseLoc.clone().add(0, yOffset, 0);
                newLoc.setYaw(yaw);
                keyStand.teleport(newLoc);

                if (textStand1 != null) textStand1.teleport(newLoc.clone().add(0, 0.7, 0));
                if (textStand2 != null) textStand2.teleport(newLoc.clone().add(0, 0.4, 0));

                // 3. Particles (Dust)
                keyStand.getWorld().spawnParticle(Particle.CLOUD, keyStand.getLocation().add(0, 1.5, 0), 1, 0, 0, 0, 0.01);

                ticks++;
            }
        };
        keyTask.runTaskTimer(Login.getPlugin(Login.class), 0L, 1L);
    }

    public void pickupKey() {
        cleanupKey();
        hasKey = true;
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_PICKUP, 1f, 1f);
        DungeonUtils.msg(player, "<green><b>You obtained the Virtual Key!</b> <gray>Right-click the door to open it.");
    }

    private void cleanupKey() {
        if (keyStand != null && !keyStand.isDead()) keyStand.remove();
        if (textStand1 != null && !textStand1.isDead()) textStand1.remove();
        if (textStand2 != null && !textStand2.isDead()) textStand2.remove();
        if (keyTask != null && !keyTask.isCancelled()) keyTask.cancel();

        keyStand = null;
        textStand1 = null;
        textStand2 = null;
        keyTask = null;
    }

    public boolean hasKey() { return hasKey; }
    public void setHasKey(boolean hasKey) { this.hasKey = hasKey; }

    public void openDoor(me.login.dungeon.model.Cuboid region) {
        if (region == null) return;
        modifyRegion(region, Material.AIR);
    }

    public void cleanup() {
        cleanupKey();
        activeMobs.forEach(Entity::remove);
        activeMobs.clear();
        minions.forEach(Entity::remove);
        minions.clear();
        if (bossEntity != null) bossEntity.remove();
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