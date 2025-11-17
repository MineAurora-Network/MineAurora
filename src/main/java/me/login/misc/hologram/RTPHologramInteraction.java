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
    private final Map<UUID, Integer> playerPages = new HashMap<>();

    public RTPHologramInteraction(HologramModule hologramModule, RTPModule rtpModule) {
        this.hologramModule = hologramModule;
        this.rtpModule = rtpModule;
    }

    public void handleClick(Player player, Hologram hologram, UUID interactionUUID) {
        int lineIndex = hologram.getLineIndexByInteraction(interactionUUID);
        if (lineIndex == -1) return;

        int currentPage = playerPages.getOrDefault(player.getUniqueId(), 1);
        List<String> lines = hologramModule.getHologramManager().getHologramLines(hologram.getName(), currentPage);
        if (lines.isEmpty() || lineIndex >= lines.size()) {
            // Page data is missing or mismatched, reset to page 1
            currentPage = 1;
            playerPages.put(player.getUniqueId(), 1);
            lines = hologramModule.getHologramManager().getHologramLines(hologram.getName(), currentPage);
            // We can't update the text per-player, so just continue
            if (lines.isEmpty() || lineIndex >= lines.size()) return; // Config is broken
        }

        String clickedLine = lines.get(lineIndex);

        if (clickedLine.contains("Click to teleport")) {
            // Teleport logic
            String worldAlias = switch (currentPage) {
                case 1 -> "overworld";
                case 2 -> "nether";
                case 3 -> "end";
                default -> "overworld";
            };

            World world = Bukkit.getWorld(rtpModule.getPlugin().getConfig().getString("worlds." + worldAlias, "world"));
            if (world == null) {
                player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <red>World '" + worldAlias + "' not found!"));
                return;
            }

            // Use the static teleport method from RTPCommand
            RTPCommand.startTeleport(player, world, worldAlias, rtpModule);

        } else if (clickedLine.contains("Current Page")) {
            // Page switch logic
            int maxPages = 3; // TODO: Make this dynamic from config if needed
            int newPage = (currentPage % maxPages) + 1;
            playerPages.put(player.getUniqueId(), newPage);

            //
            // Removed call to updatePlayerHologramPage as it's not supported.
            //
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <gray>Switched to page " + newPage));
        }
    }

    /**
     * Clears the stored page for a player when they quit.
     * @param player The player who is quitting.
     */
    public void clearPlayerPage(Player player) {
        playerPages.remove(player.getUniqueId());
    }
}