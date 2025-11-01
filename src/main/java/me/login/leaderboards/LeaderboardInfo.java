package me.login.leaderboards;

import org.bukkit.Location;

// Using a Java Record for simplicity (requires Java 16+)
// If using older Java, make this a regular class with fields, constructor, and getters.
public record LeaderboardInfo(String type, String worldName, double x, double y, double z) {

    /**
     * Helper method to reconstruct the Bukkit Location.
     * Returns null if the world is not currently loaded.
     */
    public Location getLocation() {
        org.bukkit.World world = org.bukkit.Bukkit.getWorld(worldName);
        if (world == null) {
            return null; // World not loaded
        }
        // Use the stored coordinates to create the Location object
        return new Location(world, x, y, z);
    }
}