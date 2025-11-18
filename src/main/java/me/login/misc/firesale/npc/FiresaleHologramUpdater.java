package me.login.misc.firesale.npc;

import me.login.Login;
import me.login.misc.firesale.FiresaleManager;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FiresaleHologramUpdater extends BukkitRunnable {

    private final FiresaleManager firesaleManager;
    private final Login plugin;
    private final NPC npc;
    private final MiniMessage miniMessage;
    private String lastText = "";
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    // List to manage hologram lines
    private final List<TextDisplay> hologramLines = new ArrayList<>();
    private final NamespacedKey HOLOGRAM_TAG_KEY;

    private static final double HOLOGRAM_LINE_HEIGHT = 0.27;
    private static final double HOLOGRAM_Y_ABOVE_HEAD = 3.0; // Height above head

    public FiresaleHologramUpdater(Login plugin, FiresaleManager firesaleManager, NPC npc, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.firesaleManager = firesaleManager;
        this.npc = npc;
        this.miniMessage = miniMessage;
        this.HOLOGRAM_TAG_KEY = new NamespacedKey(plugin, "firesale_hologram");
    }

    @Override
    public void run() {
        // 1. Validation Check
        if (npc == null) {
            fullReset();
            return;
        }

        // 2. Spawn Check
        if (!npc.isSpawned()) {
            if (!lastText.isEmpty()) {
                plugin.getLogger().info("[Firesale] NPC " + npc.getId() + " is not spawned. Hiding hologram.");
                fullReset();
            }
            return;
        }

        // --- LOCATION LOGIC ---
        Location baseSpawnLoc;
        Entity entity = npc.getEntity();

        if (entity instanceof LivingEntity) {
            // It has eyes (Player, Mob, etc.) - Use eye location + offset
            baseSpawnLoc = ((LivingEntity) entity).getEyeLocation().clone().add(0, HOLOGRAM_Y_ABOVE_HEAD, 0);
        } else if (entity != null) {
            // Fallback
            baseSpawnLoc = entity.getLocation().clone().add(0, HOLOGRAM_Y_ABOVE_HEAD + 1.5, 0);
        } else {
            // Last resort
            baseSpawnLoc = npc.getStoredLocation().clone().add(0, 4.5, 0);
        }

        List<Firesale> sales = firesaleManager.getActiveSales();
        Firesale active = sales.isEmpty() ? null : sales.get(0);

        StringBuilder newName = new StringBuilder();

        if (active == null) {
            // Idle State
            newName.append("<gray><bold>Firesale Merchant</bold>%nl%")
                    .append("<dark_gray>No active sales%nl%")
                    .append("<dark_gray>Check back later!");
        }
        else if (active.getStatus() == SaleStatus.PENDING) {
            // Pending State
            newName.append("<gold><bold>UPCOMING FIRESALE</bold>%nl%")
                    .append("<yellow>Starts: <white>").append(fmt.format(active.getStartTime())).append("%nl%")
                    .append("<gray>Get ready!");
        }
        else if (active.getStatus() == SaleStatus.ACTIVE) {
            // Active State
            String itemName = "Unknown Item";
            if (active.getItem() != null) {
                if (active.getItem().getItemMeta() != null && active.getItem().getItemMeta().hasDisplayName()) {
                    itemName = miniMessage.serialize(active.getItem().getItemMeta().displayName());
                } else {
                    itemName = active.getItem().getType().toString().replace("_", " ");
                }
            }

            newName.append("<red><bold>FIRESALE ACTIVE!</bold>%nl%")
                    .append("<white>Item: <aqua>").append(itemName).append("%nl%")
                    .append("<white>Price: <gold>").append((int)active.getPrice()).append(" Credits</gold>%nl%")
                    .append("<yellow><underlined>Click to View!</underlined>");
        }
        else {
            // Cooldown / Ended
            newName.append("<gray><bold>Firesale Ended</bold>%nl%")
                    .append("<dark_gray>Restocking items...");
        }

        String newText = newName.toString();

        // 3. Change Check
        if (newText.equals(lastText)) {
            return; // Text hasn't changed, do nothing
        }

        // 4. Refresh Hologram
        // CLEANUP FIRST: Passing 'true' for aggressive cleanup
        removeOldDisplays(baseSpawnLoc.getWorld(), baseSpawnLoc, true);
        spawnNewDisplays(newText, baseSpawnLoc);

        lastText = newText;
    }

    private void spawnNewDisplays(String text, Location baseLoc) {
        String[] lines = text.split("%nl%");

        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;

            final String lineText = lines[i];
            Location lineLoc = baseLoc.clone().add(0, (lines.length - 1 - i) * HOLOGRAM_LINE_HEIGHT, 0);

            try {
                TextDisplay td = baseLoc.getWorld().spawn(lineLoc, TextDisplay.class, (e) -> {
                    e.setGravity(false);
                    e.setInvulnerable(true);
                    e.text(miniMessage.deserialize(lineText));
                    e.setAlignment(TextDisplay.TextAlignment.CENTER);
                    e.setBillboard(Display.Billboard.CENTER);
                    e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
                    e.setSeeThrough(true);
                    e.setShadowed(true);
                    // Add tag for cleanup
                    e.getPersistentDataContainer().set(HOLOGRAM_TAG_KEY, PersistentDataType.BYTE, (byte) 1);
                });

                hologramLines.add(td);
            } catch (Exception e) {
                plugin.getLogger().warning("[Firesale] Failed to spawn hologram line: " + e.getMessage());
            }
        }
    }

    /**
     * Removes old displays.
     * @param aggressive If true, kills ANY TextDisplay near the location (fixes crash loop)
     */
    private void removeOldDisplays(World world, Location searchCenter, boolean aggressive) {
        // 1. Remove from in-memory list
        for (TextDisplay td : hologramLines) {
            if (td != null && !td.isDead()) {
                td.remove();
            }
        }
        hologramLines.clear();

        // 2. Find and remove orphans in the world
        if (world == null || searchCenter == null) return;

        try {
            // Search 10 blocks around
            for (Entity entity : world.getNearbyEntities(searchCenter, 5, 5, 5)) {
                if (entity instanceof TextDisplay) {
                    boolean shouldRemove = false;

                    // Check 1: Is it tagged? (Normal behavior)
                    PersistentDataContainer pdc = entity.getPersistentDataContainer();
                    if (pdc.has(HOLOGRAM_TAG_KEY, PersistentDataType.BYTE)) {
                        shouldRemove = true;
                    }

                    // Check 2: Aggressive mode (Nuclear Option)
                    // Kills ANY text display extremely close to the hologram spawn point
                    // This deletes the "ghosts" causing your crash
                    if (aggressive && !shouldRemove) {
                        double distance = entity.getLocation().distance(searchCenter);
                        // If it's within 2 blocks of where we want to be, it's probably a ghost
                        if (distance < 2.0) {
                            shouldRemove = true;
                        }
                    }

                    if (shouldRemove && !entity.isDead()) {
                        entity.remove();
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[Firesale] Error while removing old hologram displays: " + e.getMessage());
        }
    }

    public void fullReset() {
        Location loc = (npc != null) ? npc.getStoredLocation() : null;
        if (loc != null && loc.getWorld() != null) {
            // Aggressive cleanup on reset too
            removeOldDisplays(loc.getWorld(), loc.clone().add(0, 4.0, 0), true);
        } else {
            hologramLines.forEach(Entity::remove);
            hologramLines.clear();
        }
        lastText = "";
    }

    public void cleanup() {
        fullReset();
    }
}