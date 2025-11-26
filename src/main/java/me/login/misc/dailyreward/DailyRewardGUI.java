package me.login.misc.dailyreward;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DailyRewardGUI implements Listener {

    private final Login plugin;
    private final DailyRewardManager manager;
    private final MiniMessage mm;
    private static final String GUI_METADATA = "DailyRewardGUI";

    private final Player explicitPlayer;

    public DailyRewardGUI(Login plugin, DailyRewardManager manager) {
        this(plugin, manager, null);
    }

    public DailyRewardGUI(Login plugin, DailyRewardManager manager, Player player) {
        this.plugin = plugin;
        this.manager = manager;
        this.explicitPlayer = player;
        this.mm = MiniMessage.miniMessage();

        if (player == null) {
            plugin.getServer().getPluginManager().registerEvents(this, plugin);
        }
    }

    public void open() {
        if (explicitPlayer != null) {
            openGUI(explicitPlayer);
        } else {
            plugin.getLogger().warning("Attempted to call open() on DailyRewardGUI without a player context.");
        }
    }

    public void openGUI(Player player) {
        manager.getDatabase().getClaimData(player.getUniqueId(), "default").thenAccept(defaultData -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Inventory gui = Bukkit.createInventory(null, 27, mm.deserialize("<black>Daily Rewards</black>"));

                ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
                ItemMeta fillerMeta = filler.getItemMeta();
                fillerMeta.displayName(Component.empty());
                filler.setItemMeta(fillerMeta);

                for (int i = 0; i < 27; i++) {
                    gui.setItem(i, filler);
                }

                gui.setItem(4, getPlayerHead(player, defaultData.streak()));

                Map<String, DailyRewardManager.Reward> rewards = manager.getRankRewards();
                String[] rankKeys = {"elite", "ace", "overlord", "immortal", "supreme", "phantom"};
                int[] slots = {10, 11, 12, 14, 15, 16};

                for (int i = 0; i < rankKeys.length; i++) {
                    if (i >= slots.length) break;
                    String key = rankKeys[i];
                    DailyRewardManager.Reward reward = rewards.get(key);
                    if (reward != null) {
                        gui.setItem(slots[i], createRewardItem(player, key, reward, false));

                        int finalSlot = slots[i];
                        manager.getDatabase().getLastClaimTime(player.getUniqueId(), key).thenAccept(lastClaim -> {
                            boolean claimedToday = (System.currentTimeMillis() - lastClaim) < 24 * 60 * 60 * 1000;
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                if (player.getOpenInventory().getTopInventory().equals(gui)) {
                                    gui.setItem(finalSlot, createRewardItem(player, key, reward, claimedToday));
                                }
                            });
                        });
                    }
                }

                player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, true));
                player.openInventory(gui);
            });
        });
    }

    private ItemStack getPlayerHead(Player player, int streak) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<yellow>Your Stats</yellow>").decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Streaks increase your <gold>Money</gold></gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<gray>reward by <green>10%</green> per day!</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Global Streak: <white>" + streak + " Days</white></gray>").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createRewardItem(Player player, String key, DailyRewardManager.Reward reward, boolean claimedToday) {
        ItemStack item = new ItemStack(Material.MINECART);
        ItemMeta meta = item.getItemMeta();

        boolean hasPerm = player.hasPermission(reward.permission());

        meta.displayName(mm.deserialize(reward.prettyName()).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Reward:</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize(" <dark_gray>•</dark_gray> <gold>" + reward.coins() + " Coins</gold>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize(" <dark_gray>•</dark_gray> <yellow>" + reward.tokens() + " Tokens</yellow>").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (claimedToday) {
            lore.add(mm.deserialize("<red>Already Claimed!</red>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize("<gray>Come back tomorrow.</gray>").decoration(TextDecoration.ITALIC, false));
        } else if (hasPerm) {
            lore.add(mm.deserialize("<yellow>Click to claim!</yellow>").decoration(TextDecoration.ITALIC, false));
        } else {
            lore.add(mm.deserialize("<red>Locked!</red>").decoration(TextDecoration.ITALIC, false));
            lore.add(mm.deserialize("<gray>Purchase rank at <aqua>store.mineaurora.net</aqua></gray>").decoration(TextDecoration.ITALIC, false));
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        if (!p.hasMetadata(GUI_METADATA)) return;

        // FIX 2: Cancel event immediately to prevent stealing
        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        int slot = event.getSlot();
        String[] rankKeys = {"elite", "ace", "overlord", "immortal", "supreme", "phantom"};
        int[] slots = {10, 11, 12, 14, 15, 16};

        for (int i = 0; i < slots.length; i++) {
            if (slot == slots[i]) {
                String key = rankKeys[i];
                DailyRewardManager.Reward reward = manager.getRankRewards().get(key);

                if (p.hasPermission(reward.permission())) {
                    p.closeInventory();
                    manager.claimRankedReward(p, key, reward);
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                } else {
                    p.sendMessage(manager.getPrefix().append(mm.deserialize("<red>You do not have permission to claim the " + reward.prettyName() + " <red>reward.")));
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                }
                return;
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer().hasMetadata(GUI_METADATA)) {
            event.getPlayer().removeMetadata(GUI_METADATA, plugin);
        }
    }
}