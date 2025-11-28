package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TokenShopGUI implements Listener {

    private final Login plugin;
    private final TokenManager manager;
    private final MiniMessage mm;
    private static final String SHOP_METADATA = "TokenShopGUI";

    // Layout settings
    private static final int GUI_SIZE = 27;
    private static final int START_SLOT = 10; // 2nd Row, 2nd Slot
    private static final int BALANCE_SLOT = 18; // 3rd Row, 1st Slot

    private final Map<Integer, ShopItem> shopItems = new HashMap<>();
    private final String guiTitle = "<dark_gray>Token Shop</dark_gray>";

    private record ShopItem(String key, ItemStack displayItem, long cost) {}

    public TokenShopGUI(Login plugin, TokenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        // Listener registered in Module
        loadItems();
    }

    public void loadItems() {
        shopItems.clear();
        int currentSlot = START_SLOT;

        for (String key : manager.getItemManager().getShopKeys()) {
            if (currentSlot >= GUI_SIZE) break;
            // Avoid slots that wrap oddly or hit reserved areas if needed
            // For simplicity in a 27 slot GUI, row 2 is 9-17.
            if (currentSlot == 17) currentSlot = 19; // Skip last slot of row 2, jump to row 3? No, row 3 starts at 18.
            // The user said "start listing from 2nd slot of 2nd row".
            // Row 2: 9 10 11 12 13 14 15 16 17
            // 2nd slot is 10.

            ItemStack base = manager.getItemManager().getItem(key);
            long price = manager.getItemManager().getPrice(key);

            if (base != null) {
                ItemStack display = base.clone();
                ItemMeta meta = display.getItemMeta();
                List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
                lore.add(Component.empty());
                lore.add(mm.deserialize("<gray>Price: <gold>" + price + " ☆</gold>").decoration(TextDecoration.ITALIC, false));
                lore.add(mm.deserialize("<yellow>Click to purchase!</yellow>").decoration(TextDecoration.ITALIC, false));
                meta.lore(lore);
                display.setItemMeta(meta);

                shopItems.put(currentSlot, new ShopItem(key, display, price));
                currentSlot++;
            }
        }
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, mm.deserialize(guiTitle));

        // Filler
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.displayName(Component.empty());
        filler.setItemMeta(fMeta);
        for(int i=0; i<GUI_SIZE; i++) gui.setItem(i, filler);

        // Items
        shopItems.forEach((slot, item) -> gui.setItem(slot, item.displayItem));

        // Balance
        updateBalanceItemInInventory(gui, player);

        player.openInventory(gui);
        player.setMetadata(SHOP_METADATA, new FixedMetadataValue(plugin, true));
    }

    public void updateBalanceItem(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() == null &&
                player.hasMetadata(SHOP_METADATA)) {
            updateBalanceItemInInventory(player.getOpenInventory().getTopInventory(), player);
        }
    }

    private void updateBalanceItemInInventory(Inventory gui, Player player) {
        manager.getTokenBalance(player.getUniqueId()).thenAccept(balance -> {
            ItemStack item = new ItemStack(Material.SUNFLOWER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm.deserialize("<gold><bold>Your Balance</bold></gold>").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(mm.deserialize("<gray>You have: <gold>" + balance + " ☆</gold>").decoration(TextDecoration.ITALIC, false)));
            item.setItemMeta(meta);

            Bukkit.getScheduler().runTask(plugin, () -> gui.setItem(BALANCE_SLOT, item));
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata(SHOP_METADATA)) return;

        event.setCancelled(true); // PREVENT STEALING

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ShopItem item = shopItems.get(event.getSlot());
        if (item != null) {
            manager.purchaseItem(player, item.key, item.cost, this);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked().hasMetadata(SHOP_METADATA)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().hasMetadata(SHOP_METADATA)) {
            event.getPlayer().removeMetadata(SHOP_METADATA, plugin);
        }
    }
}