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

    // Slot mapping for the 6 ranks in the 3rd row
    private static final int[] RANK_SLOTS = { 19, 20, 21, 22, 23, 24 };

    public DailyRewardGUI(Login plugin, DailyRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        this.rankKey = new NamespacedKey(plugin, "dailyreward_rank_key");
    }

    public void openGUI(Player player) {
        long startOfDay = manager.getStartOfCurrentDay();

        manager.getDatabase().getClaimedRankToday(player.getUniqueId(), startOfDay).thenAccept(claimedRankKey -> {

            Map<String, DailyRewardManager.Reward> rewards = manager.getRankRewards();
            ItemStack[] guiItems = new ItemStack[54];

            // Get player's best rank
            DailyRewardManager.Reward bestReward = manager.getBestRankReward(player);
            String bestRankKey = null;
            if (bestReward != null) {
                for (Map.Entry<String, DailyRewardManager.Reward> entry : rewards.entrySet()) {
                    if (entry.getValue() == bestReward) {
                        bestRankKey = entry.getKey();
                        break;
                    }
                }
            }

            // Create rank items
            int slotIndex = 0;
            for (Map.Entry<String, DailyRewardManager.Reward> entry : rewards.entrySet()) {
                if (slotIndex >= RANK_SLOTS.length) break;

                String rankKey = entry.getKey();
                DailyRewardManager.Reward reward = entry.getValue();
                boolean isClaimed = rankKey.equals(claimedRankKey);
                boolean isBest = rankKey.equals(bestRankKey);
                boolean canClaim = isBest && claimedRankKey == null;

                // --- FIX: Accessing public record fields ---
                guiItems[RANK_SLOTS[slotIndex]] = createRankItem(rankKey, reward, isClaimed, canClaim, player.hasPermission(reward.permission()));
                slotIndex++;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Component title = mm.deserialize("<dark_gray>Daily Rewards</dark_gray>");
                Inventory gui = Bukkit.createInventory(null, 54, title);

                ItemStack filler = createFillerItem();
                for (int i = 0; i < gui.getSize(); i++) {
                    if (guiItems[i] == null) {
                        gui.setItem(i, filler);
                    } else {
                        gui.setItem(i, guiItems[i]);
                    }
                }

                player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, true));
                player.openInventory(gui);
            });
        });
    }

    private ItemStack createRankItem(String rankKey, DailyRewardManager.Reward reward, boolean isClaimed, boolean canClaim, boolean hasPerm) {
        Material mat = isClaimed ? Material.MINECART : Material.CHEST_MINECART;
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // --- FIX: Accessing public record fields ---
        Component rankName = mm.deserialize(reward.prettyName());
        meta.displayName(rankName.decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Coins: <white>" + coinFormat.format(reward.coins()) + "</white>"));
        lore.add(mm.deserialize("<gray>Tokens: <white>" + reward.tokens() + "</white>"));
        lore.add(Component.empty());

        if (isClaimed) {
            lore.add(mm.deserialize("<red>Already claimed!</red>"));
        } else if (canClaim) {
            lore.add(mm.deserialize("<yellow>Click to claim!</yellow>"));
        } else if (hasPerm) {
            // Has perm, but it's not their best (or they claimed a different one? shouldn't happen)
            lore.add(mm.deserialize("<gray>You have this rank, but can claim a better one!</gray>"));
        } else {
            lore.add(mm.deserialize("<red>You do not have this rank.</red>"));
            lore.add(mm.deserialize("<red>Visit store.mineaurora.fun to buy!</red>")); // Example store link
        }

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);

        // Add PDC tag to identify which rank this is
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        // --- FIX: Use this.rankKey as the KEY, rankKey as the VALUE ---
        pdc.set(this.rankKey, PersistentDataType.STRING, rankKey);

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").color(NamedTextColor.GRAY));
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

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String clickedRankKey = null;

        // --- FIX: Use this.rankKey to check for and get the value ---
        if (pdc.has(this.rankKey, PersistentDataType.STRING)) {
            clickedRankKey = pdc.get(this.rankKey, PersistentDataType.STRING);
        }
        // --- END FIX ---

        if (clickedRankKey == null) {
            // Clicked on glass or something else
            return;
        }

        // Player clicked a rank item
        DailyRewardManager.Reward bestReward = manager.getBestRankReward(player);
        if (bestReward == null) {
            player.closeInventory();
            return;
        }

        // --- FIX: Use .equals() to compare records ---
        if (bestReward.equals(manager.getRankRewards().get(clickedRankKey))) {
            // It's their best rank. Try to claim it.
            // The manager will handle cooldown checks and messaging.
            // --- FIX: Accessing public record field ---
            manager.claimRankedReward(player, clickedRankKey, bestReward);
        } else if (player.hasPermission(manager.getRankRewards().get(clickedRankKey).permission())) {
            // Clicked a rank they have, but it's not their best
            player.sendMessage(manager.getPrefix().append(mm.deserialize("<red>You can claim a higher-tier reward!</red>")));
        } else {
            // Clicked a rank they don't have
            player.sendMessage(manager.getPrefix().append(mm.deserialize("<red>You do not have permission for this reward.</red>")));
        }
    }
}