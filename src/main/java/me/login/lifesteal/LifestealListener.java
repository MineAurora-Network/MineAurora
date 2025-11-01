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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType; // <-- MISSING IMPORT ADDED

public class LifestealListener implements Listener {

    private final Login plugin;
    private final ItemManager itemManager;
    private final LifestealManager lifestealManager;
    private final DeadPlayerManager deadPlayerManager;
    private final ReviveMenu reviveMenu;

    public LifestealListener(Login plugin, ItemManager itemManager, LifestealManager lifestealManager, DeadPlayerManager deadPlayerManager, ReviveMenu reviveMenu) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.lifestealManager = lifestealManager;
        this.deadPlayerManager = deadPlayerManager;
        this.reviveMenu = reviveMenu;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lifestealManager.loadPlayerData(player);

        if (deadPlayerManager.isDead(player.getUniqueId())) {
            // Player is dead, needs reviving
            player.setGameMode(GameMode.SPECTATOR);
            player.sendMessage(itemManager.formatMessage("<red>You are dead! A player must revive you using a Revive Beacon."));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Save data on quit
        lifestealManager.savePlayerData(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller(); // This is null if not killed by a player

        int victimHearts = lifestealManager.getHearts(victim.getUniqueId());

        // Player loses a heart, unless they are at 1
        // --- FIX ---
        if (victimHearts > lifestealManager.getMinHearts()) {
            // --- END FIX ---
            lifestealManager.removeHearts(victim.getUniqueId(), 1);
            victim.sendMessage(itemManager.formatMessage("<red>You lost a heart!"));
        } else {
            // Player is at 1 heart and died. They are now "dead"
            deadPlayerManager.addDeadPlayer(victim.getUniqueId(), victim.getName());
            victim.setGameMode(GameMode.SPECTATOR);
            victim.sendMessage(itemManager.formatMessage("<dark_red>You have lost your final life. You are now dead.</dark_red>"));
            itemManager.sendLog(victim.getName() + " lost their final life and is now dead.");
        }

        // If killed by a player, killer gains a heart
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

        // Check if it's our custom heart item
        // --- FIX ---
        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.heartItemKey, PersistentDataType.BYTE)) {
            // --- END FIX ---
            event.setCancelled(true);
            if (lifestealManager.useHeart(player)) {
                // Success, remove one item
                item.setAmount(item.getAmount() - 1);
            }
        }
    }

    @EventHandler
    public void onBeaconPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();

        if (item.getType() == Material.AIR || !item.hasItemMeta()) return;

        // Check if it's our custom revive beacon
        // --- FIX ---
        if (item.getItemMeta().getPersistentDataContainer().has(itemManager.beaconItemKey, PersistentDataType.BYTE)) {
            // --- END FIX ---
            event.setCancelled(true); // Cancel placement

            if (!player.hasPermission("lifesteal.revive")) {
                player.sendMessage(itemManager.formatMessage("<red>You do not have permission to use this."));
                return;
            }

            // Open the Revive GUI
            reviveMenu.openMenu(player, 0, null); // Open first page, no search filter
        }
    }
}