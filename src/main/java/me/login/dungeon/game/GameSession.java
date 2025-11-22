package me.login.dungeon.game;

import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonUtils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.ItemStack;

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

    public GameSession(Player player, Dungeon dungeon) {
        this.player = player;
        this.playerId = player.getUniqueId();
        this.dungeon = dungeon;
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

        // Spawn 1 mob at EVERY location registered
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
            // If ALL mobs are dead, drop key
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
        hasKey = true;
        ItemStack key = MobManager.getKey();
        loc.getWorld().dropItemNaturally(loc, key);
        DungeonUtils.msg(player, "<green>Key dropped!");
    }

    public boolean hasKey() { return hasKey; }
    public void setHasKey(boolean hasKey) { this.hasKey = hasKey; }

    public void openDoor(me.login.dungeon.model.Cuboid region) {
        if (region == null) return;
        modifyRegion(region, Material.AIR);
    }

    public void cleanup() {
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