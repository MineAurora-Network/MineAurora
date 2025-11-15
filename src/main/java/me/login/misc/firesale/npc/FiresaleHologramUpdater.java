package me.login.misc.firesale.npc;

import me.login.Login;
import me.login.misc.firesale.FiresaleManager;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.TextDisplay;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public class FiresaleHologramUpdater extends BukkitRunnable {

    private final FiresaleManager firesaleManager;
    @SuppressWarnings("unused")
    private final Login plugin;
    private final NPC npc;
    private final MiniMessage miniMessage; // Added for component parsing
    private String lastText = "";
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    // List to manage hologram lines
    private final List<TextDisplay> hologramLines = new ArrayList<>();
    private static final double HOLOGRAM_LINE_HEIGHT = 0.27;
    private static final double HOLOGRAM_BASE_Y_OFFSET = 2.3; // Initial height above NPC

    public FiresaleHologramUpdater(Login plugin, FiresaleManager firesaleManager, NPC npc, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.firesaleManager = firesaleManager;
        this.npc = npc;
        this.miniMessage = miniMessage; // Store MiniMessage instance
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) {
            cleanup(); // NPC is gone, remove holograms
            return;
        }

        List<Firesale> sales = firesaleManager.getActiveSales();
        Firesale active = sales.isEmpty() ? null : sales.get(0);

        // Use %nl% for newlines as requested
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

        // Only update if the text has changed
        if (newText.equals(lastText)) {
            return;
        }

        lastText = newText;

        // Clear old hologram lines
        cleanup();

        // Get base location above NPC
        Location baseLoc = npc.getStoredLocation().clone().add(0, HOLOGRAM_BASE_Y_OFFSET, 0);
        String[] lines = newText.split("%nl%");

        // Iterate backwards so the first line (index 0) is at the top
        for (int i = lines.length - 1; i >= 0; i--) {
            if (lines[i].isEmpty()) continue;

            // --- FIX: Create a final variable for the lambda ---
            final String lineText = lines[i];

            // Calculate height for this line
            Location lineLoc = baseLoc.clone().add(0, (lines.length - 1 - i) * HOLOGRAM_LINE_HEIGHT, 0);

            // Spawn the TextDisplay
            TextDisplay td = baseLoc.getWorld().spawn(lineLoc, TextDisplay.class, (e) -> {
                e.setGravity(false);
                e.setInvulnerable(true);
                // --- FIX: Use the final variable ---
                e.text(miniMessage.deserialize(lineText));
                e.setAlignment(TextDisplay.TextAlignment.CENTER);
                e.setBillboard(Display.Billboard.CENTER); // Always face player
                e.setBackgroundColor(Color.fromARGB(0, 0, 0, 0)); // Transparent background
                e.setSeeThrough(true);
                e.setShadowed(true);
            });

            hologramLines.add(td);
        }
    }
    public void cleanup() {
        if (hologramLines.isEmpty()) return;

        for (TextDisplay td : hologramLines) {
            if (td != null && !td.isDead()) {
                td.remove();
            }
        }
        hologramLines.clear();
        lastText = ""; // Force update next cycle
    }
}