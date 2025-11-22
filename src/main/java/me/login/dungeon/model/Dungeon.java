package me.login.dungeon.model;

import org.bukkit.Location;
import java.util.HashMap;
import java.util.Map;

public class Dungeon {
    private final int id;
    private Location spawnLocation;
    private Cuboid entryDoor; // Door to start Dungeon

    // Boss & Reward Configuration
    private Location bossSpawnLocation;
    private Cuboid bossRoomDoor; // Door INTO Boss Room

    private Location rewardChestLocation;
    private Cuboid rewardDoor; // Door INTO Treasure Room (opens after boss dies)

    private final Map<Integer, DungeonRoom> rooms;
    private boolean isOccupied = false;

    public Dungeon(int id) {
        this.id = id;
        this.rooms = new HashMap<>();
    }

    public int getId() { return id; }

    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }

    public Cuboid getEntryDoor() { return entryDoor; }
    public void setEntryDoor(Cuboid entryDoor) { this.entryDoor = entryDoor; }

    public Location getBossSpawnLocation() { return bossSpawnLocation; }
    public void setBossSpawnLocation(Location bossSpawnLocation) { this.bossSpawnLocation = bossSpawnLocation; }

    public Cuboid getBossRoomDoor() { return bossRoomDoor; }
    public void setBossRoomDoor(Cuboid bossRoomDoor) { this.bossRoomDoor = bossRoomDoor; }

    public Location getRewardChestLocation() { return rewardChestLocation; }
    public void setRewardChestLocation(Location rewardChestLocation) { this.rewardChestLocation = rewardChestLocation; }

    public Cuboid getRewardDoor() { return rewardDoor; }
    public void setRewardDoor(Cuboid rewardDoor) { this.rewardDoor = rewardDoor; }

    public DungeonRoom getRoom(int roomId) {
        return rooms.computeIfAbsent(roomId, DungeonRoom::new);
    }

    public Map<Integer, DungeonRoom> getRooms() { return rooms; }

    public boolean isSetupComplete() {
        // Check key components
        return spawnLocation != null && entryDoor != null && !rooms.isEmpty()
                && bossSpawnLocation != null && rewardChestLocation != null;
    }

    public boolean isOccupied() { return isOccupied; }
    public void setOccupied(boolean occupied) { isOccupied = occupied; }
}