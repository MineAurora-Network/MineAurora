package me.login.items.anvil;

import me.login.Login;
import me.login.items.ArmorManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DyeAnvilMenu implements Listener {

    private final Login plugin;
    private final ArmorManager armorManager;
    private final String GUI_TITLE = "Dye Anvil";
    private final NamespacedKey DYE_HEX_KEY;

    // --- GUI Slot Constants ---
    private static final int GUI_SIZE = 54; // 6 Rows

    // --- USER-DEFINED SLOTS ---
    private static final int SLOT_ARMOR = 29; // Row 4, Slot 3
    private static final int SLOT_DYE = 33;   // Row 4, Slot 7
    private static final int SLOT_RESULT = 13;  // Row 2, Slot 5
    private static final int SLOT_ANVIL = 22;   // Row 3, Slot 5
    private static final int SLOT_CLOSE = 49;   // Row 6, Slot 5 (Middle)

    // Slots that change color per user request
    private static final Set<Integer> INDICATOR_SLOTS = new HashSet<>(Arrays.asList(
            11, 12, 14, 15, // Row 2
            20, 24          // Row 3
    ));

    // Slots player can interact with
    private static final Set<Integer> INTERACT_SLOTS = new HashSet<>(Arrays.asList(
            SLOT_ARMOR, SLOT_DYE, SLOT_ANVIL, SLOT_CLOSE
    ));

    // --- Cached ItemStacks ---
    private final ItemStack GRAY_PANE;
    private final ItemStack RED_PANE;
    private final ItemStack GREEN_PANE;
    private final ItemStack BARRIER;
    private final ItemStack ANVIL_BUTTON;
    private final ItemStack CLOSE_BUTTON;

    public DyeAnvilMenu(Login plugin, ArmorManager armorManager) {
        this.plugin = plugin;
        this.armorManager = armorManager;
        this.DYE_HEX_KEY = new NamespacedKey(plugin, "dye_hex"); // Must match items.yml

        // --- Pre-build GUI items ---
        // --- FIX: Glass panes have no name ---
        this.GRAY_PANE = createPane(Material.GRAY_STAINED_GLASS_PANE, " ");
        this.RED_PANE = createPane(Material.RED_STAINED_GLASS_PANE, " ");
        this.GREEN_PANE = createPane(Material.LIME_STAINED_GLASS_PANE, " ");

        this.BARRIER = new ItemStack(Material.BARRIER);
        ItemMeta barrierMeta = BARRIER.getItemMeta();
        // Result barrier has a name
        barrierMeta.displayName(MiniMessage.miniMessage().deserialize("<red><b>No Valid Recipe</b>").decoration(TextDecoration.ITALIC, false));
        BARRIER.setItemMeta(barrierMeta);

        this.ANVIL_BUTTON = new ItemStack(Material.ANVIL);
        ItemMeta anvilMeta = ANVIL_BUTTON.getItemMeta();
        anvilMeta.displayName(MiniMessage.miniMessage().deserialize("<green><b>Combine Items</b>").decoration(TextDecoration.ITALIC, false));
        ANVIL_BUTTON.setItemMeta(anvilMeta);

        this.CLOSE_BUTTON = new ItemStack(Material.BARRIER);
        ItemMeta closeMeta = CLOSE_BUTTON.getItemMeta();
        closeMeta.displayName(MiniMessage.miniMessage().deserialize("<red><b>Close Menu</b>").decoration(TextDecoration.ITALIC, false));
        CLOSE_BUTTON.setItemMeta(closeMeta);
    }

    private ItemStack createPane(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        // --- FIX: Use Component.text() for plain " " ---
        meta.displayName(Component.text(name));
        item.setItemMeta(meta);
        return item;
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(null, GUI_SIZE, Component.text(GUI_TITLE));

        // 1. Fill rows 1-5 with GRAY_PANE
        for (int i = 0; i < 45; i++) {
            inv.setItem(i, GRAY_PANE);
        }

        // 2. Fill row 6 with RED_PANE
        for (int i = 45; i < 54; i++) {
            inv.setItem(i, RED_PANE);
        }

        // 3. Set indicator panes to RED_PANE
        for (int slot : INDICATOR_SLOTS) {
            inv.setItem(slot, RED_PANE);
        }

        // 4. Set functional items
        inv.setItem(SLOT_RESULT, BARRIER);
        inv.setItem(SLOT_ANVIL, ANVIL_BUTTON);
        inv.setItem(SLOT_CLOSE, CLOSE_BUTTON);

        // 5. Clear input slots
        inv.setItem(SLOT_ARMOR, null);
        inv.setItem(SLOT_DYE, null);

        player.openInventory(inv);
        plugin.getViewingInventories().put(player.getUniqueId(), player.getUniqueId()); // Generic marker
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int slot = event.getRawSlot();

        // Check if player clicked outside inventory
        if (slot == -999) return;

        // Player clicked their own inventory
        if (slot >= GUI_SIZE) {
            // Allow shift-clicking into the correct slots
            if (event.isShiftClick()) {
                ItemStack currentItem = event.getCurrentItem();
                if (currentItem == null) return;

                if (armorManager.isCustomArmor(currentItem)) {
                    // Try to move to armor slot
                    if (event.getInventory().getItem(SLOT_ARMOR) == null) {
                        event.getInventory().setItem(SLOT_ARMOR, currentItem.clone());
                        currentItem.setAmount(0);
                        event.setCancelled(true);
                    }
                } else if (isValidDye(currentItem)) {
                    // Try to move to dye slot
                    if (event.getInventory().getItem(SLOT_DYE) == null) {
                        event.getInventory().setItem(SLOT_DYE, currentItem.clone());
                        currentItem.setAmount(0);
                        event.setCancelled(true);
                    }
                }
            }
            // Allow normal clicks in player inv
        } else {
            // Player clicked inside the GUI
            if (!INTERACT_SLOTS.contains(slot)) {
                event.setCancelled(true);
            }

            // Handle functional button clicks
            if (slot == SLOT_ANVIL) {
                event.setCancelled(true);
                handleCombine(player, event.getInventory());
            } else if (slot == SLOT_CLOSE) {
                event.setCancelled(true);
                player.closeInventory();
            }
        }

        // Update Logic after click (Delayed to allow item to move)
        new BukkitRunnable() {
            @Override
            public void run() {
                updateMenu(event.getInventory());
            }
        }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        for (int slot : event.getRawSlots()) {
            if (slot < GUI_SIZE && !INTERACT_SLOTS.contains(slot)) {
                event.setCancelled(true);
                return;
            }
        }
        new BukkitRunnable() { @Override public void run() { updateMenu(event.getInventory()); } }.runTaskLater(plugin, 1L);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!event.getView().title().equals(Component.text(GUI_TITLE))) return;

        plugin.getViewingInventories().remove(event.getPlayer().getUniqueId());

        // Give back items
        giveBack(event.getPlayer(), event.getInventory().getItem(SLOT_ARMOR));
        giveBack(event.getPlayer(), event.getInventory().getItem(SLOT_DYE));
    }

    private void giveBack(org.bukkit.entity.HumanEntity player, ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            player.getInventory().addItem(item).forEach((k, v) -> player.getWorld().dropItem(player.getLocation(), v));
        }
    }

    private void updateMenu(Inventory inv) {
        ItemStack armor = inv.getItem(SLOT_ARMOR);
        ItemStack dye = inv.getItem(SLOT_DYE);

        boolean validArmor = armorManager.isCustomArmor(armor);
        boolean validDye = isValidDye(dye);

        if (validArmor && validDye) {
            // Set indicators to GREEN
            for (int slot : INDICATOR_SLOTS) {
                inv.setItem(slot, GREEN_PANE);
            }
            // Set result
            inv.setItem(SLOT_RESULT, createPreview(armor, dye));
        } else {
            // Set indicators to RED
            for (int slot : INDICATOR_SLOTS) {
                inv.setItem(slot, RED_PANE);
            }
            // Set result to BARRIER
            inv.setItem(SLOT_RESULT, BARRIER);
        }
    }

    private boolean isValidDye(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(DYE_HEX_KEY, PersistentDataType.STRING);
    }

    private ItemStack createPreview(ItemStack armor, ItemStack dye) {
        ItemStack result = armor.clone();
        ItemMeta meta = result.getItemMeta();
        if (!(meta instanceof LeatherArmorMeta leatherMeta)) {
            // Should not happen if isCustomArmor is true, but good to check
            return BARRIER.clone();
        }

        String hex = dye.getItemMeta().getPersistentDataContainer().get(DYE_HEX_KEY, PersistentDataType.STRING);
        if (hex != null && hex.startsWith("#")) {
            try {
                int r = Integer.valueOf(hex.substring(1, 3), 16);
                int g = Integer.valueOf(hex.substring(3, 5), 16);
                int b = Integer.valueOf(hex.substring(5, 7), 16);
                leatherMeta.setColor(Color.fromRGB(r, g, b));
            } catch (Exception ignored) {}
        }

        // Add visual cue to lore
        List<Component> lore = leatherMeta.lore();
        if (lore != null) {
            // Remove old dye lore if present
            lore.removeIf(line -> PlainTextComponentSerializer.plainText().serialize(line).startsWith("Dyed with:"));

            // Add new dye lore
            lore.add(Component.empty().decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Dyed with: <white>" + hex).decoration(TextDecoration.ITALIC, false));
            leatherMeta.lore(lore);
        }

        result.setItemMeta(leatherMeta);
        return result;
    }

    private void handleCombine(Player player, Inventory inv) {
        // Check if the first indicator slot is green (means it's ready)
        // We can just check slot 11
        if (inv.getItem(11) != null && inv.getItem(11).getType() == Material.LIME_STAINED_GLASS_PANE) {
            ItemStack result = inv.getItem(SLOT_RESULT);
            if (result != null && result.getType() != Material.BARRIER) {
                // Success
                giveBack(player, result); // Give item
                inv.setItem(SLOT_ARMOR, null); // Consume armor

                // Consume dye (check stack size)
                ItemStack dye = inv.getItem(SLOT_DYE);
                if (dye.getAmount() > 1) {
                    dye.setAmount(dye.getAmount() - 1);
                    inv.setItem(SLOT_DYE, dye);
                } else {
                    inv.setItem(SLOT_DYE, null);
                }

                // Reset menu
                updateMenu(inv);

                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1f);
                player.sendMessage(MiniMessage.miniMessage().deserialize("<green>Armor successfully dyed!"));
            }
        } else {
            player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 1f, 1f);
            player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Invalid items! Need Custom Armor (Left) and Firesale Dye (Right)."));
        }
    }
}