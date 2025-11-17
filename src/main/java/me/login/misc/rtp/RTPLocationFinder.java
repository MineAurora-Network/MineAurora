package me.login.misc.rtp;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.EnumSet;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public class RTPLocationFinder {

    private final RTPModule module;
    private final Random random = ThreadLocalRandom.current();
    // Added more unsafe blocks
    private final EnumSet<Material> unsafeBlocks = EnumSet.of(
            Material.LAVA, Material.WATER, Material.CACTUS, Material.FIRE, Material.MAGMA_BLOCK,
            Material.VINE, Material.COBWEB
    );

    private static final int MIN_RANGE = 300;
    private static final int MAX_RANGE = 3000;
    public static final int MAX_TRIES = 25; // Made public for command message
    private static final int NETHER_MAX_Y = 120; // Search from just below nether roof

    public RTPLocationFinder(RTPModule module) {
        this.module = module;
    }

    public CompletableFuture<Location> findSafeLocationAsync(World world) {
        // Uses default Java async pool
        return CompletableFuture.supplyAsync(() -> {
            if (world.getEnvironment() == World.Environment.NETHER) {
                return findSafeLocationNether(world);
            } else {
                return findSafeLocationSurface(world); // Overworld and End logic
            }
        });
    }

    private Location findSafeLocationSurface(World world) {
        for (int i = 0; i < MAX_TRIES; i++) {
            int x = getRandomCoordinate();
            int z = getRandomCoordinate();

            Block groundBlock = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING);
            Block blockFeet = groundBlock.getRelative(BlockFace.UP);

            if (isSafeBlock(blockFeet)) {
                Location potentialLoc = blockFeet.getLocation().add(0.5, 0, 0.5);
                if (isSafeWorldGuardSync(potentialLoc)) {
                    return potentialLoc;
                }
            }
        }
        return null;
    }

    private Location findSafeLocationNether(World world) {
        for (int i = 0; i < MAX_TRIES; i++) {
            int x = getRandomCoordinate();
            int z = getRandomCoordinate();

            for (int y = NETHER_MAX_Y; y >= world.getMinHeight(); y--) {
                Block blockFeet = world.getBlockAt(x, y, z);

                // Use the improved safety check
                if (isSafeBlock(blockFeet)) {
                    Location potentialLoc = blockFeet.getLocation().add(0.5, 0, 0.5);
                    if (isSafeWorldGuardSync(potentialLoc)) {
                        return potentialLoc;
                    }
                    // Skip a few blocks down to speed up search
                    y -= 5;
                }
            }
        }
        return null;
    }

    private int getRandomCoordinate() {
        int coord = random.nextInt(MAX_RANGE - MIN_RANGE + 1) + MIN_RANGE;
        return random.nextBoolean() ? coord : -coord;
    }

    /**
     * Improved safety check
     */
    private boolean isSafeBlock(Block blockFeet) {
        Block blockHead = blockFeet.getRelative(BlockFace.UP);
        Block blockGround = blockFeet.getRelative(BlockFace.DOWN);

        // **CRITICAL FIX:** Check if the block at foot level is unsafe (e.g., lava)
        if (unsafeBlocks.contains(blockFeet.getType())) {
            return false;
        }
        // Check if head block is unsafe (e.g., lava)
        if (unsafeBlocks.contains(blockHead.getType())) {
            return false;
        }
        // Check for solid ground
        if (blockGround.isPassable()) {
            return false;
        }
        // Check if the solid ground is an unsafe block
        if (unsafeBlocks.contains(blockGround.getType())) {
            return false;
        }
        // Check if feet and head space are passable
        if (!blockFeet.isPassable() || !blockHead.isPassable()) {
            return false;
        }

        return true;
    }

    private boolean isSafeWorldGuardSync(Location loc) {
        if (!module.isWorldGuardEnabled()) {
            return true;
        }

        CompletableFuture<Boolean> wgCheck = new CompletableFuture<>();
        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Ensure chunk is loaded before checking
                    if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
                        loc.getWorld().loadChunk(loc.getBlockX() >> 4, loc.getBlockZ() >> 4);
                    }

                    // This is the correct WorldGuard 7 API
                    RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
                    RegionManager regions = container.get(BukkitAdapter.adapt(loc.getWorld()));

                    if (regions == null) {
                        wgCheck.complete(true); // No region manager, assume safe
                        return;
                    }

                    // Use BlockVector3 as required by WG7
                    BlockVector3 locVector = BlockVector3.at(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
                    ApplicableRegionSet set = regions.getApplicableRegions(locVector);
                    wgCheck.complete(set.size() == 0); // Safe if no regions apply
                } catch (Exception e) {
                    module.getLogger().log("Error checking WorldGuard region: " + e.getMessage());
                    wgCheck.complete(false); // Assume unsafe on error
                }
            }
        }.runTask(module.getPlugin());

        try {
            // Wait for the main thread check to complete
            return wgCheck.get();
        } catch (Exception e) {
            module.getLogger().log("Error getting WorldGuard check result: " + e.getMessage());
            return false;
        }
    }
}