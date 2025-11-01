package me.login.clearlag;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Boat;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Minecart; // <-- CORRECT IMPORT
import org.bukkit.entity.Projectile;
import org.bukkit.plugin.Plugin;

import java.util.Arrays;
import java.util.List;

/**
 * Utility class to handle the actual entity cleanup logic.
 * This can be called from a command or a scheduled task.
 */
public class EntityCleanup {

    // List of worlds to clean. Made public static to be accessible by CleanupTask
    public static final List<String> WORLDS_TO_CLEAN = Arrays.asList(
            "lifesteal",
            "arena",
            "normal_world",
            "nether",
            "end"
    );

    /**
     * Performs a cleanup of specified entities in the configured worlds.
     * @param plugin The main plugin instance, used for logging.
     * @return The total number of entities removed.
     */
    public static int performCleanup(Plugin plugin) {
        int totalRemoved = 0;
        for (String worldName : WORLDS_TO_CLEAN) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("Could not find world '" + worldName + "' for cleanup. Skipping.");
                continue;
            }

            int removedInWorld = 0;
            // We get a snapshot of the entities to avoid ConcurrentModificationException
            for (Entity entity : world.getEntities()) {

                // 1. Clear Dropped Items
                if (entity instanceof Item) {
                    entity.remove();
                    removedInWorld++;
                }
                // 2. Clear All Projectiles (Arrows, Snowballs, Eggs, etc.)
                else if (entity instanceof Projectile) {
                    entity.remove();
                    removedInWorld++;
                }
                // 3. Clear Boats
                else if (entity instanceof Boat) {
                    entity.remove();
                    removedInWorld++;
                }
                // 4. Clear All Minecart Types
                else if (entity instanceof Minecart) {
                    entity.remove();
                    removedInWorld++;
                }
            }

            if (removedInWorld > 0) {
                plugin.getLogger().info("Removed " + removedInWorld + " entities from world: " + worldName);
            }
            totalRemoved += removedInWorld;
        }

        plugin.getLogger().info("Cleanup complete. Total entities removed: " + totalRemoved);
        return totalRemoved;
    }
}