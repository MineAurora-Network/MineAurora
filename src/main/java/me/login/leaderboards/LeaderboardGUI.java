package me.login.leaderboards;

import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class LeaderboardGUI implements Listener {

    private final Login plugin;
    private final StatsFetcher fetcher;
    private final MiniMessage miniMessage;

    public LeaderboardGUI(Login plugin) {
        this.plugin = plugin;
        this.fetcher = new StatsFetcher(plugin);
        this.miniMessage = MiniMessage.miniMessage();
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 45, Component.text("Leaderboards"));
        FileConfiguration config = plugin.getConfig();

        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta gMeta = glass.getItemMeta();
        gMeta.setDisplayName(" ");
        glass.setItemMeta(gMeta);

        for (int i = 0; i < 45; i++) {
            inv.setItem(i, glass);
        }

        // --- Row 2: Main Stats (Heads) ---

        // Slot 12: Balance
        String balanceUrl = config.getString("leaderboard_inventory.slot12");
        setItem(inv, 12, balanceUrl, Material.PAPER, "§e§lBalance Leaderboard", "§7Click to view top balances.");

        // Slot 13: Deaths
        String deathsUrl = config.getString("leaderboard_inventory.slot13");
        setItem(inv, 13, deathsUrl, Material.SKELETON_SKULL, "§c§lDeaths Leaderboard", "§7Click to view top deaths.");

        // Slot 14: Kills
        String killsUrl = config.getString("leaderboard_inventory.slot14");
        setItem(inv, 14, killsUrl, Material.DIAMOND_SWORD, "§a§lKills Leaderboard", "§7Click to view top kills.");


        // --- Row 3: Misc Stats (Items) ---

        // Slot 21: Tokens
        setItem(inv, 21, null, Material.AMETHYST_SHARD, "§d§lTokens Leaderboard", "§7Click to view top tokens.");

        // Slot 22: Credits
        setItem(inv, 22, null, Material.SUNFLOWER, "§6§lCredits Leaderboard", "§7Click to view top credits.");

        // Slot 23: Playtime
        setItem(inv, 23, null, Material.CLOCK, "§b§lPlaytime Leaderboard", "§7Click to view top playtime.");

        // Slots 30, 31, 32 are intentionally left as Gray Glass

        player.openInventory(inv);
    }

    private void setItem(Inventory inv, int slot, String headUrl, Material fallback, String name, String... lore) {
        ItemStack item;
        if (headUrl != null && !headUrl.isEmpty()) {
            item = TextureToHead.getHead(headUrl);
        } else {
            item = new ItemStack(fallback);
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        inv.setItem(slot, item);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!e.getView().getTitle().equals("Leaderboards")) return;
        e.setCancelled(true);

        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        int slot = e.getRawSlot();

        String type = null;
        switch (slot) {
            case 12: type = "balance"; break;
            case 13: type = "deaths"; break;
            case 14: type = "kills"; break;
            case 21: type = "tokens"; break;
            case 22: type = "credits"; break;
            case 23: type = "playtime"; break;
            default: return;
        }

        p.closeInventory();
        sendLeaderboardChat(p, type, 1);
    }

    public void sendLeaderboardChat(Player player, String type, int page) {
        int pageSize = 10;
        Map<String, Double> stats;

        // Using lower limit for chat to avoid lag, can increase if needed
        switch (type.toLowerCase()) {
            case "balance": stats = fetcher.getTopBalances(100); break;
            case "credits": stats = fetcher.getTopCredits(100); break;
            case "token": case "tokens": stats = fetcher.getTopTokens(100); break; // Support both
            case "kills": stats = fetcher.getTopStats(Statistic.PLAYER_KILLS, 100); break;
            case "deaths": stats = fetcher.getTopStats(Statistic.DEATHS, 100); break;
            case "playtime": stats = fetcher.getTopStats(Statistic.PLAY_ONE_MINUTE, 100); break;
            case "parkour": stats = fetcher.getTopParkour(100); break;
            default:
                player.sendMessage("§cLeaderboard type '" + type + "' not implemented yet.");
                return;
        }

        List<Map.Entry<String, Double>> list = new ArrayList<>(stats.entrySet());
        int totalPages = (int) Math.ceil((double) list.size() / pageSize);
        if (list.isEmpty()) totalPages = 1;

        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        Audience audience = (Audience) player;
        String prefixRaw = plugin.getConfig().getString("server_prefix", "<gradient:blue:aqua>[MineAurora]</gradient> ");

        Component header = miniMessage.deserialize(prefixRaw + " <yellow>Top " + capitalize(type) + " [Page " + page + "/" + totalPages + "]");
        audience.sendMessage(header);

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, list.size());

        if (list.isEmpty()) {
            audience.sendMessage(miniMessage.deserialize("<gray>No data available for this leaderboard.</gray>"));
        } else {
            for (int i = start; i < end; i++) {
                Map.Entry<String, Double> entry = list.get(i);
                String value;
                String smallValue = LeaderboardFormatter.formatSuffix(entry.getValue());

                if (type.equalsIgnoreCase("playtime")) {
                    long hours = (long) (entry.getValue() / 20 / 3600);
                    value = String.valueOf(hours) + "h";
                    smallValue = value;
                } else if (type.equalsIgnoreCase("balance") || type.equalsIgnoreCase("credits")) {
                    // As requested: Value in chat uses the short format (100.15M)
                    value = smallValue;
                } else {
                    value = LeaderboardFormatter.formatNoDecimal(entry.getValue());
                }

                // #1. Player | Value
                String line = "<white>" + (i + 1) + ". <green>" + entry.getKey() + " <gray>| <yellow>" + value;
                audience.sendMessage(miniMessage.deserialize(line));
            }
        }

        // Navigation Buttons
        // These use a hidden command `_navigate` to handle the pagination click
        Component nav = Component.text("");

        if (page > 1) {
            nav = nav.append(Component.text("Previous Page", NamedTextColor.RED)
                    .clickEvent(ClickEvent.runCommand("/leaderboard _navigate " + type + " " + (page - 1)))
                    .hoverEvent(Component.text("Go to page " + (page - 1))));
        } else {
            nav = nav.append(Component.text("Previous Page", NamedTextColor.GRAY));
        }

        nav = nav.append(Component.text(" || ", NamedTextColor.DARK_GRAY));

        if (page < totalPages) {
            nav = nav.append(Component.text("Next Page", NamedTextColor.GREEN)
                    .clickEvent(ClickEvent.runCommand("/leaderboard _navigate " + type + " " + (page + 1)))
                    .hoverEvent(Component.text("Go to page " + (page + 1))));
        } else {
            nav = nav.append(Component.text("Next Page", NamedTextColor.GRAY));
        }

        audience.sendMessage(nav);
    }

    private String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}