package me.login.lifesteal;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class ReviveMenu implements Listener {

    private final Login plugin;
    private final ItemManager itemManager;
    private final DeadPlayerManager deadPlayerManager;
    private final LifestealLogger logger; // <-- ADDED FIELD

    private static final int GUI_SIZE = 54; // 6 rows
    private static final int PLAYERS_PER_PAGE = 45; // 5 rows
    private static final String GUI_METADATA = "ReviveMenu";
    private static final String GUI_PAGE_META = "ReviveMenuPage";
    private static final String GUI_FILTER_META = "ReviveMenuFilter";

    // --- CONSTRUCTOR UPDATED ---
    public ReviveMenu(Login plugin, ItemManager itemManager, DeadPlayerManager deadPlayerManager, LifestealLogger logger) {
        this.plugin = plugin;
        this.itemManager = itemManager;
        this.deadPlayerManager = deadPlayerManager;
        this.logger = logger; // <-- STORE LOGGER
        // plugin.getServer().getPluginManager().registerEvents(this, plugin); // This is now handled by LifestealModule
    }

    public void openMenu(Player player, int page, String filter) {
        Map<UUID, String> allDeadPlayers = deadPlayerManager.getDeadPlayers();
        List<Map.Entry<UUID, String>> filteredPlayers;

        if (filter != null && !filter.isEmpty()) {
            filteredPlayers = allDeadPlayers.entrySet().stream()
                    .filter(entry -> entry.getValue().toLowerCase().contains(filter.toLowerCase()))
                    .collect(Collectors.toList());
        } else {
            filteredPlayers = new ArrayList<>(allDeadPlayers.entrySet());
        }

        int totalPages = (int) Math.ceil((double) filteredPlayers.size() / PLAYERS_PER_PAGE);
        if (totalPages == 0) totalPages = 1;
        if (page < 0) page = 0;
        if (page >= totalPages) page = totalPages - 1;

        String title = "Revive Player (Page " + (page + 1) + "/" + totalPages + ")";
        if (filter != null) title += " | Searching: " + filter;

        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, ItemManager.toLegacy(itemManager.getMiniMessage().deserialize(title)));

        // --- Fill Player Heads ---
        int startIndex = page * PLAYERS_PER_PAGE;
        for (int i = 0; i < PLAYERS_PER_PAGE; i++) {
            int playerIndex = startIndex + i;
            if (playerIndex >= filteredPlayers.size()) break; // No more players

            Map.Entry<UUID, String> entry = filteredPlayers.get(playerIndex);
            gui.setItem(i, createPlayerHead(entry.getKey(), entry.getValue()));
        }

        // --- Fill Navigation Bar (6th row) ---
        ItemStack grayPane = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, grayPane);
        }

        // Previous Page (Slot 45)
        if (page > 0) {
            gui.setItem(45, createGuiItem(Material.ARROW, "<yellow>Previous Page</yellow>", null));
        }

        // Search (Slot 48)
        gui.setItem(48, createGuiItem(Material.OAK_SIGN, "<green>Search Player</green>", "<gray>Click to search for a player name."));

        // Close (Slot 49)
        gui.setItem(49, createGuiItem(Material.BARRIER, "<red>Close Menu</red>", null));

        // Next Page (Slot 53)
        if ((page + 1) < totalPages) {
            gui.setItem(53, createGuiItem(Material.ARROW, "<yellow>Next Page</yellow>", null));
        }

        // Set metadata to identify this GUI
        player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, true));
        player.setMetadata(GUI_PAGE_META, new FixedMetadataValue(plugin, page));
        player.setMetadata(GUI_FILTER_META, new FixedMetadataValue(plugin, filter)); // Store filter
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(GUI_METADATA)) return;

        event.setCancelled(true); // Cancel all clicks
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            return;
        }

        int currentPage = player.getMetadata(GUI_PAGE_META).getFirst().asInt();
        String currentFilter = player.getMetadata(GUI_FILTER_META).getFirst().asString();

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        int slot = event.getSlot();

        // --- Handle Navigation ---
        if (slot >= 45) {
            Material type = clickedItem.getType();
            if (type == Material.ARROW) {
                if (slot == 45) { // Previous
                    openMenu(player, currentPage - 1, currentFilter);
                } else if (slot == 53) { // Next
                    openMenu(player, currentPage + 1, currentFilter);
                }
            } else if (type == Material.OAK_SIGN && slot == 48) { // Search
                openSearchSign(player, currentPage, currentFilter);
            } else if (type == Material.BARRIER && slot == 49) { // Close
                player.closeInventory();
            }
            return;
        }

        // --- Handle Player Click ---
        if (clickedItem.getType() == Material.PLAYER_HEAD) {
            SkullMeta meta = (SkullMeta) clickedItem.getItemMeta();
            if (meta == null || meta.getOwningPlayer() == null) return;

            OfflinePlayer target = meta.getOwningPlayer();
            UUID targetUUID = target.getUniqueId();

            // Revive the player
            deadPlayerManager.removeDeadPlayer(targetUUID);

            // Give back the beacon
            player.getInventory().addItem(itemManager.getReviveBeaconItem(1));

            player.sendMessage(itemManager.formatMessage("<green>You have revived " + target.getName() + "!"));

            // --- LOGGING UPDATED ---
            if (logger != null) {
                logger.logAdmin("`" + player.getName() + "` revived `" + target.getName() + "` using a Revive Beacon.");
            }
            // --- END LOGGING ---

            // Refresh menu
            openMenu(player, currentPage, currentFilter);
        }
    }

    private void openSearchSign(Player player, int page, String filter) {
        player.closeInventory(); // Close GUI first

        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "Enter Player Name", "Or 'clear' to reset")
                    .setHandler((p, result) -> {
                        String input = result.getLine(0).trim();

                        final String newFilter;
                        if (input.equalsIgnoreCase("clear") || input.isEmpty()) {
                            newFilter = null;
                        } else {
                            newFilter = input;
                        }

                        // Re-open the menu with the new filter
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            openMenu(p, 0, newFilter); // Go back to page 0
                        });

                        return null; // Close Sign GUI
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error opening SignGUI for ReviveMenu", e);
            player.sendMessage(itemManager.formatMessage("<red>An error occurred opening the search prompt."));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (player.hasMetadata(GUI_METADATA)) {
            player.removeMetadata(GUI_METADATA, plugin);
            player.removeMetadata(GUI_PAGE_META, plugin);
            player.removeMetadata(GUI_FILTER_META, plugin);
        }
    }

    private ItemStack createPlayerHead(UUID uuid, String name) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();

        meta.setOwningPlayer(Bukkit.getOfflinePlayer(uuid));
        meta.displayName(Component.text(name, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("UUID: ", NamedTextColor.GRAY).append(Component.text(uuid.toString(), NamedTextColor.DARK_GRAY)).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Click to Revive!", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));

        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack createGuiItem(Material material, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        meta.displayName(itemManager.getMiniMessage().deserialize(name).decoration(TextDecoration.ITALIC, false));

        if (lore != null) {
            meta.lore(Collections.singletonList(
                    itemManager.getMiniMessage().deserialize(lore).decoration(TextDecoration.ITALIC, false)
            ));
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}