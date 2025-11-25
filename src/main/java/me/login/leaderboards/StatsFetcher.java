package me.login.leaderboards;

import me.login.Login;
import me.login.loginsystem.LoginModule;
import me.login.misc.tokens.TokenModule;
import me.login.premimumfeatures.credits.CreditsModule;
import me.login.scoreboard.SkriptUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class StatsFetcher {

    private final Login plugin;
    private final TokenModule tokenModule;
    private final CreditsModule creditsModule;

    public StatsFetcher(Login plugin, TokenModule tokenModule, CreditsModule creditsModule) {
        this.plugin = plugin;
        this.tokenModule = tokenModule;
        this.creditsModule = creditsModule;
    }

    // --- GENERIC STATS (Async) ---
    public CompletableFuture<Map<String, Double>> getTopStats(Statistic statistic, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;
                if (!LeaderboardModule.isShowOps() && player.isOp()) continue;
                try {
                    int statValue = player.getStatistic(statistic);
                    if (statValue > 0) statsMap.put(player.getName(), (double) statValue);
                } catch (Exception ignored) {
                    // Start/stats might not exist for player
                }
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- MOB KILLS (Async) ---
    public CompletableFuture<Map<String, Double>> getTopMobKills(int limit) {
        return getTopStats(Statistic.MOB_KILLS, limit);
    }

    // --- BLOCKS BROKEN (Async - Heavy!) ---
    public CompletableFuture<Map<String, Double>> getTopBlocksBroken(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;
                if (!LeaderboardModule.isShowOps() && player.isOp()) continue;

                long totalBroken = 0;
                for (Material mat : Material.values()) {
                    if (mat.isBlock()) {
                        try {
                            totalBroken += player.getStatistic(Statistic.MINE_BLOCK, mat);
                        } catch (Exception ignored) {}
                    }
                }

                if (totalBroken > 0) statsMap.put(player.getName(), (double) totalBroken);
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- VAULT ECONOMY (Async) ---
    public CompletableFuture<Map<String, Double>> getTopBalances(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            if (!plugin.getServer().getPluginManager().isPluginEnabled("Vault")) return statsMap;

            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp == null) return statsMap;
            Economy economy = rsp.getProvider();

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;
                if (!LeaderboardModule.isShowOps() && player.isOp()) continue;
                try {
                    if (economy.hasAccount(player)) {
                        double balance = economy.getBalance(player);
                        if (balance > 0) statsMap.put(player.getName(), balance);
                    }
                } catch (Exception ignored) {}
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- TOKENS (Async SQL) ---
    public CompletableFuture<Map<String, Double>> getTopTokens(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            if (tokenModule == null || tokenModule.getTokenDatabase() == null) return statsMap;

            try (PreparedStatement ps = tokenModule.getTokenDatabase().getConnection().prepareStatement(
                    "SELECT player_uuid, token_balance FROM player_tokens ORDER BY token_balance DESC LIMIT ?")) {
                ps.setInt(1, limit * 2);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    long balance = rs.getLong("token_balance");
                    try {
                        OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                        if (p.getName() != null) {
                            if (!LeaderboardModule.isShowOps() && p.isOp()) continue;
                            statsMap.put(p.getName(), (double) balance);
                        }
                    } catch (IllegalArgumentException ignored) {}
                    if (statsMap.size() >= limit) break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error fetching top tokens: " + e.getMessage());
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- CREDITS (Async SQL) ---
    public CompletableFuture<Map<String, Double>> getTopCredits(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            File dbFile = new File(plugin.getDataFolder(), "database/credits.db");
            if (!dbFile.exists()) return statsMap;

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement ps = conn.prepareStatement("SELECT uuid, amount FROM player_credits ORDER BY amount DESC LIMIT ?")) {
                ps.setInt(1, limit * 2);
                ResultSet rs = ps.executeQuery();
                while (rs.next()) {
                    String uuidStr = rs.getString("uuid");
                    double amount = rs.getDouble("amount");
                    try {
                        OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                        if (p.getName() != null) {
                            if (!LeaderboardModule.isShowOps() && p.isOp()) continue;
                            statsMap.put(p.getName(), amount);
                        }
                    } catch (IllegalArgumentException ignored) {}
                    if (statsMap.size() >= limit) break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error fetching top credits: " + e.getMessage());
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- SKRIPT VARS (Async) ---
    // Warning: Accessing Skript vars async might be risky depending on Skript implementation, but usually reading variables is fine.
    public CompletableFuture<Map<String, Double>> getTopSkriptVar(String varPattern, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) return statsMap;

            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;
                if (!LeaderboardModule.isShowOps() && player.isOp()) continue;
                String varName = varPattern.replace("%uuid%", player.getUniqueId().toString());
                try {
                    Object varValue = SkriptUtils.getVar(varName);
                    if (varValue instanceof Number) {
                        double value = ((Number) varValue).doubleValue();
                        if (value > 0) statsMap.put(player.getName(), value);
                    }
                } catch (Exception ignored) {}
            }
            return sortByValue(statsMap, limit);
        });
    }

    // --- PARKOUR (Async SQL) ---
    public CompletableFuture<Map<String, Double>> getTopParkour(int limit) {
        return CompletableFuture.supplyAsync(() -> {
            LoginModule loginModule = plugin.getLoginModule();
            if (loginModule != null && loginModule.getLoginDatabase() != null) {
                return loginModule.getLoginDatabase().getTopParkourCompletions(limit);
            }
            return new HashMap<>();
        });
    }

    private Map<String, Double> sortByValue(Map<String, Double> unsortedMap, int limit) {
        return unsortedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
    }
}