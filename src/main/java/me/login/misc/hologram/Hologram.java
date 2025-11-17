package me.login.misc.hologram;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color; // <-- ADDED IMPORT
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity; // <-- ADDED IMPORT
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Interaction;
import org.bukkit.entity.Player; // <-- ADDED IMPORT
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Hologram {

    private final String name;
    private final Location baseLocation;
    private final int lineCount;
    private final List<UUID> textDisplayUUIDs = new ArrayList<>();
    private final List<UUID> interactionUUIDs = new ArrayList<>();
    private final Map<Integer, UUID> lineIndexToInteractionUUID = new HashMap<>();

    private static final double LINE_HEIGHT = 0.3;
    private static final float VIEW_RANGE = 30.0f; // <-- ADDED

    public Hologram(String name, Location baseLocation, int lineCount) {
        this.name = name;
        this.baseLocation = baseLocation;
        this.lineCount = lineCount;
    }

    public void spawn(List<String> lines) {
        if (lines.size() != lineCount) {
            Bukkit.getLogger().warning("Hologram spawn failed: line count mismatch. Expected " + lineCount + ", got " + lines.size());
            return;
        }

        World world = baseLocation.getWorld();
        if (world == null) return;

        remove(); // Clear any existing entities first

        for (int i = 0; i < lineCount; i++) {
            Location lineLoc = baseLocation.clone().subtract(0, i * LINE_HEIGHT, 0);
            String lineText = lines.get(i);

            // 1. Create TextDisplay
            TextDisplay textDisplay = (TextDisplay) world.spawnEntity(lineLoc, EntityType.TEXT_DISPLAY);
            textDisplay.setPersistent(false);
            textDisplay.setInvulnerable(true);
            textDisplay.text(HologramTextParser.parse(lineText));
            textDisplay.setBillboard(Display.Billboard.CENTER);

            // --- ADDED/MODIFIED ---
            textDisplay.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent background
            textDisplay.setShadowed(true); // Added shadow
            textDisplay.setViewRange(VIEW_RANGE); // Added view range
            // --- END ---

            textDisplayUUIDs.add(textDisplay.getUniqueId());

            // 2. Create matching Interaction entity
            Interaction interaction = (Interaction) world.spawnEntity(lineLoc, EntityType.INTERACTION);
            interaction.setPersistent(false);
            interaction.setInvulnerable(true);
            interaction.setInteractionHeight((float) LINE_HEIGHT);
            interaction.setInteractionWidth(2.0f); // Make it wide enough to click easily
            // interaction.setViewRange(VIEW_RANGE); // Interaction view range (optional)

            interactionUUIDs.add(interaction.getUniqueId());
            lineIndexToInteractionUUID.put(i, interaction.getUniqueId());
        }
    }

    public void remove() {
        for (UUID uuid : getAllEntityUUIDs()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
        textDisplayUUIDs.clear();
        interactionUUIDs.clear();
        lineIndexToInteractionUUID.clear();
    }

    public void updateTextGlobal(List<String> lines) {
        if (lines.size() != lineCount) return;

        for (int i = 0; i < lineCount; i++) {
            UUID textDisplayUUID = textDisplayUUIDs.get(i);
            Entity entity = Bukkit.getEntity(textDisplayUUID);
            if (entity instanceof TextDisplay textDisplay) {
                textDisplay.text(HologramTextParser.parse(lines.get(i)));
            }
        }
    }

    //
    // Removed the updateTextPerPlayer(Player player, List<String> lines) method
    // as it is not supported by the Spigot API (it is a Paper-only feature).
    //

    public int getLineIndexByInteraction(UUID interactionUUID) {
        for (Map.Entry<Integer, UUID> entry : lineIndexToInteractionUUID.entrySet()) {
            if (entry.getValue().equals(interactionUUID)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    public List<UUID> getAllEntityUUIDs() {
        List<UUID> all = new ArrayList<>(textDisplayUUIDs);
        all.addAll(interactionUUIDs);
        return all;
    }

    // Getters
    public String getName() { return name; }
    public Location getBaseLocation() { return baseLocation; }
    public int getLineCount() { return lineCount; }
}