package me.login.misc.hologram;

import me.login.misc.rtp.RTPCommand;
import me.login.misc.rtp.RTPModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class RTPHologramInteraction {

    private final HologramModule hologramModule;
    private final RTPModule rtpModule;
    // Stores which page (1=Overworld, 2=Nether, 3=End) each player has selected
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public RTPHologramInteraction(HologramModule hologramModule, RTPModule rtpModule) {
        this.hologramModule = hologramModule;
        this.rtpModule = rtpModule;
    }

    public void handleClick(Player player, Hologram hologram, UUID interactionUUID) {
        int lineIndex = hologram.getLineIndexByInteraction(interactionUUID);
        if (lineIndex == -1) return;

        // Get the player's currently selected page, defaulting to 1 (Overworld)
        int currentPage = getPlayerPage(player);

        // Get the text lines for that page (to check what was clicked)
        List<String> lines = hologramModule.getHologramManager().getHologramLines(hologram.getName(), currentPage);

        if (lines.isEmpty() || lineIndex >= lines.size()) {
            // Config is broken or mismatched, reset player to page 1
            currentPage = 1;
            playerPages.put(player.getUniqueId(), 1);
            lines = hologramModule.getHologramManager().getHologramLines(hologram.getName(), currentPage);

            // If it's still broken, just stop
            if (lines.isEmpty() || lineIndex >= lines.size()) return;
        }

        String clickedLine = lines.get(lineIndex);

        if (clickedLine.contains("Click to teleport")) {
            // --- SELECTION CONFIRMATION (NO TELEPORT) ---

            // Get the world alias (overworld, nether, end) for the player's page
            String worldAlias = getWorldAliasForPage(currentPage);

            // Send feedback messages
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <green>RTP world selected: <gold>" + worldAlias));
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <gray>Enter a Nether Portal in the Lifesteal region to teleport."));

            // --- TELEPORT LOGIC REMOVED ---

        } else if (clickedLine.contains("Current Page")) {
            // --- Page Switch Logic ---
            int maxPages = 3; // Overworld, Nether, End
            int newPage = (currentPage % maxPages) + 1; // Cycles 1 -> 2 -> 3 -> 1
            playerPages.put(player.getUniqueId(), newPage);

            // Send a chat message as feedback, since we can't update the hologram text per-player
            String newPageName = getWorldAliasForPage(newPage);
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <gray>Switched RTP target to: <yellow>" + newPageName));
        }
    }

    /**
     * Clears the stored page for a player when they quit.
     *
     * @param player The player who is quitting.
     */
    public void clearPlayerPage(Player player) {
        playerPages.remove(player.getUniqueId());
    }

    /**
     * Gets the player's currently selected page, defaulting to 1 (Overworld).
     *
     * @param player The player
     * @return The page number (1, 2, or 3)
     */
    public int getPlayerPage(Player player) {
        return playerPages.getOrDefault(player.getUniqueId(), 1);
    }

    /**
     * Gets the world alias (overworld, nether, end) for a given page number.
     *
     * @param page The page number (1, 2, or 3)
     * @return The corresponding world alias as a string.
     */
    public String getWorldAliasForPage(int page) {
        return switch (page) {
            case 2 -> "nether";
            case 3 -> "end";
            default -> "overworld"; // Case 1 and default
        };
    }
}