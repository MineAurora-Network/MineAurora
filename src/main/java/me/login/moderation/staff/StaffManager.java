package me.login.moderation.staff;

import me.login.Login;
import me.login.moderation.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class StaffManager implements Listener {

    private final Login plugin;
    private final Set<UUID> notificationsDisabled = new HashSet<>();
    private final Set<UUID> vanishedPlayers = new HashSet<>();
    private BukkitTask vanishTask;

    // Maintenance
    private boolean globalMaintenance = false;
    private final Set<String> lockedWorlds = new HashSet<>();

    public StaffManager(Login plugin) {
        this.plugin = plugin;
    }

    // --- Start/Stop the Enforcer Task ---
    public void init() {
        // Runs every 20 ticks (1 second) to ensure vanished players STAY hidden
        // This fixes the issue where TabManager/ScoreboardManager might reveal them.
        this.vanishTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (UUID uuid : vanishedPlayers) {
                Player vanishedPlayer = Bukkit.getPlayer(uuid);
                if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                    refreshVisibilityFor(vanishedPlayer);
                }
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (vanishTask != null) {
            vanishTask.cancel();
        }
    }

    // --- Notifications ---
    public boolean isNotificationEnabled(UUID uuid) {
        return !notificationsDisabled.contains(uuid);
    }

    public void toggleNotifications(UUID uuid) {
        if (notificationsDisabled.contains(uuid)) {
            notificationsDisabled.remove(uuid);
        } else {
            notificationsDisabled.add(uuid);
        }
    }

    public void broadcastToStaff(Component message, String permission) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission) && isNotificationEnabled(p.getUniqueId())) {
                p.sendMessage(message);
            }
        }
    }

    // --- Vanish System ---

    public boolean isVanished(UUID uuid) {
        return vanishedPlayers.contains(uuid);
    }

    public void toggleVanish(Player player) {
        if (vanishedPlayers.contains(player.getUniqueId())) {
            // --- UNVANISH ---
            vanishedPlayers.remove(player.getUniqueId());
            player.removeMetadata("vanished", plugin);

            player.setSleepingIgnored(false);
            player.setCollidable(true);
            player.setGameMode(GameMode.SURVIVAL);

            // Show to everyone
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(player)) continue;
                other.showPlayer(plugin, player);
            }

            player.sendMessage(Component.text("You are now visible.", NamedTextColor.RED));

        } else {
            // --- VANISH ---
            vanishedPlayers.add(player.getUniqueId());
            player.setMetadata("vanished", new FixedMetadataValue(plugin, true));

            player.setAllowFlight(true);
            player.setFlying(true);
            player.setSleepingIgnored(true);
            player.setCollidable(false);
            player.setGameMode(GameMode.CREATIVE);

            // Force update visibility immediately
            refreshVisibilityFor(player);

            player.sendMessage(Component.text("You are now vanished.", NamedTextColor.GREEN));
        }
    }

    /**
     * Loops through all players and Hides/Shows the target based on permission.
     */
    private void refreshVisibilityFor(Player vanishedPlayer) {
        for (Player observer : Bukkit.getOnlinePlayers()) {
            if (observer.equals(vanishedPlayer)) continue;

            if (observer.hasPermission("staff.vanish.see")) {
                observer.showPlayer(plugin, vanishedPlayer); // Admin sees Ghost
            } else {
                observer.hidePlayer(plugin, vanishedPlayer); // Normal player sees Nothing
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player joiner = event.getPlayer();

        // RUN 1 TICK LATER to ensure compatibility with other plugins handling join
        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            // 1. Hide existing vanished players from the new joiner
            for (UUID uuid : vanishedPlayers) {
                Player vanishedPlayer = Bukkit.getPlayer(uuid);
                if (vanishedPlayer != null && vanishedPlayer.isOnline()) {
                    if (joiner.hasPermission("staff.vanish.see")) {
                        joiner.showPlayer(plugin, vanishedPlayer);
                    } else {
                        joiner.hidePlayer(plugin, vanishedPlayer);
                    }
                }
            }

            // 2. If the joiner is vanished (rejoin), hide them from others
            if (vanishedPlayers.contains(joiner.getUniqueId())) {
                joiner.setMetadata("vanished", new FixedMetadataValue(plugin, true));
                joiner.setGameMode(GameMode.CREATIVE);
                joiner.setAllowFlight(true);

                refreshVisibilityFor(joiner);
            }

        }, 1L); // 1 Tick Delay

        // Silence join message if vanished
        if (vanishedPlayers.contains(joiner.getUniqueId())) {
            event.joinMessage(null);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (vanishedPlayers.contains(event.getPlayer().getUniqueId())) {
            event.quitMessage(null);
        }
    }

    // --- Vanish Protections ---

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && isVanished(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetLivingEntityEvent event) {
        if (event.getTarget() instanceof Player && isVanished(event.getTarget().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player && isVanished(event.getDamager().getUniqueId())) {
            event.getDamager().sendMessage(Component.text("You cannot attack while vanished.", NamedTextColor.RED));
            event.setCancelled(true);
        }
        if (event.getEntity() instanceof Player && isVanished(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (isVanished(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You cannot break blocks while vanished.", NamedTextColor.RED));
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isVanished(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Component.text("You cannot place blocks while vanished.", NamedTextColor.RED));
        }
    }

    // --- World Maintenance System ---

    public void setGlobalMaintenance(boolean state) {
        this.globalMaintenance = state;
        if (state) {
            Component kickMsg = Utils.getServerPrefix(plugin).append(plugin.getComponentSerializer().deserialize("<red>\nServer is under maintenance."));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.hasPermission("maintenance.bypass")) {
                    p.kick(kickMsg);
                }
            }
        }
    }

    public boolean isGlobalMaintenance() { return globalMaintenance; }

    public void setWorldMaintenance(String worldName, boolean state) {
        String lowerName = worldName.toLowerCase();
        if (state) {
            lockedWorlds.add(lowerName);
            evacuateWorld(lowerName);
        } else {
            lockedWorlds.remove(lowerName);
        }
    }

    private void evacuateWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        Location hubLoc = null;
        World hubWorld = Bukkit.getWorld("hub");
        if (hubWorld != null) {
            hubLoc = new Location(hubWorld, 178.5, 118, -5.5, 90f, 0f);
        }

        Location loginLoc = null;
        World loginWorld = Bukkit.getWorld("login");
        if (loginWorld != null) {
            loginLoc = new Location(loginWorld, 0.5, 100, 0.5, 90f, 0f);
        }

        for (Player p : world.getPlayers()) {
            if (p.hasPermission("worldmaintenance.bypass")) continue;

            p.sendMessage(Utils.getServerPrefix(plugin).append(Component.text("This world is undergoing maintenance. Teleporting...", NamedTextColor.RED)));

            if (hubLoc != null) {
                p.teleport(hubLoc);
            } else if (loginLoc != null) {
                p.teleport(loginLoc);
            } else {
                p.sendMessage(Component.text("Error: Destination worlds (hub/login) not found.", NamedTextColor.RED));
            }
        }
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent event) {
        if (globalMaintenance && !event.getPlayer().hasPermission("maintenance.bypass")) {
            Component kickMsg = Utils.getServerPrefix(plugin).append(plugin.getComponentSerializer().deserialize("<red>\nServer is under maintenance."));
            event.disallow(PlayerLoginEvent.Result.KICK_OTHER, kickMsg);
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo().getWorld() == null) return;
        String toWorld = event.getTo().getWorld().getName().toLowerCase();

        if (lockedWorlds.contains(toWorld) && !event.getPlayer().hasPermission("worldmaintenance.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(Utils.getServerPrefix(plugin).append(Component.text("That world is under maintenance.", NamedTextColor.RED)));
        }
    }
}