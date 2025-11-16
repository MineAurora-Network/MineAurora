package me.login.lifesteal;

import me.login.Login;
import net.kyori.adventure.text.Component; // <-- IMPORT ADDED
import net.kyori.adventure.text.format.NamedTextColor; // <-- IMPORT ADDED
import org.bukkit.Bukkit; // <-- IMPORT ADDED
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.World; // <-- IMPORT ADDED
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
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
    private LifestealLogger logger; // <-- ADDED FIELD

    // --- CONSTRUCTOR UPDATED ---
    public LifestealListener(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, DeadPlayerManager deadPlayerManager, ReviveMenu reviveMenu, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.deadPlayerManager = deadPlayerManager;
        this.reviveMenu = reviveMenu;
        this.logger = logger; // <-- STORE LOGGER
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lifestealManager.loadPlayerData(player);
        if (deadPlayerManager.isDead(player.getUniqueId())) {
            final Component kickMessage = itemManager.formatMessage("<red>You are dead! A player must revive you using a Revive Beacon.");
            Bukkit.getScheduler().runTask(plugin, () -> {
                player.kick(kickMessage);
            });
        } else {
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        int victimHearts = lifestealManager.getHearts(victim.getUniqueId());

        if (victimHearts > lifestealManager.getMinHearts()) {
            lifestealManager.removeHearts(victim.getUniqueId(), 1);
            victim.sendMessage(itemManager.formatMessage("<red>You lost a heart!"));
        } else {
            // --- MODIFIED (Request 3) ---
            deadPlayerManager.addDeadPlayer(victim.getUniqueId(), victim.getName());

            final Component kickMessage = itemManager.formatMessage("<dark_red>You have lost your final life. You are now dead.</dark_red>");

            // --- BROADCAST (Request 3) ---
            Component broadcastMessage = Component.text(victim.getName(), NamedTextColor.RED)
                    .append(Component.text(" has been banned for running out of hearts.", NamedTextColor.GRAY));

            for (String worldName : lifestealManager.getLifestealWorlds()) {
                World world = Bukkit.getWorld(worldName);
                if (world != null) {
                    for (Player playerInWorld : world.getPlayers()) {
                        // Use the ItemManager to format the broadcast with the server prefix
                        playerInWorld.sendMessage(itemManager.formatMessage(broadcastMessage));
                    }
                }
            }
            // --- END BROADCAST ---

            // Kick player on the next tick to ensure death event processing is complete
            Bukkit.getScheduler().runTask(plugin, () -> {
                victim.kick(kickMessage);
            });
            // --- END MODIFICATION ---

            // --- LOGGING UPDATED ---
            if (logger != null) {
                String killerName = (killer != null) ? killer.getName() : "Unknown Causes";
                logger.logNormal("Player `" + victim.getName() + "` lost their final life (killed by `" + killerName + "`) and was kicked.");
            }
        }

        if (killer != null && !killer.equals(victim)) {
            int killerHearts = lifestealManager.getHearts(killer.getUniqueId());
            if (killerHearts < lifestealManager.getMaxHearts()) {
                lifestealManager.addHearts(killer.getUniqueId(), 1);
                killer.sendMessage(itemManager.formatMessage("<green>You stole a heart from " + victim.getName() + "!"));
            } else {
                killer.sendMessage(itemManager.formatMessage("<yellow>" + victim.getName() + " had no heart to steal (you are at max)."));
            }
        }
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

    // --- REMOVED onBeaconPlace ---

    // --- ADDED (Request 2 & 3) ---
    @EventHandler
    public void onBeaconUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.beaconItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);

            // Prevent opening menu if right-clicking an interactable block (like a chest)
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

            // Consume the beacon
            item.setAmount(item.getAmount() - 1);
        }
    }
    // --- END ADDITION ---
}