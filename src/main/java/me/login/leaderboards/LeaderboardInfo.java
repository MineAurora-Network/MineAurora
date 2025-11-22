package me.login.leaderboards;

import org.bukkit.Location;

public record LeaderboardInfo(String type, String worldName, double x, double y, double z) {

    public Location getLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null;
        }
        return new Location(world, x, y, z);
    }
}