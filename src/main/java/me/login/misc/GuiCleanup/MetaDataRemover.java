package me.login.misc.GuiCleanup;

import me.login.Login;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MetaDataRemover implements Listener {

    private final Login plugin;

    public MetaDataRemover(Login plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        clearAllGUIFlags(player);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        clearAllGUIFlags(event.getPlayer());
    }

    /**
     * Removes all metadata flags used by your plugin's GUIs.
     * Add each GUI metadata key here.
     */
    private void clearAllGUIFlags(Player player) {
        // ✅ Add every GUI metadata name used in your plugin here
        String[] guiMetadataKeys = {
                "DailyRewardGUI",
                "PlaytimeRewardsGUI",
                "CoinflipGUI",
                "TokenShopGUI",
                "ReviveMenu",
                "CoinflipAdminMenu",
                "CoinflipManageMenu",
                "CoinflipMainMenu",
        };

        for (String key : guiMetadataKeys) {
            if (player.hasMetadata(key)) {
                player.removeMetadata(key, plugin);
                plugin.getLogger().info("[GuiCleanup] Removed metadata '" + key + "' for " + player.getName());
            }
        }
    }
}
