package me.login.moderation.commands;

import me.login.Login;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashSet;
import java.util.Set;

public class WorldManager {

    private final Login plugin;
    private final Set<String> lockedWorlds = new HashSet<>();
    private Location hubSpawn;

    public WorldManager(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Helper to send a prefixed message
     */
    private void sendPrefixedMessage(Player player, String message) {
        player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + message));
    }

    /**
     * Locks a world, preventing non-bypass players from entering.
     * @param worldName The name of the world to lock.
     * @return True if the world was successfully locked, false if it doesn't exist.
     */
    public boolean lockWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            return false;
        }

        String name = world.getName().toLowerCase();
        if (lockedWorlds.add(name)) {
            // World was not already locked. Now move players out.
            Location hub = getHubSpawn();
            if (hub == null) {
                plugin.getLogger().warning("World '" + name + "' locked, but 'hub' world or spawn not found! Cannot move players.");
                return true;
            }

            for (Player player : world.getPlayers()) {
                if (!player.hasPermission("login.admin.worldmanager.bypass")) {
                    player.teleport(hub);
                    sendPrefixedMessage(player, "<red>This world has been locked by an administrator.");
                }
            }
        }
        return true;
    }

    /**
     * Unlocks a world.
     * @param worldName The name of the world to unlock.
     * @return True if the world was unlocked, false if it wasn't locked.
     */
    public boolean unlockWorld(String worldName) {
        return lockedWorlds.remove(worldName.toLowerCase());
    }

    /**
     * Checks if a player is allowed to enter a world.
     * @param event The PlayerTeleportEvent.
     */
    public void checkTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }

        World toWorld = to.getWorld();
        // Don't check if the world isn't locked
        if (!isWorldLocked(toWorld.getName())) {
            return;
        }

        // Don't check if the player has bypass permission
        Player player = event.getPlayer();
        if (player.hasPermission("login.admin.worldmanager.bypass")) {
            return;
        }

        // Player is not allowed
        event.setCancelled(true);
        sendPrefixedMessage(player, "<red>You cannot enter " + toWorld.getName() + " as it is currently locked.");

        // Send them to hub
        Location hub = getHubSpawn();
        if (hub != null && !player.getWorld().equals(hub.getWorld())) {
            player.teleport(hub);
        } else {
            // As a fallback, just cancel the teleport
            event.setCancelled(true);
        }
    }

    public boolean isWorldLocked(String worldName) {
        return lockedWorlds.contains(worldName.toLowerCase());
    }

    /**
     * Safely gets and caches the hub spawn location.
     * @return The hub spawn location, or null if not found.
     */
    private Location getHubSpawn() {
        if (hubSpawn == null) {
            World hubWorld = Bukkit.getWorld("hub");
            if (hubWorld != null) {
                hubSpawn = hubWorld.getSpawnLocation();
            } else {
                plugin.getLogger().severe("Cannot find 'hub' world! WorldManager redirects will fail.");
                return null;
            }
        }
        return hubSpawn;
    }

    /**
     * Getter for the plugin instance, used by WorldManagerCommand.
     * @return The Login plugin instance.
     */
    public Login getPlugin() {
        return plugin;
    }
}