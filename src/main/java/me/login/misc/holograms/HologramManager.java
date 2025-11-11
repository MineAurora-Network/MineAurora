package me.login.misc.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages the creation, deletion, and client-side pagination of holograms.
 */
public class HologramManager {

    private final JavaPlugin plugin;
    private final HologramConfig hologramConfig;
    private final HologramStorage hologramStorage; // --- ADDED ---

    // Stores the server-side representation of a hologram's entities
    private final Map<String, ServerHologram> serverHolograms = new HashMap<>();

    // Tracks which page each player is currently viewing for each hologram
    private final Map<UUID, PlayerHologram> activePlayerHolograms = new HashMap<>();

    // Maps interaction entity IDs to their hologram and action ("holoName:next" or "holoName:prev")
    private final Map<UUID, String> interactionEntityIds = new HashMap<>();

    // Internal record to store server-side entity IDs for a hologram
    private record ServerHologram(
            String name,
            Location baseLocation,
            List<List<UUID>> pageEntityIds, // List of pages, each with a list of entity IDs for its lines
            UUID nextButtonId,
            UUID prevButtonId,
            UUID nextTextId,
            UUID prevTextId
    ) {}

    // Internal record to track a player's current hologram state
    private record PlayerHologram(UUID playerId, String hologramName, int currentPage) {}

    public HologramManager(JavaPlugin plugin, HologramConfig hologramConfig, HologramStorage hologramStorage) { // --- MODIFIED ---
        this.plugin = plugin;
        this.hologramConfig = hologramConfig;
        this.hologramStorage = hologramStorage; // --- ADDED ---
    }

    /**
     * Creates and spawns a new hologram at the specified location.
     *
     * @param name The name of the hologram (must match config.yml).
     * @param loc  The location to spawn the hologram.
     */
    public void createHologram(String name, Location loc) {
        name = name.toLowerCase();
        if (!hologramConfig.hasHologram(name)) {
            return;
        }

        // Remove old hologram if it exists
        removeHologram(name);

        List<List<String>> pages = hologramConfig.getHologramPages(name);
        if (pages == null || pages.isEmpty()) {
            return;
        }

        List<List<UUID>> pageEntityIds = new ArrayList<>();
        double lineSpacing = 0.3; // Space between lines

        // Spawn all TextDisplay entities for all pages
        for (List<String> pageLines : pages) {
            List<UUID> entityIdsForThisPage = new ArrayList<>();
            Location currentLineLoc = loc.clone();
            for (String line : pageLines) {
                TextDisplay td = loc.getWorld().spawn(currentLineLoc, TextDisplay.class, e -> {
                    e.text(TextUtil.deserialize(line));
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent background
                    e.setShadowed(true); // --- ADDED ---
                    e.setViewRange(30f); // --- ADDED ---
                    e.setAlignment(TextDisplay.TextAlignment.CENTER); // --- ADDED ---
                });
                entityIdsForThisPage.add(td.getUniqueId());
                currentLineLoc.subtract(0, lineSpacing, 0); // Move down for next line
            }
            pageEntityIds.add(entityIdsForThisPage);
        }

        boolean paginated = pages.size() > 1;
        UUID nextButtonId = null, prevButtonId = null, nextTextId = null, prevTextId = null;

        if (paginated) {
            // Spawn pagination buttons below the text
            Location buttonRowLoc = loc.clone().subtract(0, (pages.get(0).size() * lineSpacing) + 0.2, 0);
            Location prevLoc = buttonRowLoc.clone().subtract(0.7, 0, 0);
            Location nextLoc = buttonRowLoc.clone().add(0.7, 0, 0);

            // Previous Button (Text + Interaction)
            TextDisplay prevText = loc.getWorld().spawn(prevLoc, TextDisplay.class, e -> {
                e.text(Component.text("◀ PREV").color(NamedTextColor.RED));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setViewRange(30f); // --- ADDED ---
                e.setAlignment(TextDisplay.TextAlignment.CENTER); // --- ADDED ---
                e.setShadowed(true); // --- ADDED ---
            });
            Interaction prevInteraction = loc.getWorld().spawn(prevLoc, Interaction.class, e -> {
                e.setInteractionWidth(1.0f);
                e.setInteractionHeight(0.3f);
            });
            prevTextId = prevText.getUniqueId();
            prevButtonId = prevInteraction.getUniqueId();
            interactionEntityIds.put(prevButtonId, name + ":prev");

            // Next Button (Text + Interaction)
            TextDisplay nextText = loc.getWorld().spawn(nextLoc, TextDisplay.class, e -> {
                e.text(Component.text("NEXT ▶").color(NamedTextColor.GREEN));
                e.setBillboard(Display.Billboard.CENTER);
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                e.setViewRange(30f); // --- ADDED ---
                e.setAlignment(TextDisplay.TextAlignment.CENTER); // --- ADDED ---
                e.setShadowed(true); // --- ADDED ---
            });
            Interaction nextInteraction = loc.getWorld().spawn(nextLoc, Interaction.class, e -> {
                e.setInteractionWidth(1.0f);
                e.setInteractionHeight(0.3f);
            });
            nextTextId = nextText.getUniqueId();
            nextButtonId = nextInteraction.getUniqueId();
            interactionEntityIds.put(nextButtonId, name + ":next");
        }

        ServerHologram serverHolo = new ServerHologram(name, loc, pageEntityIds, nextButtonId, prevButtonId, nextTextId, prevTextId);
        serverHolograms.put(name, serverHolo);
        hologramStorage.saveHologram(name, loc); // --- ADDED: Save to YML ---

        // Hide all pages, then show page 1 for all online players
        hideAllPages(serverHolo);
        showPage(serverHolo, 0); // Show page 1 by default
    }

    /**
     * Removes a hologram from the world and cleans up its entities.
     *
     * @param name The name of the hologram.
     */
    public void removeHologram(String name) {
        ServerHologram serverHolo = serverHolograms.remove(name.toLowerCase());
        if (serverHolo == null) return;

        // Remove all text display entities
        for (List<UUID> page : serverHolo.pageEntityIds()) {
            for (UUID entityUuid : page) {
                Entity e = Bukkit.getEntity(entityUuid);
                if (e != null) e.remove();
            }
        }

        // Remove button entities
        UUID[] buttonUuids = {serverHolo.nextButtonId(), serverHolo.prevButtonId(), serverHolo.nextTextId(), serverHolo.prevTextId()};
        for (UUID entityUuid : buttonUuids) {
            if (entityUuid != null) {
                Entity e = Bukkit.getEntity(entityUuid);
                if (e != null) e.remove();
                interactionEntityIds.remove(entityUuid);
            }
        }

        hologramStorage.removeHologram(name); // --- ADDED: Remove from YML ---
    }

    /**
     * Removes all active holograms. Called on plugin disable.
     */
    public void removeAllHolograms() {
        // Create a copy of keys to avoid ConcurrentModificationException
        new ArrayList<>(serverHolograms.keySet()).forEach(this::removeHologram); // This already calls hologramStorage.removeHologram
        activePlayerHolograms.clear();
        interactionEntityIds.clear();
    }

    // --- NEW METHODS ADDED ---

    /**
     * Loads all holograms from storage and spawns them.
     * Called on plugin enable.
     */
    public void spawnHologramsFromStorage() {
        plugin.getLogger().info("Spawning holograms from storage...");
        Map<String, Location> hologramsToSpawn = hologramStorage.loadHolograms();
        if (hologramsToSpawn.isEmpty()) {
            plugin.getLogger().info("No holograms found in storage.");
            return;
        }

        for (Map.Entry<String, Location> entry : hologramsToSpawn.entrySet()) {
            String name = entry.getKey();
            Location loc = entry.getValue();
            if (hologramConfig.hasHologram(name)) {
                plugin.getLogger().info("Spawning hologram '" + name + "' at " + loc.toVector());
                createHologram(name, loc); // createHologram already saves, but it removes old first, so it's fine
            } else {
                plugin.getLogger().warning("Hologram '" + name + "' found in storage but not in config.yml. Removing from storage.");
                hologramStorage.removeHologram(name);
            }
        }
    }

    /**
     * Removes all hologram entities from the world and clears them from storage.
     * Called by /commandhologram killall
     */
    public void killAllHolograms() {
        // Create a copy of keys to avoid ConcurrentModificationException
        new ArrayList<>(serverHolograms.keySet()).forEach(this::removeHologram);
        activePlayerHolograms.clear();
        interactionEntityIds.clear();
        hologramStorage.removeAllHolograms(); // Clear the YML file
    }

    /**
     * Reloads hologram config and respawns all holograms from storage.
     * Called by /commandhologram reload
     */
    public void reloadAllHolograms() {
        plugin.getLogger().info("Reloading all holograms...");
        // 1. Reload the config text definitions
        hologramConfig.load(plugin);

        // 2. Get the list of holograms to respawn
        Map<String, Location> hologramsToRespawn = hologramStorage.loadHolograms();

        // 3. Remove all current entities
        // Create a copy of keys to avoid ConcurrentModificationException
        new ArrayList<>(serverHolograms.keySet()).forEach(this::removeHologram);
        activePlayerHolograms.clear();
        interactionEntityIds.clear();
        // Do NOT clear storage here

        // 4. Respawn all holograms from storage with new config text
        for (Map.Entry<String, Location> entry : hologramsToRespawn.entrySet()) {
            String name = entry.getKey();
            Location loc = entry.getValue();
            if (hologramConfig.hasHologram(name)) {
                plugin.getLogger().info("Respawning hologram '" + name + "'...");
                createHologram(name, loc);
            } else {
                plugin.getLogger().warning("Hologram '" + name + "' was in storage but is no longer in config.yml. Removing from storage.");
                hologramStorage.removeHologram(name);
            }
        }
        plugin.getLogger().info("Hologram reload complete.");
    }

    // --- END NEW METHODS ---

    // --- Player Event Handlers ---

    /**
     * Called when a player interacts with an entity.
     * Checks if the entity is a hologram button and changes the player's page.
     *
     * @param player   The player who interacted.
     * @param entityUuid The entity UUID they clicked.
     */
    public void onPlayerInteract(Player player, UUID entityUuid) {
        String action = interactionEntityIds.get(entityUuid);
        if (action == null) return;

        String[] parts = action.split(":");
        String hologramName = parts[0];
        String buttonType = parts[1];

        ServerHologram serverHolo = serverHolograms.get(hologramName);
        PlayerHologram playerHolo = activePlayerHolograms.get(player.getUniqueId());

        // Ensure player is viewing this hologram
        if (serverHolo == null || playerHolo == null || !playerHolo.hologramName().equals(hologramName)) {
            // This can happen if the player joins *after* a hologram is spawned
            // Let's initialize them
            if (serverHolo != null) {
                playerHolo = new PlayerHologram(player.getUniqueId(), hologramName, 0);
                activePlayerHolograms.put(player.getUniqueId(), playerHolo);
                // Sync them to page 0
                hideAllPages(player, serverHolo);
                showPage(player, serverHolo, 0);
            } else {
                return;
            }
        }

        int numPages = serverHolo.pageEntityIds().size();
        if (numPages <= 1) return; // Not paginated

        int currentPage = playerHolo.currentPage();
        int newPage = currentPage;

        if (buttonType.equals("next")) {
            newPage = (currentPage + 1) % numPages; // Wrap around
        } else if (buttonType.equals("prev")) {
            newPage = (currentPage - 1 + numPages) % numPages; // Wrap around
        }

        if (newPage != currentPage) {
            // Client-side update: hide old page, show new page
            hidePage(player, serverHolo, currentPage);
            showPage(player, serverHolo, newPage);
            // Update the player's tracked page
            activePlayerHolograms.put(player.getUniqueId(), new PlayerHologram(player.getUniqueId(), hologramName, newPage));
        }
    }

    /**
     * Called on PlayerJoinEvent.
     * Hides all hologram pages and shows page 1 to the joining player.
     *
     * @param player The player who joined.
     */
    public void onPlayerJoin(Player player) {
        for (ServerHologram serverHolo : serverHolograms.values()) {
            hideAllPages(player, serverHolo);
            showPage(player, serverHolo, 0); // Show page 1
            // Initialize their hologram state
            activePlayerHolograms.put(player.getUniqueId(), new PlayerHologram(player.getUniqueId(), serverHolo.name(), 0));
        }
    }

    /**
     * Called on PlayerQuitEvent.
     * Cleans up the player's hologram state from memory.
     *
     * @param player The player who quit.
     */
    public void onPlayerQuit(Player player) {
        activePlayerHolograms.remove(player.getUniqueId());
    }

    // --- Client-Side Visibility Helpers ---

    private void showPage(Player player, ServerHologram holo, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= holo.pageEntityIds().size()) return;
        for (UUID entityUuid : holo.pageEntityIds().get(pageIndex)) {
            Entity e = Bukkit.getEntity(entityUuid);
            if (e != null) {
                player.showEntity(plugin, e);
            }
        }
    }

    private void hidePage(Player player, ServerHologram holo, int pageIndex) {
        if (pageIndex < 0 || pageIndex >= holo.pageEntityIds().size()) return;
        for (UUID entityUuid : holo.pageEntityIds().get(pageIndex)) {
            Entity e = Bukkit.getEntity(entityUuid);
            if (e != null) {
                player.hideEntity(plugin, e);
            }
        }
    }

    private void hideAllPages(Player player, ServerHologram holo) {
        for (List<UUID> page : holo.pageEntityIds()) {
            for (UUID entityUuid : page) {
                Entity e = Bukkit.getEntity(entityUuid);
                if (e != null) {
                    player.hideEntity(plugin, e);
                }
            }
        }
    }

    // --- Server-Wide Visibility Helpers ---

    private void showPage(ServerHologram holo, int pageIndex) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            showPage(player, holo, pageIndex);
            // Update player's active hologram state
            activePlayerHolograms.put(player.getUniqueId(), new PlayerHologram(player.getUniqueId(), holo.name(), pageIndex));
        }
    }

    private void hideAllPages(ServerHologram holo) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            hideAllPages(player, holo);
        }
    }
}