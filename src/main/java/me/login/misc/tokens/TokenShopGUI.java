package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
    private static final int BALANCE_SLOT = 4;

    // Cache items to avoid rebuilding every time
    private final Map<Integer, ShopItem> shopItems = new HashMap<>();
    private int guiSize = 27;
    private String guiTitle = "<dark_gray>Token Shop</dark_gray>";

    private record ShopItem(String key, ItemStack displayItem, long cost, int slot) {}

    public TokenShopGUI(Login plugin, TokenManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        loadConfig();
    }

    public void loadConfig() {
        shopItems.clear();
        // Reload config logic if needed, assuming manager has access to plugin config
        // For now, using items.yml structure as implied by previous interactions or a specific section
        // Assuming 'token_shop' section in config.yml or similar
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("token_shop");
        if (section == null) return; // Or default setup

        this.guiTitle = section.getString("title", "<dark_gray>Token Shop</dark_gray>");
        this.guiSize = section.getInt("size", 27);

        ConfigurationSection itemsSec = section.getConfigurationSection("items");
        if (itemsSec != null) {
            for (String key : itemsSec.getKeys(false)) {
                String itemKey = itemsSec.getString(key + ".item_key"); // Key in items.yml
                long cost = itemsSec.getLong(key + ".cost");
                int slot = itemsSec.getInt(key + ".slot");

                ItemStack item = manager.getItemManager().getItem(itemKey); // Get display item
                if (item != null) {
                    ItemMeta meta = item.getItemMeta();
                    List<Component> lore = meta.hasLore() ? meta.lore() : new ArrayList<>();
                    lore.add(Component.empty());
                    lore.add(mm.deserialize("<gray>Cost: <gold>" + cost + " ☆</gold>").decoration(TextDecoration.ITALIC, false));
                    lore.add(mm.deserialize("<yellow>Click to purchase!</yellow>").decoration(TextDecoration.ITALIC, false));
                    meta.lore(lore);
                    item.setItemMeta(meta);

                    shopItems.put(slot, new ShopItem(itemKey, item, cost, slot));
                }
            }
        }
    }

    public void openGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, guiSize, mm.deserialize(guiTitle));

        // Fill items
        shopItems.forEach((slot, shopItem) -> gui.setItem(slot, shopItem.displayItem));

        // Add fillers if needed (optional)
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fMeta = filler.getItemMeta();
        fMeta.displayName(Component.empty());
        filler.setItemMeta(fMeta);
        for (int i = 0; i < guiSize; i++) {
            if (gui.getItem(i) == null) gui.setItem(i, filler);
        }

        // Add Balance Indicator
        updateBalanceItemInInventory(gui, player);

        player.setMetadata(SHOP_METADATA, new FixedMetadataValue(plugin, true));
        player.openInventory(gui);
    }

    // Requirement 4: Method to update balance item dynamically
    public void updateBalanceItem(Player player) {
        if (player.getOpenInventory().getTopInventory().getHolder() == null &&
                player.hasMetadata(SHOP_METADATA)) { // Simple check if shop is open
            updateBalanceItemInInventory(player.getOpenInventory().getTopInventory(), player);
        }
    }

    private void updateBalanceItemInInventory(Inventory gui, Player player) {
        manager.getTokenBalance(player.getUniqueId()).thenAccept(balance -> {
            ItemStack balanceItem = new ItemStack(Material.SUNFLOWER);
            ItemMeta meta = balanceItem.getItemMeta();
            meta.displayName(mm.deserialize("<gold><bold>Your Balance</bold></gold>").decoration(TextDecoration.ITALIC, false));
            meta.lore(List.of(
                    mm.deserialize("<gray>You have: <gold>" + balance + " ☆</gold></gray>").decoration(TextDecoration.ITALIC, false)
            ));
            balanceItem.setItemMeta(meta);

            // Run on main thread to modify inventory
            Bukkit.getScheduler().runTask(plugin, () -> gui.setItem(BALANCE_SLOT, balanceItem));
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata(SHOP_METADATA)) return;

        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        ShopItem item = shopItems.get(slot);

        if (item != null) {
            manager.purchaseItem(player, item.key, item.cost, this);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().hasMetadata(SHOP_METADATA)) {
            // Requirement 3: Remove metadata on close
            event.getPlayer().removeMetadata(SHOP_METADATA, plugin);
        }
    }
}