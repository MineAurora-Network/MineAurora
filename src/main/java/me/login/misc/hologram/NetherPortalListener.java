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
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerPortalEvent;

public class NetherPortalListener implements Listener {

    private final RTPModule rtpModule;
    private static final String REGION_ID = "lifesteal";

    public NetherPortalListener(RTPModule rtpModule) {
        this.rtpModule = rtpModule;
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

        // 2. Set Target: Normal World (Overworld)
        String worldAlias = "overworld";
        String worldName = rtpModule.getPlugin().getConfig().getString("worlds.overworld", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <red>Could not find world '" + worldAlias + "' to teleport to."));
            rtpModule.getLogger().log("NetherPortal RTP Error: World '" + worldAlias + "' (config: " + worldName + ") is null.");
            return;
        }

        // 3. Send feedback message
        player.sendMessage(MiniMessage.miniMessage().deserialize(rtpModule.getServerPrefix() + " <green>Teleporting to the wilderness..."));

        // 4. Start the RTP teleport
        RTPCommand.startTeleport(player, world, worldAlias, rtpModule);
    }

    private boolean isInLifestealRegion(Player player) {
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            RegionManager regions = container.get(BukkitAdapter.adapt(player.getWorld()));

            if (regions == null) return false;

            BlockVector3 locVector = BlockVector3.at(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ());
            ApplicableRegionSet set = regions.getApplicableRegions(locVector);

            for (ProtectedRegion region : set.getRegions()) {
                if (region.getId().equalsIgnoreCase(REGION_ID)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}