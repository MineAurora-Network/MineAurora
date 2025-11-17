package me.login.misc.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

    public RTPMenu(RTPModule module) {
        this.module = module;
        // Use MiniMessage for the title for consistency
        Component title = MiniMessage.miniMessage().deserialize(module.getServerPrefix() + " <gray>Random Teleport</gray>");
        this.inventory = Bukkit.createInventory(this, 27, title); // 27 slots = 3 rows
        initializeItems();
    }

    private void initializeItems() {
        // Slot 11: Overworld (Grass Block)
        inventory.setItem(11, createGuiItem(
                Material.GRASS_BLOCK,
                Component.text("Teleport to Overworld", NamedTextColor.GREEN, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in the Overworld.", NamedTextColor.GRAY)
                )
        ));

        // Slot 13: Nether (Netherrack)
        inventory.setItem(13, createGuiItem(
                Material.NETHERRACK,
                Component.text("Teleport to Nether", NamedTextColor.RED, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in the Nether.", NamedTextColor.GRAY)
                )
        ));

        // Slot 15: End (End Stone)
        inventory.setItem(15, createGuiItem(
                Material.END_STONE,
                Component.text("Teleport to The End", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD),
                Arrays.asList(
                        Component.text("Click to find a random, safe", NamedTextColor.GRAY),
                        Component.text("location in The End.", NamedTextColor.GRAY)
                )
        ));
    }

    private ItemStack createGuiItem(Material material, Component name, List<Component> lore) {
        ItemStack item = new ItemStack(material, 1);
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
                return; // Clicked on empty slot
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

        // Use the command's teleport logic (which now includes the cooldown check)
        RTPCommand.startTeleport(player, world, worldAlias, module);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}