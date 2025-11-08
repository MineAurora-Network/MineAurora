package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class TokenShopGUI implements Listener {

    private final Login plugin;
    private final TokenManager manager;
    private final MiniMessage mm;
    private static final String GUI_METADATA = "TokenShopGUI";
    private final NamespacedKey itemKeyPDC;
    private final NamespacedKey costKeyPDC;

    // Define shop layout: Slot -> ItemKey in items.yml
    // You can add more items here and in config.yml
    private final ShopItem[] shopLayout = {
            new ShopItem(0, "sell-wand-1.5", 100),
            new ShopItem(1, "sell-wand-2.0", 250),
            new ShopItem(2, "sell-wand-2.5", 500),
            new ShopItem(3, "feed-voucher-3d", 75),
            new ShopItem(4, "craft-voucher-3d", 75)
            // Add item 6 here, e.g.:
            // new ShopItem(30, "my-new-item-key", 1000)
    };

    // Simple record to hold shop item data
    private record ShopItem(int slot, String itemKey, long cost) {}

    public TokenShopGUI(Login plugin, TokenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        this.itemKeyPDC = new NamespacedKey(plugin, "token_shop_item");
        this.costKeyPDC = new NamespacedKey(plugin, "token_shop_cost");
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, mm.deserialize("<dark_gray>Token Shop</dark_gray>")); // 6 rows

        // --- Fill Decoration ---
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" ").color(NamedTextColor.GRAY));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, filler);
        }

        // --- Close Button ---
        ItemStack close = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = close.getItemMeta();
        closeMeta.displayName(mm.deserialize("<red><bold>Close</bold></red>").decoration(TextDecoration.ITALIC, false));
        close.setItemMeta(closeMeta);
        gui.setItem(49, close);

        // --- Load Shop Items ---
        manager.getTokenBalance(player.getUniqueId()).thenAccept(balance -> {
            for (ShopItem shopItem : shopLayout) {
                ItemStack displayItem = createShopItem(player, shopItem.itemKey, shopItem.cost, balance);
                gui.setItem(shopItem.slot, displayItem);
            }

            // --- Player Balance Info ---
            ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
            ItemMeta balanceMeta = balanceItem.getItemMeta();
            balanceMeta.displayName(mm.deserialize("<gold><bold>Your Balance</bold></gold>").decoration(TextDecoration.ITALIC, false));
            balanceMeta.lore(List.of(
                    mm.deserialize("<gray>You have <white>" + balance + " ☆</white> Tokens.</gray>").decoration(TextDecoration.ITALIC, false)
            ));
            balanceItem.setItemMeta(balanceMeta);
            gui.setItem(48, balanceItem); // Next to close button

            // Open inventory on main thread
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, true));
                player.openInventory(gui);
            });
        });
    }

    private ItemStack createShopItem(Player player, String itemKey, long cost, long balance) {
        ItemStack item = manager.getItemManager().getItem(itemKey);
        if (item == null) {
            // Item not in items.yml
            item = new ItemStack(Material.BARRIER);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(mm.deserialize("<red>Error: Item '" + itemKey + "' not found.</red>"));
            meta.lore(List.of(mm.deserialize("<gray>Please contact an admin.</gray>")));
            item.setItemMeta(meta);
            return item;
        }

        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Cost: <gold>" + cost + " ☆</gold>").decoration(TextDecoration.ITALIC, false));

        if (balance >= cost) {
            lore.add(mm.deserialize("<green>Click to purchase!</green>").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(mm.deserialize("<red>Not enough tokens!</red>").decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);

        // Add PDC tags
        meta.getPersistentDataContainer().set(itemKeyPDC, PersistentDataType.STRING, itemKey);
        meta.getPersistentDataContainer().set(costKeyPDC, PersistentDataType.LONG, cost);

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata(GUI_METADATA)) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        // Close button
        if (clickedItem.getType() == Material.BARRIER && clickedItem.getItemMeta().getDisplayName().contains("Close")) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(itemKeyPDC, PersistentDataType.STRING) && pdc.has(costKeyPDC, PersistentDataType.LONG)) {
            String itemKey = pdc.get(itemKeyPDC, PersistentDataType.STRING);
            Long cost = pdc.get(costKeyPDC, PersistentDataType.LONG);

            if (itemKey != null && cost != null) {
                // Let the manager handle the purchase logic
                manager.purchaseItem(player, itemKey, cost);
            }
        }
    }
}