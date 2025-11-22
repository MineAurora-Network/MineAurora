package me.login.dungeon.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public class Cuboid {
    private final String worldName;
    private double minX, minY, minZ;
    private double maxX, maxY, maxZ;

    public Cuboid(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) throw new IllegalArgumentException("Locations must be in same world");
        this.worldName = loc1.getWorld().getName();
        this.minX = Math.min(loc1.getX(), loc2.getX());
        this.minY = Math.min(loc1.getY(), loc2.getY());
        this.minZ = Math.min(loc1.getZ(), loc2.getZ());
        this.maxX = Math.max(loc1.getX(), loc2.getX());
        this.maxY = Math.max(loc1.getY(), loc2.getY());
        this.maxZ = Math.max(loc1.getZ(), loc2.getZ());
    }

    public Cuboid(String worldName, double x1, double y1, double z1, double x2, double y2, double z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public boolean contains(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        return loc.getX() >= minX && loc.getX() <= maxX &&
                loc.getY() >= minY && loc.getY() <= maxY &&
                loc.getZ() >= minZ && loc.getZ() <= maxZ;
    }

    public boolean containsBlock(Location loc) {
        if (!loc.getWorld().getName().equals(worldName)) return false;
        // Check integer boundaries for block containment
        return loc.getBlockX() >= Math.floor(minX) && loc.getBlockX() <= Math.floor(maxX) &&
                loc.getBlockY() >= Math.floor(minY) && loc.getBlockY() <= Math.floor(maxY) &&
                loc.getBlockZ() >= Math.floor(minZ) && loc.getBlockZ() <= Math.floor(maxZ);
    }

    public World getWorld() { return Bukkit.getWorld(worldName); }
    public double getMinX() { return minX; }
    public double getMinY() { return minY; }
    public double getMinZ() { return minZ; }
    public double getMaxX() { return maxX; }
    public double getMaxY() { return maxY; }
    public double getMaxZ() { return maxZ; }

    public Location getCenter() {
        return new Location(getWorld(), minX + (maxX - minX) / 2, minY + (maxY - minY) / 2, minZ + (maxZ - minZ) / 2);
    }
}