package me.login.misc.generator;

import me.login.Login;
import me.login.scoreboard.SkriptUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.sql.ResultSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GenManager {

    private final Login plugin;
    private final GenDatabase database;
    private final GenItemManager itemManager;
    private final GenLogger logger;

    // Active generators cache: Location (as string) -> GenInstance
    private final Map<String, GenInstance> activeGenerators = new ConcurrentHashMap<>();
    private BukkitRunnable task;

    public GenManager(Login plugin, GenDatabase database, GenItemManager itemManager, GenLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.itemManager = itemManager;
        this.logger = logger;
    }

    public void loadGenerators() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (ResultSet rs = database.getAllGenerators()) {
                while (rs != null && rs.next()) {
                    String worldName = rs.getString("world");
                    int x = rs.getInt("x");
                    int y = rs.getInt("y");
                    int z = rs.getInt("z");
                    String tierId = rs.getString("tier_id");
                    String owner = rs.getString("owner_uuid");

                    String locKey = locToString(worldName, x, y, z);

                    // Validate tier exists
                    if (itemManager.getGenInfo(tierId) != null) {
                        activeGenerators.put(locKey, new GenInstance(owner, worldName, x, y, z, tierId));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            // Start task after loading
            startTask();
        });
    }

    private void startTask() {
        task = new BukkitRunnable() {
            @Override
            public void run() {
                for (GenInstance gen : activeGenerators.values()) {
                    gen.ticks++;
                    GenItemManager.GenInfo info = itemManager.getGenInfo(gen.tierId);
                    if (info == null) continue; // Should not happen

                    if (gen.ticks >= info.speed) {
                        gen.ticks = 0;
                        spawnDrop(gen, info);
                    }
                }
            }
        };
        task.runTaskTimer(plugin, 20L, 1L); // Run every tick, handle speed internally
    }

    private void spawnDrop(GenInstance gen, GenItemManager.GenInfo info) {
        World world = Bukkit.getWorld(gen.world);
        if (world == null) return;
        // Check chunk loaded
        if (!world.isChunkLoaded(gen.x >> 4, gen.z >> 4)) return;

        Location loc = new Location(world, gen.x + 0.5, gen.y + 2.0, gen.z + 0.5); // 2 blocks above
        ItemStack drop = itemManager.getDropItem(gen.tierId);

        if (drop != null) {
            world.spawnParticle(Particle.POOF, loc, 5, 0.1, 0.1, 0.1, 0.05);

            Item itemEntity = world.dropItem(loc, drop);
            itemEntity.setVelocity(new Vector(0, 0.1, 0)); // Small hop
        }
    }

    public void placeGenerator(org.bukkit.entity.Player player, Location loc, String tierId) {
        String locKey = locToString(loc);
        activeGenerators.put(locKey, new GenInstance(player.getUniqueId().toString(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tierId));

        // Save to DB async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.addGenerator(player.getUniqueId().toString(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tierId);
        });

        logger.logPlace(player.getName(), tierId, locKey);
    }

    public boolean breakGenerator(org.bukkit.entity.Player player, Location loc) {
        String locKey = locToString(loc);
        GenInstance gen = activeGenerators.remove(locKey);
        if (gen == null) return false;

        // Check ownership (or admin)
        if (!player.hasPermission("admin.genbreak") && !gen.ownerUUID.equals(player.getUniqueId().toString())) {
            activeGenerators.put(locKey, gen); // Put back
            return false;
        }

        // Drop the generator item
        ItemStack item = itemManager.getGeneratorItem(gen.tierId);
        if (item != null) {
            // FIX: Changed dropItemNatural -> dropItemNaturally
            loc.getWorld().dropItemNaturally(loc, item);
        }

        // DB Removal
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.removeGenerator(gen.world, gen.x, gen.y, gen.z);
        });

        logger.logBreak(player.getName(), gen.tierId, locKey);
        return true;
    }

    public void upgradeGenerator(org.bukkit.entity.Player player, Location loc) {
        String locKey = locToString(loc);
        GenInstance gen = activeGenerators.get(locKey);
        if (gen == null) return;

        GenItemManager.GenInfo currentInfo = itemManager.getGenInfo(gen.tierId);
        if (currentInfo == null || currentInfo.nextGenId == null || currentInfo.nextGenId.equalsIgnoreCase("none")) {
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Max tier reached!"));
            return;
        }

        GenItemManager.GenInfo nextInfo = itemManager.getGenInfo(currentInfo.nextGenId);
        if (nextInfo == null) {
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Next tier definition not found!"));
            return;
        }

        double cost = currentInfo.upgradeCost;
        boolean useSkript = (nextInfo.tier >= 25); // Amethyst/Netherite use Credits

        if (useSkript) {
            // Use Skript Variable
            if (plugin.getServer().getPluginManager().getPlugin("Skript") == null) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Error: Skript not found for credit transaction."));
                return;
            }
            String varName = "credits." + player.getUniqueId().toString();
            Object val = SkriptUtils.getVar(varName);
            double balance = (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;

            if (balance < cost) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Not enough credits! Need " + cost));
                return;
            }
            // Deduct
            SkriptUtils.setVar(varName, balance - cost);
            logger.logUpgrade(player.getName(), gen.tierId, nextInfo.id, cost, "Credits");

        } else {
            // Use Vault
            if (plugin.getVaultEconomy().getBalance(player) < cost) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Not enough money! Need $" + cost));
                return;
            }
            plugin.getVaultEconomy().withdrawPlayer(player, cost);
            logger.logUpgrade(player.getName(), gen.tierId, nextInfo.id, cost, "$");
        }

        // Update Logic
        gen.tierId = nextInfo.id;

        // Update Block Material
        Material newMat = nextInfo.genItem.getType();
        loc.getBlock().setType(newMat);

        // Update DB
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.updateGeneratorTier(gen.world, gen.x, gen.y, gen.z, nextInfo.id);
        });

        player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Upgraded to " + nextInfo.displayName + "!"));

        // Particles/Sound
        loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.clone().add(0.5, 1, 0.5), 10);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        activeGenerators.clear();
        database.close();
    }

    // Helper
    public String locToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }
    private String locToString(String w, int x, int y, int z) {
        return w + "," + x + "," + y + "," + z;
    }

    public Map<String, GenInstance> getActiveGenerators() { return activeGenerators; }

    public static class GenInstance {
        public String ownerUUID;
        public String world;
        public int x, y, z;
        public String tierId;
        public int ticks = 0;

        public GenInstance(String ownerUUID, String world, int x, int y, int z, String tierId) {
            this.ownerUUID = ownerUUID;
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.tierId = tierId;
        }
    }
}