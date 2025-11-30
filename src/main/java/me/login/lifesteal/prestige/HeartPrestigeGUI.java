package me.login.lifesteal.prestige;

import me.login.Login;
import me.login.lifesteal.ItemManager;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

// Implements Listener so it can handle its own clicks
public class HeartPrestigeGUI implements Listener {

    private final Login plugin;
    private final HeartPrestigeManager manager;
    private final ItemManager itemManager;
    private final String HEAD_TEXTURE = "http://textures.minecraft.net/texture/33d1e94bcadcb9a165978937ce030122fccb06341f693f91b53a59fc8252adfa";
    private final String GUI_TITLE = "<dark_gray>Heart Prestige";

    public HeartPrestigeGUI(Login plugin, HeartPrestigeManager manager, ItemManager itemManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemManager = itemManager;
    }

    public void open(Player player) {
        Component title = itemManager.getMiniMessage().deserialize(GUI_TITLE);
        Inventory gui = Bukkit.createInventory(null, 27, ItemManager.toLegacy(title));

        // Fill Background with Dark Gray
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Slots 10 to 16
        int currentLevel = manager.getPrestigeLevel(player);

        for (int i = 0; i < 7; i++) {
            int slot = 10 + i;
            int tier = i + 1; // Prestige 1, 2, 3...

            gui.setItem(slot, createPrestigeItem(tier, currentLevel));
        }

        player.openInventory(gui);
    }

    private ItemStack createPrestigeItem(int tier, int playerCurrentLevel) {
        ItemStack item = TextureToHead.getHead(HEAD_TEXTURE);
        ItemMeta meta = item.getItemMeta();

        boolean unlocked = playerCurrentLevel >= tier;
        boolean isNext = playerCurrentLevel == (tier - 1);
        int cost = manager.getCostForNextLevel(tier);

        String nameColor = unlocked ? "<green>" : (isNext ? "<yellow>" : "<red>");
        String status = unlocked ? "UNLOCKED" : (isNext ? "CLICK TO PRESTIGE" : "LOCKED");

        meta.displayName(itemManager.getMiniMessage().deserialize(nameColor + "<bold>Prestige Tier " + tier).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Cost: ", NamedTextColor.GRAY).append(Component.text(cost + " Heart Items", NamedTextColor.RED)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Reward: ", NamedTextColor.GRAY).append(Component.text("+1 Max Heart Limit", NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        if (isNext) {
            lore.add(Component.text("Requires " + cost + " hearts in inventory!", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
        }

        lore.add(itemManager.getMiniMessage().deserialize(nameColor + "<bold>" + status).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!event.getView().getTitle().contains("Heart Prestige")) return;
        event.setCancelled(true);

        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != null) return;

        int slot = event.getSlot();
        if (slot >= 10 && slot <= 16) {
            int tier = slot - 9; // Slot 10 is Tier 1
            int currentLevel = manager.getPrestigeLevel(player);

            if (currentLevel >= tier) {
                player.sendMessage(itemManager.formatMessage("<red>You have already unlocked this prestige tier!"));
            } else if (currentLevel == (tier - 1)) {
                // Attempt purchase via Manager
                manager.attemptPrestige(player, tier);
                open(player); // Re-open to update icons
            } else {
                player.sendMessage(itemManager.formatMessage("<red>You must unlock the previous tier first!"));
            }
        }
    }
}