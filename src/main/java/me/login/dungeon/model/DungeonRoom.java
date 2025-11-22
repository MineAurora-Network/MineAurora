package me.login.dungeon.model;

import org.bukkit.Location;
import java.util.ArrayList;
import java.util.List;

public class DungeonRoom {
    private final int roomId;
    private final List<Location> mobSpawnLocations; // Changed to List
    private Cuboid doorRegion;

    public DungeonRoom(int roomId) {
        this.roomId = roomId;
        this.mobSpawnLocations = new ArrayList<>();
    }

    public int getRoomId() {
        return roomId;
    }

    public List<Location> getMobSpawnLocations() {
        return mobSpawnLocations;
    }

    public void addMobSpawnLocation(Location location) {
        this.mobSpawnLocations.add(location);
    }

    public void clearMobSpawnLocations() {
        this.mobSpawnLocations.clear();
    }

    public Cuboid getDoorRegion() {
        return doorRegion;
    }

    public void setDoorRegion(Cuboid doorRegion) {
        this.doorRegion = doorRegion;
    }
}