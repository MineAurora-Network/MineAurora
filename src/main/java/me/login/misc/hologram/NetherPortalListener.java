package me.login.misc.hologram;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.login.misc.rtp.RTPCommand;
import me.login.misc.rtp.RTPModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

/**
 * Listener to handle player portal events, specifically for intercepting
 * nether portal usage in the "lifesteal" WorldGuard region.
 */
public class NetherPortalListener implements Listener {

    private final RTPModule rtpModule;
    private final RTPHologramInteraction rtpInteraction;
    private static final String REGION_ID = "lifesteal";

    public NetherPortalListener(RTPModule rtpModule, RTPHologramInteraction rtpInteraction) {
        this.rtpModule = rtpModule;
        this.rtpInteraction = rtpInteraction;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerPortal(PlayerPortalEvent event) {
        // Only handle nether portals
        if (event.getCause() != PlayerPortalEvent.TeleportCause.NETHER_PORTAL) {
            return;
        }

        Player player = event.getPlayer();

        // Check if WorldGuard integration is active
        if (!rtpModule.isWorldGuardEnabled()) {
            return;
        }

        // Check if the player is in the "lifesteal" region
        if (!isInLifestealRegion(player)) {
            return;
        }

        // --- Player is in the "lifesteal" region and used a nether portal ---

        // 1. Cancel the default portal event
        event.setCancelled(true);

        // 2. Get the player's selected RTP world from the hologram interaction manager
        int selectedPage = rtpInteraction.getPlayerPage(player);
        String worldAlias = rtpInteraction.getWorldAliasForPage(selectedPage);

        // 3. Get the world name from config
        String worldName = rtpModule.getPlugin().getConfig().getString("worlds." + worldAlias, "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <red>Could not find world '" + worldAlias + "' to teleport to."));
            rtpModule.getLogger().log("NetherPortal RTP Error: World '" + worldAlias + "' (config: " + worldName + ") is null.");
            return;
        }

        // 4. Send feedback message
        player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <green>Teleporting you to your selected world: <gold>" + worldAlias + "</gold>..."));

        // 5. Start the RTP teleport (this static method handles cooldowns and further messages)
        RTPCommand.startTeleport(player, world, worldAlias, rtpModule);
    }

    /**
     * Checks if a player is currently inside a WorldGuard region with the ID "lifesteal".
     *
     * @param player The player to check.
     * @return true if the player is in the "lifesteal" region, false otherwise.
     */
    private boolean isInLifestealRegion(Player player) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

            if (regions == null) {
                return false; // No region manager for this world
            }

            BlockVector3 locVector = BlockVector3.at(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
            ApplicableRegionSet set = regions.getApplicableRegions(locVector);

            // Check if any of the applicable regions match the ID
            for (ProtectedRegion region : set.getRegions()) {
                if (region.getId().equalsIgnoreCase(REGION_ID)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            rtpModule.getLogger().log("Error checking WorldGuard region for 'lifesteal': " + e.getMessage());
            return false; // Assume not in region on error
        }
    }
}