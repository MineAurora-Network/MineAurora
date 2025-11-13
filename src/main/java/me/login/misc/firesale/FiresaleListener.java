package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.gui.FiresaleGUI;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
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
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.checkPlayerJoin(event.getPlayer());
        }, 60L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.checkPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().hasMetadata(METADATA_KEY)) {
            event.getPlayer().removeMetadata(METADATA_KEY, plugin);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!player.hasMetadata(METADATA_KEY)) {
            return;
        }

        // Safety Check: Ensure the player is actually looking at a Firesale menu (Size 36).
        // If they somehow have metadata but are in a different inventory (like a large chest), remove metadata and un-stick them.
        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getSize() != 36 || top.getType() != InventoryType.CHEST) {
            player.removeMetadata(METADATA_KEY, plugin);
            return;
        }

        event.setCancelled(true); // Cancel by default in this menu

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getItemMeta() == null) {
            return;
        }

        PersistentDataContainer pdc = clickedItem.getItemMeta().getPersistentDataContainer();
        if (pdc.has(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING)) {
            String action = pdc.get(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "open_sales_menu":
                    gui.openActiveSalesMenu(player);
                    break;
                case "open_history_menu":
                    gui.openHistoryMenu(player, 0);
                    break;
                case "buy_sale_item":
                    if (pdc.has(FiresaleGUI.NBT_SALE_ID_KEY, PersistentDataType.INTEGER)) {
                        int saleId = pdc.get(FiresaleGUI.NBT_SALE_ID_KEY, PersistentDataType.INTEGER);
                        manager.getSaleById(saleId).ifPresent(sale -> {
                            manager.attemptPurchase(player, sale);
                        });
                    }
                    break;
                case "history_next_page":
                    int nextPage = pdc.getOrDefault(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER, 0);
                    gui.openHistoryMenu(player, nextPage);
                    break;
                case "history_prev_page":
                    int prevPage = pdc.getOrDefault(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER, 0);
                    gui.openHistoryMenu(player, prevPage);
                    break;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (!player.hasMetadata(METADATA_KEY)) return;

        // When switching menus (e.g. Main -> Active Sales), the old inventory closes
        // and the new one opens in the same tick or immediately after.
        // If we remove metadata immediately here, the next menu click will be ignored.
        // Instead, check 1 tick later if the player is still viewing a Chest inventory (the new menu).

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            Inventory top = player.getOpenInventory().getTopInventory();

            // If the player closed the menu completely, the top inventory will usually be
            // type CRAFTING (their 2x2 grid) or null depending on version.
            // If they are still in our menu loop, it should be a CHEST inventory.
            if (top == null || top.getType() != InventoryType.CHEST) {
                player.removeMetadata(METADATA_KEY, plugin);
            }
        });
    }
}