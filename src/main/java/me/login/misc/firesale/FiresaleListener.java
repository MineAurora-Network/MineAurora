package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.gui.FiresaleGUI;
import me.login.misc.firesale.model.Firesale;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class FiresaleListener implements Listener {

    private final Login plugin;
    private final FiresaleManager manager;
    private final FiresaleGUI gui;
    private final int npcId;

    public static final String METADATA_KEY = "OPEN_FIRESALE_MENU";

    public FiresaleListener(Login plugin, FiresaleManager manager, FiresaleGUI gui, int npcId) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.npcId = npcId;
    }

    @EventHandler
    public void onNPCInteract(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            gui.openMainMenu(event.getClicker());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Delay to allow Skript variables and world to load
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.checkPlayerJoin(event.getPlayer());
        }, 60L); // 3-second delay
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.checkPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(METADATA_KEY)) {
            return;
        }

        // Player is in a firesale GUI
        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir()) {
            return;
        }

        String menuType = player.getMetadata(METADATA_KEY).get(0).asString();
        PersistentDataContainer pdc = clickedItem.getItemMeta().getPersistentDataContainer();

        if (pdc.has(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING)) {
            String action = pdc.get(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING);

            switch (action) {
                case "open_sales_menu":
                    gui.openActiveSalesMenu(player);
                    break;
                case "open_history_menu":
                    gui.openHistoryMenu(player, 0); // Open first page
                    break;
                case "buy_sale_item":
                    int saleId = pdc.get(FiresaleGUI.NBT_SALE_ID_KEY, PersistentDataType.INTEGER);
                    manager.getSaleById(saleId).ifPresent(sale -> {
                        manager.attemptPurchase(player, sale);
                        // Refresh the GUI after purchase attempt
                        gui.openActiveSalesMenu(player);
                    });
                    break;
                case "history_next_page":
                    int nextPage = pdc.get(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER);
                    gui.openHistoryMenu(player, nextPage);
                    break;
                case "history_prev_page":
                    int prevPage = pdc.get(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER);
                    gui.openHistoryMenu(player, prevPage);
                    break;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        // Correctly remove the metadata to prevent bugs
        if (player.hasMetadata(METADATA_KEY)) {
            player.removeMetadata(METADATA_KEY, plugin);
        }
    }
}