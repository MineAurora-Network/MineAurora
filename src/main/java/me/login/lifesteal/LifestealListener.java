package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority; // <-- IMPORT ADDED
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
// import org.bukkit.event.block.BlockPlaceEvent; // <-- This import is unused
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class LifestealListener implements Listener {

    private final Login plugin;
    private final ItemManager itemManager;
    private final LifestealManager lifestealManager;
    private final DeadPlayerManager deadPlayerManager;
    private final ReviveMenu reviveMenu;
    private LifestealLogger logger;

    public LifestealListener(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, DeadPlayerManager deadPlayerManager, ReviveMenu reviveMenu, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.deadPlayerManager = deadPlayerManager;
        this.reviveMenu = reviveMenu;
        this.logger = logger;
    }

    // --- FIX: Added EventPriority.HIGH ---
    // This forces this event to run *before* other plugins (like LoginSystem)
    // allowing us to fix the player's gamemode first.
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lifestealManager.loadPlayerData(player);
        if (deadPlayerManager.isDead(player.getUniqueId())) {
            final Component kickMessage = itemManager.formatMessage("<red>You are dead! A player must revive you using a Revive Beacon.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kick(kickMessage);
            });
        } else {
            // --- FIX: For revived players bypassing login ---
            // If a player was revived offline, they might join as a spectator.
            // This forces them back to SURVIVAL so the LoginSystem can see them.
            if (player.getGameMode() == GameMode.SPECTATOR) {
                player.setGameMode(GameMode.SURVIVAL);
            }
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        lifestealManager.updatePlayerHealth(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        lifestealManager.savePlayerData(event.getPlayer());
    }

    // --- FIX: Logic completely restructured ---
    // Now, heart loss and banning ONLY happen if there is a player killer.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // --- MAIN FIX ---
        // Only run Lifesteal logic if the death was a true PvP kill.
        if (killer != null && !killer.equals(victim)) {

            // --- Victim's Logic ---
            int victimHearts = lifestealManager.getHearts(victim.getUniqueId());

            if (victimHearts > lifestealManager.getMinHearts()) {
                // Victim loses a heart
                lifestealManager.removeHearts(victim.getUniqueId(), 1);
                victim.sendMessage(itemManager.formatMessage("<red>You lost a heart!"));
            } else {
                // Victim loses their final life and is banned
                deadPlayerManager.addDeadPlayer(victim.getUniqueId(), victim.getName());

                final Component kickMessage = itemManager.formatMessage("<dark_red>You have lost your final life. You are now dead.</dark_red>");

                Component broadcastMessage = Component.text(victim.getName(), NamedTextColor.RED)
                        .append(Component.text(" has been banned for running out of hearts.", NamedTextColor.GRAY));

                for (String worldName : lifestealManager.getLifestealWorlds()) {
                    World world = Bukkit.getWorld(worldName);
                    if (world != null) {
                        for (Player playerInWorld : world.getPlayers()) {
                            playerInWorld.sendMessage(itemManager.formatMessage(broadcastMessage));
                        }
                    }
                }

                // Kick player on the next tick
                Bukkit.getScheduler().runTask(plugin, () -> {
                    victim.kick(kickMessage);
                });

                if (logger != null) {
                    logger.logNormal("Player `" + victim.getName() + "` lost their final life (killed by `" + killer.getName() + "`) and was kicked.");
                }
            }

            // --- Killer's Logic ---
            int killerHearts = lifestealManager.getHearts(killer.getUniqueId());
            if (killerHearts < lifestealManager.getMaxHearts()) {
                lifestealManager.addHearts(killer.getUniqueId(), 1);
                killer.sendMessage(itemManager.formatMessage("<green>You stole a heart from " + victim.getName() + "!"));
            } else {
                killer.sendMessage(itemManager.formatMessage("<yellow>" + victim.getName() + " had no heart to steal (you are at max)."));
            }
        }
        // If killer is null (e.g., fall damage, lava, mobs), nothing happens.
        // The player respawns normally with no heart loss.
    }

    @EventHandler
    public void onHeartUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.heartItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            if (lifestealManager.useHeart(player)) {
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    @EventHandler
    public void onBeaconUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.beaconItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);

            if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
                if (event.getClickedBlock().getType().isInteractable()) {
                    return;
                }
            }

            if (!player.hasPermission("lifesteal.revive")) {
                player.sendMessage(itemManager.formatMessage("<red>You do not have permission to use this."));
                return;
            }

            reviveMenu.openMenu(player, 0, null);

            item.setAmount(item.getAmount() - 1);
        }
    }
}