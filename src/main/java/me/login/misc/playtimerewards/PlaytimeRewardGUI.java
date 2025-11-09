package me.login.misc.playtimerewards;

import me.login.Login;
import net.kyori.adventure.text.Component;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class PlaytimeRewardGUI implements Listener {

    private final Login plugin;
    private final PlaytimeRewardManager manager;
    private final MiniMessage mm;
    private final int TOTAL_PAGES;

    private final NamespacedKey levelKey;
    private final NamespacedKey pageKey;

    private static final String GUI_METADATA = "PlaytimeRewardsGUI";
    private static final int GUI_SIZE = 54; // 6 rows
    private static final int LEVELS_PER_PAGE = 21; // 3 rows of 7
    // Slots for rewards:
    // Row 2: 10-16
    // Row 3: 19-25
    // Row 4: 28-34
    private static final int[] LEVEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, // Row 2
            19, 20, 21, 22, 23, 24, 25, // Row 3
            28, 29, 30, 31, 32, 33, 34  // Row 4
    };

    private static final int PREVIOUS_PAGE_SLOT = 39;
    private static final int PLAYER_INFO_SLOT = 40;
    private static final int NEXT_PAGE_SLOT = 41;

    // Materials for the 7 reward items in a row
    private final Map<Integer, Material> RAINBOW_MATERIALS = Map.of(
            0, Material.RED_CONCRETE,
            1, Material.ORANGE_CONCRETE,
            2, Material.YELLOW_CONCRETE,
            3, Material.LIME_CONCRETE,
            4, Material.CYAN_CONCRETE,
            5, Material.PURPLE_CONCRETE,
            6, Material.PINK_CONCRETE
    );

    public PlaytimeRewardGUI(Login plugin, PlaytimeRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        this.mm = manager.getMiniMessage();
        this.levelKey = new NamespacedKey(plugin, "ptreward_level");
        this.pageKey = new NamespacedKey(plugin, "ptreward_page");

        // Calculate total pages dynamically
        this.TOTAL_PAGES = (int) Math.ceil((double) PlaytimeRewardManager.MAX_LEVEL / LEVELS_PER_PAGE);
    }

    public void openGUI(Player player, int page) {
        if (page < 0) page = 0;
        if (page >= TOTAL_PAGES) page = TOTAL_PAGES - 1;

        Component title = mm.deserialize("<dark_gray>Playtime Levels [" + (page + 1) + "/" + TOTAL_PAGES + "]</dark_gray>");
        Inventory gui = Bukkit.createInventory(null, GUI_SIZE, title);

        // --- Get Player Data ---
        Component serverPrefix = manager.getPrefix();
        PlaytimeRewardDatabase.PlayerPlaytimeData data = manager.getPlayerData(player.getUniqueId());
        if (data == null) {
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>Your playtime data is not loaded yet. Please wait a moment and try again.</red>")));
            return;
        }

        // --- Fill all slots with filler glass first ---
        ItemStack filler = createDecorationItem(Material.GRAY_STAINED_GLASS_PANE, Component.empty());
        for (int i = 0; i < GUI_SIZE; i++) {
            gui.setItem(i, filler);
        }

        // --- Navigation & Info ---
        // Always display previous page button, but gray it out if on page 0
        gui.setItem(PREVIOUS_PAGE_SLOT, createNavItem(Material.RED_CANDLE, "<red>Previous Page</red>", page, page > 0));

        gui.setItem(PLAYER_INFO_SLOT, createPlayerInfoItem(player, data)); // Player head
        if (page < TOTAL_PAGES - 1) {
            gui.setItem(NEXT_PAGE_SLOT, createNavItem(Material.GREEN_CANDLE, "<green>Next Page</green>", page, true));
        } else {
            // If on the last page, put a grayed-out "next page" indicator
            gui.setItem(NEXT_PAGE_SLOT, createNavItem(Material.GRAY_CANDLE, "<gray>Next Page</gray>", page, false));
        }


        // --- Fill Level Items ---
        int levelStart = (page * LEVELS_PER_PAGE) + 1;

        for (int i = 0; i < LEVEL_SLOTS.length; i++) {
            int currentLevel = levelStart + i;
            if (currentLevel > PlaytimeRewardManager.MAX_LEVEL) break;

            PlaytimeRewardLevel levelInfo = manager.getLevelInfo(currentLevel);
            if (levelInfo == null) continue;

            gui.setItem(LEVEL_SLOTS[i], createLevelItem(levelInfo, data));
        }

        player.setMetadata(GUI_METADATA, new FixedMetadataValue(plugin, page));
        player.openInventory(gui);
    }

    private ItemStack createLevelItem(PlaytimeRewardLevel level, PlaytimeRewardDatabase.PlayerPlaytimeData data) {
        Material mat;
        Component name;
        List<Component> lore = new ArrayList<>();
        boolean glow = false;

        int materialIndex = (level.level() - 1) % 7;
        Material rainbowMat = RAINBOW_MATERIALS.getOrDefault(materialIndex, Material.STONE);

        if (level.level() <= data.lastClaimedLevel()) {
            // --- CLAIMED ---
            mat = Material.GRAY_CONCRETE;
            // FIX: Corrected claimed level name and added gap
            name = mm.deserialize("<strikethrough><gray>Level " + level.level() + "</gray></strikethrough>").decoration(TextDecoration.ITALIC, false);
            lore.add(Component.empty()); // Gap
            lore.add(mm.deserialize("<green>REWARD CLAIMED</green>").decoration(TextDecoration.ITALIC, false));

        } else {
            // --- NOT CLAIMED (LOCKED OR CLAIMABLE) ---
            mat = rainbowMat;

            if (level.level() == data.lastClaimedLevel() + 1 && data.totalPlaytimeSeconds() >= level.timeRequiredSeconds()) {
                // --- CLAIMABLE ---
                name = mm.deserialize("<green><bold>Level " + level.level() + "</bold> - Click to Claim!</green>").decoration(TextDecoration.ITALIC, false);
                lore.add(Component.empty());
                lore.add(mm.deserialize("<gray>+ <white>" + level.coinReward() + " Coins</white>").decoration(TextDecoration.ITALIC, false));
                lore.add(mm.deserialize("<gray>+ <white>" + level.tokenReward() + " Tokens</white>").decoration(TextDecoration.ITALIC, false));
                glow = true;

            } else {
                // --- LOCKED ---
                // THIS IS THE FIX: Removed "DRAFT:" from the string
                name = mm.deserialize("<red>Level " + level.level() + " - LOCKED</red>").decoration(TextDecoration.ITALIC, false);
                lore.add(Component.empty());
                lore.add(mm.deserialize("<gray>Requires: <white>" + manager.formatPlaytime(level.timeRequiredSeconds()) + "</white>").decoration(TextDecoration.ITALIC, false));
                long remaining = level.timeRequiredSeconds() - data.totalPlaytimeSeconds();
                if (remaining > 0) {
                    lore.add(mm.deserialize("<gray>Time Left: <white>" + manager.formatPlaytime(remaining) + "</white>").decoration(TextDecoration.ITALIC, false));
                }
            }
        }

        ItemBuilder builder = new ItemBuilder(mat).displayName(name).lore(lore);

        if (glow) {
            builder.glow();
        }

        builder.pdc(levelKey, PersistentDataType.INTEGER, level.level());

        return builder.build();
    }

    /**
     * Creates the player info head item.
     */
    private ItemStack createPlayerInfoItem(Player player, PlaytimeRewardDatabase.PlayerPlaytimeData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head;

        meta.setOwningPlayer(player);
        meta.displayName(mm.deserialize("<aqua><bold>Your Statistics</bold></aqua>").decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>INFORMATION:</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>PLAYTIME LEVELS ARE ACHIEVABLE TIERS WHERE</white>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>YOU CAN GET REWARDS BY JUST PLAYING! (OR AFKING)</white>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>THE MORE YOU PLAY THE MORE IN-GAME REWARDS</white>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>YOU WILL RECEIVE!</white>").decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>STATISTICS:</gray>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>LEVEL: <aqua>" + data.lastClaimedLevel() + "</aqua></white>").decoration(TextDecoration.ITALIC, false));
        lore.add(mm.deserialize("<dark_gray>┃ <white>PLAYTIME: <aqua>" + manager.formatPlaytime(data.totalPlaytimeSeconds()) + "</aqua></white>").decoration(TextDecoration.ITALIC, false));

        meta.lore(lore); // Fixed: assign the modified lore directly
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Creates a navigation item (e.g., next/prev page).
     * @param enabled If true, it's a normal candle; if false, it's a gray candle with gray text.
     */
    private ItemStack createNavItem(Material mat, String name, int currentPage, boolean enabled) {
        ItemStack item = new ItemStack(enabled ? mat : Material.GRAY_CANDLE); // Use gray candle if not enabled
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        Component displayName = mm.deserialize(name).decoration(TextDecoration.ITALIC, false);
        if (!enabled) {
            displayName = mm.deserialize("<gray>" + mm.serialize(displayName) + "</gray>").decoration(TextDecoration.ITALIC, false);
        }
        meta.displayName(displayName);
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a decorative item (like the concrete border).
     */
    private ItemStack createDecorationItem(Material material, Component name) {
        return new ItemBuilder(material)
                .displayName(name.decoration(TextDecoration.ITALIC, false)) // Also remove italics
                .build();
    }


    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!player.hasMetadata(GUI_METADATA)) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(player.getOpenInventory().getTopInventory())) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int slot = event.getSlot();

        if (pdc.has(levelKey, PersistentDataType.INTEGER)) {
            // Clicked on a reward
            Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
            if (level != null) {
                manager.claimReward(player, level);
                // Re-open GUI to show new state
                int currentPage = player.getMetadata(GUI_METADATA).getFirst().asInt();
                openGUI(player, currentPage); // FIX: Ensure current page is passed
            }

        } else if (pdc.has(pageKey, PersistentDataType.INTEGER)) {
            // Clicked on navigation
            Integer currentPage = pdc.get(pageKey, PersistentDataType.INTEGER);
            if (currentPage != null) {
                if (slot == NEXT_PAGE_SLOT) {
                    openGUI(player, currentPage + 1);
                } else if (slot == PREVIOUS_PAGE_SLOT) {
                    // Only navigate if not on the first page
                    if (currentPage > 0) {
                        openGUI(player, currentPage - 1);
                    } else {
                        // For showcase, do nothing if trying to go back from page 0
                        // player.sendMessage(manager.getPrefix().append(mm.deserialize("<red>You are already on the first page!</red>"))); // Commented out for less spam
                    }
                }
            }
        }
    }
}