package me.login.misc.milestones;

import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class MilestoneGUI implements Listener {

    private final Login plugin;
    private final MilestoneManager manager;
    private final MiniMessage mm;
    private final String HEAD_TEXTURE = "http://textures.minecraft.net/texture/7c9abb58e3b82068a662a294d0ffd8035a71a2f2067de056a18d7e185c0da3cd";

    public MilestoneGUI(Login plugin, MilestoneManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = MiniMessage.miniMessage();
    }

    public void open(Player player) {
        Component title = mm.deserialize(plugin.getServerPrefix().trim() + " <dark_gray>Milestones");
        Inventory gui = Bukkit.createInventory(null, 27, title); // 3 rows

        // Fill background with Dark Gray
        // In 1.13+, GRAY_STAINED_GLASS_PANE is Dark Gray.
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Slots 10 to 16 (Milestones 0 to 6)
        for (int i = 0; i < 7; i++) {
            int slot = 10 + i;
            gui.setItem(slot, createMilestoneItem(player, i));
        }

        player.openInventory(gui);
    }

    private ItemStack createMilestoneItem(Player player, int index) {
        ItemStack item = TextureToHead.getHead(HEAD_TEXTURE);
        ItemMeta meta = item.getItemMeta();

        int reqKills = manager.getRequiredKills(index);
        int currentStreak = manager.getStreak(player.getUniqueId());
        boolean claimed = manager.isClaimed(player.getUniqueId(), index);
        boolean canClaim = currentStreak >= reqKills && !claimed;

        String statusColor = claimed ? "<red>" : (canClaim ? "<green>" : "<yellow>");
        String statusText = claimed ? "CLAIMED" : (canClaim ? "CLICK TO CLAIM" : "LOCKED");

        meta.displayName(mm.deserialize("<gold><bold>Milestone " + (index + 1)).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>Requirement: <red>" + reqKills + " Kill Streak").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<gray>Your Streak: <yellow>" + currentStreak).decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<gray>Reward: <gold>20 Tokens").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(mm.deserialize(statusColor + "<bold>" + statusText).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        // Simple string check is safer when titles contain gradients/components
        if (!e.getView().getTitle().contains("Milestones")) return;

        e.setCancelled(true); // Prevent taking items

        if (e.getClickedInventory() == null || e.getClickedInventory().getHolder() != null) return;

        int slot = e.getSlot();
        if (slot >= 10 && slot <= 16) {
            int milestoneIndex = slot - 10;
            if (e.getWhoClicked() instanceof Player player) {
                if (manager.canClaim(player.getUniqueId(), milestoneIndex)) {
                    manager.claimMilestone(player, milestoneIndex);
                    open(player); // Refresh
                } else if (manager.isClaimed(player.getUniqueId(), milestoneIndex)) {
                    player.sendMessage(mm.deserialize("<red>You have already claimed this milestone."));
                } else {
                    player.sendMessage(mm.deserialize("<red>You haven't reached the required kill streak yet!"));
                }
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTitle().contains("Milestones")) {
            e.setCancelled(true);
        }
    }
}