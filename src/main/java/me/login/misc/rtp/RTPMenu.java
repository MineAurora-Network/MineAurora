package me.login.misc.rtp;

import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class RTPMenu implements Listener, InventoryHolder {

    private final Inventory inventory;
    private final RTPModule module;

    // Texture URLs
    private static final String OVERWORLD_TEXTURE = "http://textures.minecraft.net/texture/5a02e03ccabd21773df1c938df68908f02cde3f81d439aa9acadf7b6eb2a6395";
    private static final String NETHER_TEXTURE = "http://textures.minecraft.net/texture/b63c7f188070d1f27dfff8be4d5258c5a317009f566dfd9e861cf6e5f8fb38e2";
    private static final String END_TEXTURE = "http://textures.minecraft.net/texture/5dcf6ce9c81d84a6981d4c5b3dc09abb121ec7a807919265f871a5a7b1a8f21";

    public RTPMenu(RTPModule module) {
        this.module = module;
        // Changed title to Dark Gray "Random Teleport"
        Component title = MiniMessage.miniMessage().deserialize("<dark_gray>Random Teleport");
        this.inventory = Bukkit.createInventory(this, 27, title); // 27 slots = 3 rows
        initializeItems();
    }

    private void initializeItems() {
        // Fill empty slots with Gray Stained Glass Pane
        ItemStack filler = createFillerItem();
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // Slot 11: Overworld (Custom Head)
        inventory.setItem(11, createHeadItem(
                OVERWORLD_TEXTURE,
                Component.text("Teleport to Overworld", NamedTextColor.GREEN, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in the Overworld.", NamedTextColor.GRAY)
                )
        ));

        // Slot 13: Nether (Custom Head)
        inventory.setItem(13, createHeadItem(
                NETHER_TEXTURE,
                Component.text("Teleport to Nether", NamedTextColor.RED, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in the Nether.", NamedTextColor.GRAY)
                )
        ));

        // Slot 15: End (Custom Head)
        inventory.setItem(15, createHeadItem(
                END_TEXTURE,
                Component.text("Teleport to The End", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in The End.", NamedTextColor.GRAY)
                )
        ));
    }

    private ItemStack createFillerItem() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.empty());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack createHeadItem(String textureUrl, Component name, List<Component> lore) {
        // Use utility to get head with texture
        ItemStack item = TextureToHead.getHead(textureUrl);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
            meta.lore(lore.stream()
                    .map(line -> line.decoration(TextDecoration.ITALIC, false))
                    .toList());
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() != this) {
            return;
        }

        // Prevent players from taking items
        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        int slot = event.getSlot();

        String worldAlias = null;
        switch (slot) {
            case 11: // Overworld
                worldAlias = "overworld";
                break;
            case 13: // Nether
                worldAlias = "nether";
                break;
            case 15: // End
                worldAlias = "end";
                break;
            default:
                return; // Clicked on filler or empty
        }

        player.closeInventory();

        // Get the actual world name from config
        String worldName = module.getPlugin().getConfig().getString("worlds." + worldAlias);
        World world = Bukkit.getWorld(worldName);

        Component prefix = MiniMessage.miniMessage().deserialize(module.getServerPrefix());

        if (world == null) {
            player.sendMessage(prefix.append(Component.text(" The " + worldAlias + " world could not be found or isn't loaded.").color(NamedTextColor.RED)));
            module.getLogger().log("RTP Error: World '" + worldAlias + "' (config: " + worldName + ") is null.");
            return;
        }

        // Use the command's teleport logic
        RTPCommand.startTeleport(player, world, worldAlias, module);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}