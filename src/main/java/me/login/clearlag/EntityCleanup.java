package me.login.clearlag;

import me.login.Login; // <-- CHANGED
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Projectile;
// import org.bukkit.plugin.Plugin; // <-- REMOVED

import java.util.Arrays;
import java.util.List;

public class EntityCleanup {

    public static final List<String> WORLDS_TO_CLEAN = Arrays.asList(
            "lifesteal",
            "arena",
            "normal_world",
            "nether",
            "end"
    );

    /**
     * Performs a cleanup of specified entities in the configured worlds.
     * @param plugin The main Login plugin instance, used for logging. // <-- CHANGED
     * @return The total number of entities removed.
     */
    public static int performCleanup(Login plugin) { // <-- CHANGED
        int totalRemoved = 0;
        LagClearLogger logger = plugin.getLagClearLogger(); // <-- NEW

        for (String worldName : WORLDS_TO_CLEAN) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                String warning = "Could not find world '" + worldName + "' for cleanup. Skipping.";
                if (logger != null) logger.sendLog(warning); else plugin.getLogger().warning(warning); // <-- CHANGED
                continue;
            }

            int removedInWorld = 0;
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item || entity instanceof Projectile || entity instanceof Boat || entity instanceof Minecart) { // <-- Simplified
                    entity.remove();
                    removedInWorld++;
                }
            }

            if (removedInWorld > 0) {
                String info = "Removed " + removedInWorld + " entities from world: " + worldName;
                if (logger != null) logger.sendLog(info); else plugin.getLogger().info(info); // <-- CHANGED
            }
            totalRemoved += removedInWorld;
        }

        String completeMsg = "Cleanup complete. Total entities removed: " + totalRemoved;
        if (logger != null) logger.sendLog(completeMsg); else plugin.getLogger().info(completeMsg); // <-- CHANGED
        return totalRemoved;
    }
}