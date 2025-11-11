package me.login.misc.holograms;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set; // --- ADDED ---

/**
 * Manages loading hologram configurations from the config.yml.
 * Loads holograms from the "command_hologram" section.
 */
public class HologramConfig {

    private final Map<String, List<List<String>>> hologramPages = new HashMap<>();

    /**
     * Loads all hologram configurations from the plugin's config.yml.
     *
     * @param plugin The main plugin instance.
     */
    public void load(JavaPlugin plugin) {
        hologramPages.clear();
        FileConfiguration config = plugin.getConfig();
        ConfigurationSection holoSection = config.getConfigurationSection("command_hologram");

        if (holoSection == null) {
            plugin.getLogger().warning("No 'command_hologram' section found in config.yml.");
            return;
        }

        for (String hologramName : holoSection.getKeys(false)) {
            ConfigurationSection nameSection = holoSection.getConfigurationSection(hologramName);
            if (nameSection == null) continue;

            List<List<String>> pages = new ArrayList<>();
            int pageNum = 1;
            // Loop while "page_1", "page_2", etc. exist
            while (nameSection.isList("page_" + pageNum)) {
                pages.add(nameSection.getStringList("page_" + pageNum));
                pageNum++;
            }

            if (pages.isEmpty()) {
                plugin.getLogger().warning("Hologram '" + hologramName + "' has no pages defined.");
            } else {
                hologramPages.put(hologramName.toLowerCase(), pages);
            }
        }
    }

    /**
     * Gets the configured pages for a specific hologram.
     *
     * @param name The name of the hologram.
     * @return A list of pages, where each page is a list of lines. Returns null if not found.
     */
    public List<List<String>> getHologramPages(String name) {
        return hologramPages.get(name.toLowerCase());
    }

    /**
     * Checks if a hologram with the given name is configured.
     *
     * @param name The name of the hologram.
     * @return true if it exists, false otherwise.
     */
    public boolean hasHologram(String name) {
        return hologramPages.containsKey(name.toLowerCase());
    }

    /**
     * Gets all configured hologram names.
     *
     * @return A Set of hologram names.
     */
    public Set<String> getHologramNames() {
        return hologramPages.keySet();
    }
}