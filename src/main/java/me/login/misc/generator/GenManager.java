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
    // Custom player limits cache: UUID -> Limit
    private final Map<String, Integer> customLimits = new ConcurrentHashMap<>();

    private BukkitRunnable task;

    public GenManager(Login plugin, GenDatabase database, GenItemManager itemManager, GenLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.itemManager = itemManager;
        this.logger = logger;
    }

    public void loadGenerators() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // Load Generators
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
                    gen.secondsElapsed++; // Now counting seconds
                    GenItemManager.GenInfo info = itemManager.getGenInfo(gen.tierId);
                    if (info == null) continue;

                    if (gen.secondsElapsed >= info.speed) {
                        gen.secondsElapsed = 0;
                        spawnDrop(gen, info);
                    }
                }
            }
        };
        // FIX: Run every 20 ticks (1 second) so speed is interpreted as seconds
        task.runTaskTimer(plugin, 20L, 20L);
    }

    private void spawnDrop(GenInstance gen, GenItemManager.GenInfo info) {
        World world = Bukkit.getWorld(gen.world);
        if (world == null) return;
        if (!world.isChunkLoaded(gen.x >> 4, gen.z >> 4)) return;

        Location loc = new Location(world, gen.x + 0.5, gen.y + 2.0, gen.z + 0.5); // 2 blocks above
        ItemStack drop = itemManager.getDropItem(gen.tierId);

        if (drop != null) {
            world.spawnParticle(Particle.POOF, loc, 5, 0.1, 0.1, 0.1, 0.05);
            Item itemEntity = world.dropItem(loc, drop);
            itemEntity.setVelocity(new Vector(0, 0.1, 0));
        }
    }

    public void placeGenerator(org.bukkit.entity.Player player, Location loc, String tierId) {
        String locKey = locToString(loc);
        activeGenerators.put(locKey, new GenInstance(player.getUniqueId().toString(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tierId));

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.addGenerator(player.getUniqueId().toString(), loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), tierId);
        });

        logger.logPlace(player.getName(), tierId, locKey);
    }

    public boolean breakGenerator(org.bukkit.entity.Player player, Location loc) {
        String locKey = locToString(loc);
        GenInstance gen = activeGenerators.remove(locKey);
        if (gen == null) return false;

        if (!player.hasPermission("admin.genbreak") && !gen.ownerUUID.equals(player.getUniqueId().toString())) {
            activeGenerators.put(locKey, gen);
            return false;
        }

        ItemStack item = itemManager.getGeneratorItem(gen.tierId);
        if (item != null) {
            loc.getWorld().dropItemNaturally(loc, item);
        }

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
            // Using correct server_prefix from config
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Max tier reached!"));
            return;
        }

        GenItemManager.GenInfo nextInfo = itemManager.getGenInfo(currentInfo.nextGenId);
        if (nextInfo == null) {
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Next tier definition not found!"));
            return;
        }

        double cost = currentInfo.upgradeCost;
        boolean useSkript = (nextInfo.tier >= 25);

        if (useSkript) {
            if (plugin.getServer().getPluginManager().getPlugin("Skript") == null) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Error: Skript not found."));
                return;
            }
            String varName = "credits." + player.getUniqueId().toString();
            Object val = SkriptUtils.getVar(varName);
            double balance = (val instanceof Number) ? ((Number) val).doubleValue() : 0.0;

            if (balance < cost) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Not enough credits! Need " + cost));
                return;
            }
            SkriptUtils.setVar(varName, balance - cost);
            logger.logUpgrade(player.getName(), gen.tierId, nextInfo.id, cost, "Credits");

        } else {
            if (plugin.getVaultEconomy().getBalance(player) < cost) {
                player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Not enough money! Need $" + cost));
                return;
            }
            plugin.getVaultEconomy().withdrawPlayer(player, cost);
            logger.logUpgrade(player.getName(), gen.tierId, nextInfo.id, cost, "$");
        }

        // Update logic
        gen.tierId = nextInfo.id;
        Material newMat = nextInfo.genItem.getType();
        loc.getBlock().setType(newMat);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.updateGeneratorTier(gen.world, gen.x, gen.y, gen.z, nextInfo.id);
        });

        // Message with correct prefix
        player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Upgraded to " + nextInfo.displayName + "!"));

        // Send Title (Requested Feature)
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getConfig().getString("server_prefix_2")),
                plugin.getComponentSerializer().deserialize("<green>Generator Upgraded!")
        );
        player.showTitle(title);

        loc.getWorld().spawnParticle(Particle.COMPOSTER, loc.clone().add(0.5, 1, 0.5), 10);
    }

    public int getPlayerLimit(org.bukkit.entity.Player player) {
        String uuid = player.getUniqueId().toString();

        // Check cache first
        if (customLimits.containsKey(uuid)) {
            return customLimits.get(uuid);
        }

        // Check DB (sync for first load if not in cache? Or just fallback to permission for now)
        // Ideally, limits are loaded on join. For this implementation, we check DB or perm.
        Integer dbLimit = database.getPlayerLimit(uuid);
        if (dbLimit != null) {
            customLimits.put(uuid, dbLimit);
            return dbLimit;
        }

        // Permissions
        if (player.hasPermission("admin.genplace")) return 1000;
        if (player.hasPermission("phantom.genplace")) return 18;
        if (player.hasPermission("supreme.genplace")) return 15;
        if (player.hasPermission("immortal.genplace")) return 13;
        if (player.hasPermission("overlord.genplace")) return 11;
        if (player.hasPermission("ace.genplace")) return 9;
        if (player.hasPermission("elite.genplace")) return 7;
        return 5;
    }

    public void setPlayerLimit(String uuid, int limit) {
        customLimits.put(uuid, limit);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            database.setPlayerLimit(uuid, limit);
        });
    }

    public void shutdown() {
        if (task != null) task.cancel();
        activeGenerators.clear();
        customLimits.clear();
        database.close();
    }

    private String locToString(String w, int x, int y, int z) {
        return w + "," + x + "," + y + "," + z;
    }

    public String locToString(Location loc) {
        return loc.getWorld().getName() + "," + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    public Map<String, GenInstance> getActiveGenerators() { return activeGenerators; }

    public static class GenInstance {
        public String ownerUUID;
        public String world;
        public int x, y, z;
        public String tierId;
        public int secondsElapsed = 0;

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