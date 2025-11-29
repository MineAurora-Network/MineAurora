package me.login.level;

import me.login.Login;
import me.login.lifesteal.ItemManager;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.ArrayList;
import java.util.List;

public class LevelGUI implements Listener {

    private final Login plugin;
    private final ItemManager itemManager;
    private final MiniMessage mm;
    private final String GUI_TITLE = "<dark_gray>Lifesteal Levels";
    private final String METADATA_KEY = "LifestealLevelGUI";

    public LevelGUI(Login plugin) {
        this.plugin = plugin;
        this.itemManager = new ItemManager(plugin); // Or pass existing one
        this.mm = MiniMessage.miniMessage();
    }

    public void open(Player player) {
        Inventory gui = Bukkit.createInventory(null, 27, mm.deserialize(GUI_TITLE));

        // Fill background with GRAY (Dark Gray) glass panes
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);

        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        // Slot 11: What are Levels?
        String head11 = "http://textures.minecraft.net/texture/1ea522f5f15f944ebb42d33fd4f6130c07dcde52a27c0f09a5d6bbd1197b91d7";
        List<String> lore11 = List.of(
                "",
                "<gray>Levels are a fun aspect to lifesteal",
                "<gray>that allows you to progress further",
                "<gray>on the server",
                "",
                "<gray>Every time you level up u will gain",
                "<green>1<gray>,<green>000 <gray>in-game cash"
        );
        gui.setItem(11, createGuiItem(head11, "<green>What are Levels?", lore11));

        // Slot 13: How to gain XP
        String head13 = "http://textures.minecraft.net/texture/d0a471e37c513fd75f3f1aa3ee2c5d710f61da5bb1a2ce844269a2e4db12a00d";
        List<String> lore13 = List.of(
                "",
                "<gray>Mining/Farming: <green>[1 XP]",
                "<gray>Kill Mob (<50HP): <green>[1 XP]",
                "<gray>Kill Mob (50-125HP): <green>[2 XP]",
                "<gray>Kill Mob (>125HP): <green>[3 XP]",
                "<gray>Kill Player: <green>[5 XP]",
                "<gray>Eat Food: <green>[2 XP]",
                "<gray>Breed Animals: <green>[1 XP]",
                "<gray>Brew Potion: <green>[1 XP]",
                "<gray>Fishing: <green>[2 XP]",
                "",
                "<red>Dying (Natural): <dark_red>[-5 XP]",
                "<red>Dying (PvP): <dark_red>[-10 XP]"
        );
        gui.setItem(13, createGuiItem(head13, "<yellow>How to gain XP:", lore13));

        // Slot 15: More Info
        String head15 = "http://textures.minecraft.net/texture/1ea522f5f15f944ebb42d33fd4f6130c07dcde52a27c0f09a5d6bbd1197b91d7"; // Same texture requested
        List<String> lore15 = List.of(
                "",
                "<gray>Your level is shown on your nametab in",
                "<green>tab menu<gray> and <green>in chat <gray>along with being shown",
                "<gray>on your <green>scoreboard",
                "",
                "<gray>You can see a detailed breakdown of your",
                "<gray>XP by using command <green>/lifesteallevel"
        );
        gui.setItem(15, createGuiItem(head15, "<aqua>More Info:", lore15));

        player.setMetadata(METADATA_KEY, new FixedMetadataValue(plugin, true));
        player.openInventory(gui);
    }

    private ItemStack createGuiItem(String textureUrl, String name, List<String> lore) {
        ItemStack item = TextureToHead.getHead(textureUrl);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));

        List<Component> componentLore = new ArrayList<>();
        for (String line : lore) {
            componentLore.add(mm.deserialize(line).decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(componentLore);

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        // Check metadata first for robust handling
        if (player.hasMetadata(METADATA_KEY)) {
            // Ensure they are clicking inside the GUI we opened
            if (event.getView().getTopInventory().equals(event.getClickedInventory())) {
                event.setCancelled(true);
            } else if (event.isShiftClick() && event.getView().getTopInventory().equals(event.getInventory())) {
                event.setCancelled(true); // Prevent shift clicking into the GUI
            }
            // Actually, simplest is to cancel ALL clicks while this GUI is open
            // but usually we want to allow player inventory clicks unless they affect the GUI
            if (event.getInventory().equals(event.getView().getTopInventory())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && player.hasMetadata(METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player && player.hasMetadata(METADATA_KEY)) {
            player.removeMetadata(METADATA_KEY, plugin);
        }
    }
}