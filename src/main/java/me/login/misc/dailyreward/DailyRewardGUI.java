package me.login.misc.dailyreward;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent; // Added explicit import
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DailyRewardGUI implements Listener {

    private final Login plugin;
    private final DailyRewardManager manager;
    private final MiniMessage mm;
    private static final String GUI_METADATA = "DailyRewardGUI";
    private final NamespacedKey rankKey;
    private static final DecimalFormat coinFormat = new DecimalFormat("#,###");

    // Updated slot mapping for the new 6-row layout
    private static final int[] RANK_SLOTS = { 10, 13, 16, 28, 31, 34 };
    private static final int GUI_ROWS = 6;
    private static final int GUI_SIZE = GUI_ROWS * 9; // 54 slots
    private static final int CLOSE_SLOT = 49; // Middle of 6th row

    public DailyRewardGUI(Login plugin, DailyRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        this.rankKey = new NamespacedKey(plugin, "dailyreward_rank_key");
    }

    public void openGUI(Player player) {
        long startOfDay = manager.getStartOfCurrentDay();

        // Get a list of *all* claimed ranks asynchronously
        manager.getDatabase().getClaimedRanksToday(player.getUniqueId(), startOfDay).thenAccept(claimedRanks -> {

            Map<String, DailyRewardManager.Reward> rewards = manager.getRankRewards();
            ItemStack[] guiItems = new ItemStack[GUI_SIZE];

            // Create rank items
            int slotIndex = 0;
            for (Map.Entry<String, DailyRewardManager.Reward> entry : rewards.entrySet()) {
                if (slotIndex >= RANK_SLOTS.length) break;

                String rankKey = entry.getKey();
                DailyRewardManager.Reward reward = entry.getValue();

                boolean isClaimed = claimedRanks.contains(rankKey);
                boolean hasPerm = player.hasPermission(reward.permission());

                guiItems[RANK_SLOTS[slotIndex]] = createRankItem(rankKey, reward, isClaimed, hasPerm);
                slotIndex++;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Component title = mm.deserialize("<dark_gray>Daily Rewards</dark_gray>");
                Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

                // Add close button
                ItemStack close = new ItemStack(Material.BARRIER);
                ItemMeta closeMeta = close.getItemMeta();
                if (closeMeta != null) {
                    closeMeta.displayName(mm.deserialize("<red><bold>Close</bold></red>").decoration(TextDecoration.ITALIC, false));
                    close.setItemMeta(closeMeta);
                }
                guiItems[CLOSE_SLOT] = close;

                ItemStack filler = createFillerItem();
                for (int i = 0; i < gui.getSize(); i++) {
                    if (guiItems[i] == null) {
                        gui.setItem(i, filler);
                    } else {
                        gui.setItem(i, guiItems[i]);
                    }
                }

                // CRITICAL ORDER CHANGE FOR REFRESH SAFETY:
                // 1. Open the inventory first. If the player already has a menu open,
                //    this triggers 'onInventoryClose' for the OLD menu immediately.
                player.openInventory(gui);

                // 2. 'onInventoryClose' will run and remove the OLD metadata.

                // 3. NOW set the NEW metadata. This ensures the player has the metadata
                //    for this new session and it wasn't wiped by the close event of the previous session.
                player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, true));
            });
        });
    }

    private ItemStack createRankItem(String rankKey, DailyRewardManager.Reward reward, boolean isClaimed, boolean hasPerm) {
        Material mat = (isClaimed || !hasPerm) ? Material.MINECART : Material.CHEST_MINECART;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return item;
        }

        Component rankName = mm.deserialize(reward.prettyName());
        meta.displayName(rankName.decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gold>Coins: <yellow>" + coinFormat.format(reward.coins()) + "</yellow></gold>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<light_purple>Tokens: <aqua>" + reward.tokens() + "</aqua></light_purple>").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (isClaimed) {
            lore.add(mm.deserialize("<red>You have already claimed</red>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize("<red>this reward today!</red>").decoration(TextDecoration.ITALIC, false));
        } else if (hasPerm) {
            lore.add(mm.deserialize("<yellow>Click to claim!</yellow>").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(mm.deserialize("<red>You do not have this rank.</red>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize("<red>Visit store.mineaurora.fun to buy!</red>").decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(this.rankKey, PersistentDataType.STRING, rankKey);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ").color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasMetadata(GUI_METADATA)) return;

        event.setCancelled(true);
        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (clickedItem.getType() == Material.BARRIER && event.getSlot() == CLOSE_SLOT) {
            player.closeInventory();
            return;
        }

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String clickedRankKey = null;

        if (pdc.has(this.rankKey, PersistentDataType.STRING)) {
            clickedRankKey = pdc.get(this.rankKey, PersistentDataType.STRING);
        }

        if (clickedRankKey == null) {
            return;
        }

        DailyRewardManager.Reward reward = manager.getRankRewards().get(clickedRankKey);
        if (reward == null) return;

        if (player.hasPermission(reward.permission())) {
            manager.claimRankedReward(player, clickedRankKey, reward).thenAccept(success -> {
                if (success) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> openGUI(player));
                }
            });
        } else {
            player.sendMessage(manager.getPrefix().append(mm.deserialize("<red>You do not have permission for this reward.</red>")));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (player.hasMetadata(GUI_METADATA)) {
            player.removeMetadata(GUI_METADATA, plugin);
        }
    }
}