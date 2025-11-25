package me.login.leaderboards;

import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class LeaderboardGUI implements Listener {

    private final Login plugin;
    private final StatsFetcher fetcher;
    private final LeaderboardDisplayManager displayManager;

    public LeaderboardGUI(Login plugin, StatsFetcher fetcher, LeaderboardDisplayManager displayManager) {
        this.plugin = plugin;
        this.fetcher = fetcher;
        this.displayManager = displayManager;
    }

    private static class LeaderboardHolder implements InventoryHolder {
        @Override public org.bukkit.inventory.Inventory getInventory() { return null; }
    }

    public void openMenu(Player player) {
        Inventory inv = Bukkit.createInventory(new LeaderboardHolder(), 45, Component.text("Leaderboards"));

        ItemStack glass = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 45; i++) inv.setItem(i, glass);

        // Row 2
        String tex12 = plugin.getConfig().getString("leaderboard_inventory.slot12", "");
        inv.setItem(12, createHead(tex12, "§e§lBalance Leaderboard", "§7Click to view top balances."));

        String tex13 = plugin.getConfig().getString("leaderboard_inventory.slot13", "");
        inv.setItem(13, createHead(tex13, "§c§lDeaths Leaderboard", "§7Click to view top deaths."));

        String tex14 = plugin.getConfig().getString("leaderboard_inventory.slot14", "");
        inv.setItem(14, createHead(tex14, "§a§lKills Leaderboard", "§7Click to view top kills."));

        // Row 3
        inv.setItem(21, createItem(Material.AMETHYST_SHARD, "§d§lTokens Leaderboard", "§7Click to view top tokens."));
        inv.setItem(22, createItem(Material.EMERALD, "§6§lCredits Leaderboard", "§7Click to view top credits."));
        inv.setItem(23, createItem(Material.CLOCK, "§b§lPlaytime Leaderboard", "§7Click to view top playtime."));

        // Row 4
        inv.setItem(30, createItem(Material.ZOMBIE_HEAD, "§2§lMob Kills Leaderboard", "§7Click to view top mob kills."));
        inv.setItem(31, createItem(Material.IRON_PICKAXE, "§f§lBlocks Broken Leaderboard", "§7Click to view top miners."));
        inv.setItem(32, createItem(Material.PAPER, "§7Coming Soon...", ""));

        player.openInventory(inv);
    }

    private void openLeaderboardView(Player player, String type) {
        Inventory inv = Bukkit.createInventory(new LeaderboardHolder(), 54, Component.text("Top 10: " + capitalize(type)));

        ItemStack glass = createItem(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) inv.setItem(i, glass);

        inv.setItem(49, createItem(Material.ARROW, "§cGo Back"));

        // Set Loading indicators
        ItemStack loading = createItem(Material.CLOCK, "§eLoading Data...", "§7Please wait.");
        int[] slots = {12, 13, 14, 21, 22, 23, 30, 31, 32, 40};
        for (int slot : slots) inv.setItem(slot, loading);

        player.openInventory(inv);

        // ASYNC FETCH
        CompletableFuture<Map<String, Double>> future;
        switch (type) {
            case "kills": future = fetcher.getTopStats(Statistic.PLAYER_KILLS, 10); break;
            case "deaths": future = fetcher.getTopStats(Statistic.DEATHS, 10); break;
            case "playtime": future = fetcher.getTopStats(Statistic.PLAY_ONE_MINUTE, 10); break;
            case "balance": future = fetcher.getTopBalances(10); break;
            case "tokens": future = fetcher.getTopTokens(10); break;
            case "credits": future = fetcher.getTopCredits(10); break;
            case "mobkills": future = fetcher.getTopMobKills(10); break;
            case "blocksbroken": future = fetcher.getTopBlocksBroken(10); break;
            default: future = CompletableFuture.completedFuture(new HashMap<>());
        }

        future.thenAccept(stats -> {
            // SWITCH BACK TO MAIN THREAD TO UPDATE INVENTORY
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().equals(inv)) {
                    int rank = 1;
                    for (Map.Entry<String, Double> entry : stats.entrySet()) {
                        if (rank > 10) break;

                        String name = entry.getKey();
                        double val = entry.getValue();
                        String displayVal = LeaderboardFormatter.formatNoDecimal(val);

                        if (type.equals("playtime")) displayVal = (long)(val / 20 / 3600) + "h";
                        if (type.equals("balance") || type.equals("credits")) displayVal = "$" + LeaderboardFormatter.formatSuffix(val);

                        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
                        SkullMeta meta = (SkullMeta) skull.getItemMeta();
                        meta.setOwner(name);
                        meta.setDisplayName("§6#" + rank + " §e" + name);
                        meta.setLore(Arrays.asList("§7Value: §f" + displayVal));
                        skull.setItemMeta(meta);

                        inv.setItem(slots[rank - 1], skull);
                        rank++;
                    }
                    // Clear remaining loading slots if less than 10 players
                    for (int i = stats.size(); i < 10; i++) {
                        inv.setItem(slots[i], createItem(Material.BARRIER, "§cNo Data"));
                    }
                }
            });
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof LeaderboardHolder) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null) return;
            Player p = (Player) e.getWhoClicked();
            int slot = e.getRawSlot();
            String title = e.getView().getTitle();

            if (title.equals("Leaderboards")) {
                switch (slot) {
                    case 12: openLeaderboardView(p, "balance"); break;
                    case 13: openLeaderboardView(p, "deaths"); break;
                    case 14: openLeaderboardView(p, "kills"); break;
                    case 21: openLeaderboardView(p, "tokens"); break;
                    case 22: openLeaderboardView(p, "credits"); break;
                    case 23: openLeaderboardView(p, "playtime"); break;
                    case 30: openLeaderboardView(p, "mobkills"); break;
                    case 31: openLeaderboardView(p, "blocksbroken"); break;
                }
            } else if (title.startsWith("Top 10:")) {
                if (slot == 49) openMenu(p);
            }
        }
    }

    private ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> l = new ArrayList<>();
            for (String s : lore) if(!s.isEmpty()) l.add(Component.text(s).color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(l);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createHead(String texture, String name, String... lore) {
        ItemStack item;
        if (texture != null && !texture.isEmpty()) {
            item = TextureToHead.getHead(texture);
        } else {
            item = new ItemStack(Material.PLAYER_HEAD);
        }
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        if (lore.length > 0) {
            meta.setLore(Arrays.asList(lore));
        }
        item.setItemMeta(meta);
        return item;
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}