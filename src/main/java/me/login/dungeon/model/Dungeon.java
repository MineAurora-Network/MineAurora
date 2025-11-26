package me.login.dungeon.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Dungeon {
    private final int id;
    private Location spawnLocation;
    private Cuboid entryDoor;
    private final Map<Integer, DungeonRoom> rooms = new HashMap<>();

    private Location bossSpawnLocation;
    private Location rewardChestLocation;
    private Location miniRewardChestLocation; // New

    private Cuboid bossRoomDoor;
    private Cuboid rewardDoor;
    private boolean isOccupied;
    private final List<Location> chestLocations = new ArrayList<>();

    public Dungeon(int id) {
        this.id = id;
        for (int i = 1; i <= 6; i++) {
            rooms.put(i, new DungeonRoom(i));
        }
    }

    public int getId() { return id; }
    public Location getSpawnLocation() { return spawnLocation; }
    public void setSpawnLocation(Location spawnLocation) { this.spawnLocation = spawnLocation; }
    public Cuboid getEntryDoor() { return entryDoor; }
    public void setEntryDoor(Cuboid entryDoor) { this.entryDoor = entryDoor; }

    public DungeonRoom getRoom(int id) { return rooms.get(id); }
    public Map<Integer, DungeonRoom> getRooms() { return rooms; }

    public Location getBossSpawnLocation() { return bossSpawnLocation; }
    public void setBossSpawnLocation(Location bossSpawnLocation) { this.bossSpawnLocation = bossSpawnLocation; }

    public Location getRewardChestLocation() { return rewardChestLocation; }
    public void setRewardChestLocation(Location rewardChestLocation) { this.rewardChestLocation = rewardChestLocation; }

    public Location getMiniRewardChestLocation() { return miniRewardChestLocation; }
    public void setMiniRewardChestLocation(Location loc) { this.miniRewardChestLocation = loc; }

    public Cuboid getBossRoomDoor() { return bossRoomDoor; }
    public void setBossRoomDoor(Cuboid bossRoomDoor) { this.bossRoomDoor = bossRoomDoor; }

    public Cuboid getRewardDoor() { return rewardDoor; }
    public void setRewardDoor(Cuboid rewardDoor) { this.rewardDoor = rewardDoor; }

    public boolean isOccupied() { return isOccupied; }
    public void setOccupied(boolean occupied) { isOccupied = occupied; }

    public List<Location> getChestLocations() { return chestLocations; }
    public void addChestLocation(Location loc) { chestLocations.add(loc); }

    public boolean isSetupComplete() {
        return spawnLocation != null && entryDoor != null && bossSpawnLocation != null && rewardChestLocation != null;
    }
}