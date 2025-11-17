package me.login.misc.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class HologramManager {

    private final HologramModule module;
    private final Map<String, Hologram> activeHolograms = new HashMap<>();
    private final Map<UUID, Hologram> entityToHologramMap = new HashMap<>();

    public HologramManager(HologramModule module) {
        this.module = module;
    }

    public void loadHolograms() {
        despawnAllHolograms();
        activeHolograms.clear();
        entityToHologramMap.clear();

        ConfigurationSection holoData = module.getDatabase().getConfig().getConfigurationSection("holograms");
        if (holoData == null) {
            module.getPlugin().getLogger().info("No holograms found in database to load.");
            return;
        }

        for (String holoName : holoData.getKeys(false)) {
            ConfigurationSection data = holoData.getConfigurationSection(holoName);
            if (data == null) continue;

            String worldName = data.getString("location.world");
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                module.getPlugin().getLogger().warning("World '" + worldName + "' for hologram '" + holoName + "' not found. Skipping.");
                continue;
            }

            Location location = new Location(
                    world,
                    data.getDouble("location.x"),
                    data.getDouble("location.y"),
                    data.getDouble("location.z")
            );

            List<String> page1Lines = getHologramLines(holoName, 1);
            if (page1Lines.isEmpty()) {
                module.getPlugin().getLogger().warning("Hologram '" + holoName + "' has no 'page1' configured in config.yml. Skipping.");
                continue;
            }

            Hologram hologram = new Hologram(holoName, location, page1Lines.size());
            hologram.spawn(page1Lines); // Spawn with default text (page 1)

            activeHolograms.put(holoName, hologram);
            for (UUID entityId : hologram.getAllEntityUUIDs()) {
                entityToHologramMap.put(entityId, hologram);
            }
        }
        module.getPlugin().getLogger().info("Successfully loaded " + activeHolograms.size() + " holograms.");
    }

    public void spawnHologram(String name, Location location) {
        if (activeHolograms.containsKey(name)) {
            despawnHologram(name);
        }

        List<String> page1Lines = getHologramLines(name, 1);
        if (page1Lines.isEmpty()) {
            module.getPlugin().getLogger().warning("Cannot spawn hologram '" + name + "': No 'page1' configured in config.yml.");
            return;
        }

        Hologram hologram = new Hologram(name, location, page1Lines.size());
        hologram.spawn(page1Lines);

        activeHolograms.put(name, hologram);
        for (UUID entityId : hologram.getAllEntityUUIDs()) {
            entityToHologramMap.put(entityId, hologram);
        }

        // Save to database
        module.getDatabase().saveHologram(hologram);
    }

    public void despawnHologram(String name) {
        Hologram hologram = activeHolograms.remove(name);
        if (hologram != null) {
            hologram.getAllEntityUUIDs().forEach(entityToHologramMap::remove);
            hologram.remove();
        }
    }

    public void despawnAllHolograms() {
        activeHolograms.values().forEach(Hologram::remove);
        activeHolograms.clear();
        entityToHologramMap.clear();
    }

    public void reloadHologram(String name) {
        Hologram hologram = activeHolograms.get(name);
        if (hologram == null) {
            module.getPlugin().getLogger().warning("Cannot reload hologram '" + name + "': Not found.");
            return;
        }

        List<String> page1Lines = getHologramLines(name, 1);
        if (page1Lines.isEmpty()) {
            module.getPlugin().getLogger().warning("Cannot reload hologram '" + name + "': No 'page1' configured.");
            return;
        }

        // Re-spawn with new line count if necessary, or just update text
        if (page1Lines.size() != hologram.getLineCount()) {
            hologram.remove();
            Hologram newHologram = new Hologram(name, hologram.getBaseLocation(), page1Lines.size());
            newHologram.spawn(page1Lines);

            // Update maps
            hologram.getAllEntityUUIDs().forEach(entityToHologramMap::remove);
            activeHolograms.put(name, newHologram);
            newHologram.getAllEntityUUIDs().forEach(uuid -> entityToHologramMap.put(uuid, newHologram));
        } else {
            hologram.updateTextGlobal(page1Lines);
        }
    }

    public Hologram getHologramByEntity(UUID entityId) {
        return entityToHologramMap.get(entityId);
    }

    public Hologram getHologramByName(String name) {
        return activeHolograms.get(name);
    }

    public Collection<String> getHologramNames() {
        ConfigurationSection config = module.getPlugin().getConfig().getConfigurationSection("hologram");
        if (config == null) {
            return List.of();
        }
        return config.getKeys(false);
    }

    public List<String> getHologramLines(String name, int page) {
        return module.getPlugin().getConfig().getStringList("hologram." + name + ".page" + page);
    }

    public void respawnHologram(Hologram hologram) {
        if (hologram == null) return;
        module.getPlugin().getLogger().info("Respawning hologram: " + hologram.getName());
        hologram.spawn(getHologramLines(hologram.getName(), 1)); // Respawn with page 1
        hologram.getAllEntityUUIDs().forEach(uuid -> entityToHologramMap.put(uuid, hologram));
    }

    //
    // Removed updatePlayerHologramPage(Player player, Hologram hologram, int page)
    // as it depended on the now-removed Hologram.updateTextPerPlayer method.
    //
}