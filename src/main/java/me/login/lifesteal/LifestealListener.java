package me.login.lifesteal;

import me.login.Login;
import org.bukkit.GameMode;
import org.bukkit.Material;
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

    // --- (onPlayerJoin, onWorldChange, onPlayerQuit... remain the same) ---

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lifestealManager.loadPlayerData(player);
        if (deadPlayerManager.isDead(player.getUniqueId())) {
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(itemManager.formatMessage("<red>You are dead! A player must revive you using a Revive Beacon."));
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
            deadPlayerManager.addDeadPlayer(victim.getUniqueId(), victim.getName());
            victim.setGameMode(GameMode.SPECTATOR);
            victim.sendMessage(itemManager.formatMessage("<dark_red>You have lost your final life. You are now dead.</dark_red>"));

            // --- LOGGING UPDATED ---
            if (logger != null) {
                String killerName = (killer != null) ? killer.getName() : "Unknown Causes";
                logger.logNormal("Player `" + victim.getName() + "` lost their final life (killed by `" + killerName + "`) and is now dead.");
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

    // --- (onHeartUse, onBeaconPlace... remain the same) ---

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
    public void onBeaconPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.beaconItemKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);

            if (!player.hasPermission("lifesteal.revive")) {
                player.sendMessage(itemManager.formatMessage("<red>You do not have permission to use this."));
                return;
            }

            reviveMenu.openMenu(player, 0, null);
        }
    }
}