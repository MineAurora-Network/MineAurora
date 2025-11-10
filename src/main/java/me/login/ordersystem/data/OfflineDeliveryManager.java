package me.login.ordersystem.data;

import me.login.Login;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import ---
import me.login.ordersystem.util.OrderMessageHandler;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/**
 * Handles refunding items and money to players who were offline
 * when their order was cancelled by an admin.
 * (Points 4, 5)
 */
public class OfflineDeliveryManager implements Listener {

    private final Login plugin;
    private final OrdersDatabase database;
    private final OrderMessageHandler messageHandler;
    private final String requiredWorld;

    public OfflineDeliveryManager(Login plugin, OrdersDatabase database, OrderMessageHandler messageHandler) {
        this.plugin = plugin;
        this.database = database;
        this.messageHandler = messageHandler;
        this.requiredWorld = plugin.getConfig().getString("lifesteal-world", "lifesteal");
    }

    /**
     * Stores items and a refund for a player who is offline.
     */
    public void scheduleDelivery(UUID playerUUID, double refundAmount, List<ItemStack> items) {
        database.saveOfflineDelivery(playerUUID, refundAmount, items)
                .exceptionally(ex -> {
                    plugin.getLogger().severe("Failed to save offline delivery for " + playerUUID + ": " + ex.getMessage());
                    return null;
                });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // Check if player is in the correct world (e.g., "lifesteal")
        if (!player.getWorld().getName().equalsIgnoreCase(requiredWorld)) {
            return;
        }

        // Check for and process deliveries on the main thread
        Bukkit.getScheduler().runTaskLater(plugin, () -> checkForDelivery(player), 20L * 3); // 3-second delay
    }

    private void checkForDelivery(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        UUID playerUUID = player.getUniqueId();
        // --- FIX: Changed whenCompleteAsync to whenComplete, removed bad executor ---
        database.loadAndRemoveOfflineDelivery(playerUUID).whenComplete((delivery, error) -> {
            if (error != null) {
                plugin.getLogger().severe("Error loading offline delivery for " + playerUUID + ": " + error.getMessage());
                return;
            }

            if (delivery == null) {
                return; // No delivery found
            }

            // Process delivery on the main thread
            Bukkit.getScheduler().runTask(plugin, () -> {
                // --- FIX: Type inference is now correct ---
                double refund = delivery.getRefundAmount();
                List<ItemStack> items = delivery.getItems();

                boolean itemsGiven = false;
                boolean refundGiven = false;

                // 1. Give Items
                if (items != null && !items.isEmpty()) {
                    HashMap<Integer, ItemStack> failed = player.getInventory().addItem(items.toArray(new ItemStack[0]));
                    if (!failed.isEmpty()) {
                        messageHandler.sendMessage(player, "<yellow>An admin cancelled one of your orders while you were offline.");
                        messageHandler.sendMessage(player, "<red>We tried to return your items, but your inventory is full! Dropping them at your feet.</red>");
                        failed.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    } else {
                        messageHandler.sendMessage(player, "<green>An admin cancelled one of your orders. Your partially filled items have been returned.</green>");
                    }
                    itemsGiven = true;
                }

                // 2. Give Refund
                if (refund > 0.01) {
                    var economy = OrderModule.getEconomy();
                    if (economy != null) {
                        economy.depositPlayer(player, refund);
                        messageHandler.sendMessage(player, "<green>An admin cancelled one of your orders. <gold>" + economy.format(refund) + "</gold> has been refunded to you.</green>");
                        refundGiven = true;
                    }
                }

                if (!itemsGiven && !refundGiven) {
                    messageHandler.sendMessage(player, "<gray>An admin cancelled one of your orders while you were offline (no refund/items were due).</gray>");
                }
            });

        });
    }
}