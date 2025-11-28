package me.login.leaderboards;

import me.login.Login;
import me.login.misc.tokens.TokenModule;
import me.login.premiumfeatures.credits.CreditsModule;
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

            // We verify the module is active, but we connect to the FILE directly for safety in async threads
            if (tokenModule == null) return statsMap;

            File dbFile = new File(plugin.getDataFolder(), "database/tokens.db");
            if (!dbFile.exists()) return statsMap;

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            // Query matches your TokenDatabase: table 'player_tokens', column 'tokens'
            String query = "SELECT player_uuid, tokens FROM player_tokens ORDER BY tokens DESC LIMIT ?";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement ps = conn.prepareStatement(query)) {

                ps.setInt(1, limit * 2); // Fetch extra in case of OPs we need to filter
                ResultSet rs = ps.executeQuery();

                while (rs.next()) {
                    String uuidStr = rs.getString("player_uuid");
                    long balance = rs.getLong("tokens");
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

            if (creditsModule == null) return statsMap;

            File dbFile = new File(plugin.getDataFolder(), "database/credits.db");
            if (!dbFile.exists()) return statsMap;

            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();

            // Query matches your CreditsDatabase: table 'player_credits', columns 'uuid' and 'amount'
            String query = "SELECT uuid, amount FROM player_credits ORDER BY amount DESC LIMIT ?";

            try (Connection conn = DriverManager.getConnection(url);
                 PreparedStatement ps = conn.prepareStatement(query)) {

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
    public CompletableFuture<Map<String, Double>> getTopSkriptVar(String varPattern, int limit) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, Double> statsMap = new HashMap<>();
            if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) return statsMap;

            // Note: Skript variable access async can be risky depending on storage backend.
            // Ensure you are using a SQL backend for Skript if you have many players.
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() == null) continue;
                if (!LeaderboardModule.isShowOps() && player.isOp()) continue;

                String varName = varPattern.replace("%uuid%", player.getUniqueId().toString());
                try {
                    // Accessing SkriptUtils
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
            if (plugin.getLoginModule() != null && plugin.getLoginModule().getLoginDatabase() != null) {
                return plugin.getLoginModule().getLoginDatabase().getTopParkourCompletions(limit);
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