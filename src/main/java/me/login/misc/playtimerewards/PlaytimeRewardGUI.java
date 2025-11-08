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
    private static final int GUI_SIZE = 54;
    private static final int LEVELS_PER_PAGE = 21; // 3 rows of 7
    private static final int[] LEVEL_SLOTS = {
            10, 11, 12, 13, 14, 15, 16, // Row 2
            19, 20, 21, 22, 23, 24, 25, // Row 3
            28, 29, 30, 31, 32, 33, 34  // Row 4
    };

    // Swapped to concrete for stability
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
        this.mm = manager.getMiniMessage(); // Get MiniMessage from manager
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

        // --- Fill Rainbow Decoration ---
        for (int i = 0; i < 7; i++) {
            gui.setItem(i, createDecorationItem(RAINBOW_MATERIALS.get(i), Component.empty())); // L87
        }
        for (int i = 47; i < 54; i++) {
            gui.setItem(i, createDecorationItem(RAINBOW_MATERIALS.get(i - 47), Component.empty())); // L90
        }

        // --- Navigation & Info ---
        if (page > 0) {
            gui.setItem(36, createNavItem(Material.RED_CANDLE, "<red>Previous Page</red>", page));
        }
        gui.setItem(40, createPlayerInfoItem(player, data)); // Player head - L95
        if (page < TOTAL_PAGES - 1) {
            gui.setItem(44, createNavItem(Material.GREEN_CANDLE, "<green>Next Page</green>", page)); // L99
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

        if (level.level() <= data.lastClaimedLevel()) {
            // --- CLAIMED ---
            mat = Material.GRAY_DYE;
            name = mm.deserialize("<strikethrough><gray>Level " + level.level() + "</strikethrough></gray>");
            lore.add(mm.deserialize("<green>REWARD CLAIMED</green>"));

        } else if (level.level() == data.lastClaimedLevel() + 1 && data.totalPlaytimeSeconds() >= level.timeRequiredSeconds()) {
            // --- CLAIMABLE ---
            mat = Material.LIME_DYE;
            name = mm.deserialize("<green><bold>Level " + level.level() + "</bold> - Click to Claim!</green>");
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gray>+ <white>" + level.coinReward() + " Coins</white>"));
            lore.add(mm.deserialize("<gray>+ <white>" + level.tokenReward() + " Tokens</white>"));
            glow = true;

        } else {
            // --- LOCKED ---
            mat = Material.RED_DYE;
            name = mm.deserialize("<red>Level " + level.level() + " - LOCKED</red>");
            lore.add(Component.empty());
            lore.add(mm.deserialize("<gray>Requires: <white>" + manager.formatPlaytime(level.timeRequiredSeconds()) + "</white>"));
            long remaining = level.timeRequiredSeconds() - data.totalPlaytimeSeconds();
            if (remaining > 0) {
                lore.add(mm.deserialize("<gray>Time Left: <white>" + manager.formatPlaytime(remaining) + "</white>"));
            }
        }

        ItemBuilder builder = new ItemBuilder(mat).displayName(name).lore(lore);

        if (glow) {
            builder.glow();
        }

        // Add PDC tag
        builder.pdc(levelKey, PersistentDataType.INTEGER, level.level());

        return builder.build();
    }

    // ---
    // --- HELPER METHODS RE-IMPLEMENTED ---
    // ---

    /**
     * Creates the player info head item.
     */
    private ItemStack createPlayerInfoItem(Player player, PlaytimeRewardDatabase.PlayerPlaytimeData data) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta == null) return head; // Failsafe

        meta.setOwningPlayer(player); // This is correct, Player is an OfflinePlayer
        meta.displayName(mm.deserialize("<aqua><bold>Your Statistics</bold></aqua>").decoration(TextDecoration.ITALIC, false));

        // This is where L195 error was
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>INFORMATION:</gray>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>PLAYTIME LEVELS ARE ACHIEVABLE TIERS WHERE</white>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>YOU CAN GET REWARDS BY JUST PLAYING! (OR AFKING)</white>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>THE MORE YOU PLAY THE MORE IN-GAME REWARDS</white>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>YOU WILL RECEIVE!</white>"));
        lore.add(Component.empty());
        lore.add(mm.deserialize("<gray>STATISTICS:</gray>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>LEVEL: <aqua>" + data.lastClaimedLevel() + "</aqua></white>"));
        lore.add(mm.deserialize("<dark_gray>┃ <white>PLAYTIME: <aqua>" + manager.formatPlaytime(data.totalPlaytimeSeconds()) + "</aqua></white>"));

        List<Component> finalLore = new ArrayList<>();
        for (Component line : lore) {
            finalLore.add(line.decoration(TextDecoration.ITALIC, false));
        }
        meta.lore(finalLore);

        head.setItemMeta(meta);
        return head;
    }

    /**
     * Creates a navigation item (e.g., next/prev page).
     */
    private ItemStack createNavItem(Material mat, String name, int currentPage) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item; // Failsafe

        // FIX: Deserialize the String 'name'
        meta.displayName(mm.deserialize(name).decoration(TextDecoration.ITALIC, false));
        // FIX: This is where L223 errors were
        meta.getPersistentDataContainer().set(pageKey, PersistentDataType.INTEGER, currentPage);
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Creates a decorative item (like the concrete border).
     */
    private ItemStack createDecorationItem(Material material, Component name) {
        // FIX: This is where L239 errors were. Use ItemBuilder.
        return new ItemBuilder(material)
                .displayName(name)
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

        if (pdc.has(levelKey, PersistentDataType.INTEGER)) {
            // Clicked on a reward
            Integer level = pdc.get(levelKey, PersistentDataType.INTEGER);
            if (level != null) {
                manager.claimReward(player, level);
                // Re-open GUI to show new state
                int currentPage = player.getMetadata(GUI_METADATA).getFirst().asInt();
                openGUI(player, currentPage);
            }

        } else if (pdc.has(pageKey, PersistentDataType.INTEGER)) {
            // Clicked on navigation
            Integer currentPage = pdc.get(pageKey, PersistentDataType.INTEGER);
            if (currentPage != null) {
                if (clickedItem.getType() == Material.GREEN_CANDLE) {
                    openGUI(player, currentPage + 1);
                } else if (clickedItem.getType() == Material.RED_CANDLE) {
                    openGUI(player, currentPage - 1);
                }
            }
        }
    }
}