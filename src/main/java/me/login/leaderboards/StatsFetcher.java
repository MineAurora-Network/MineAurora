package me.login.leaderboards;

import me.login.Login;
import me.login.scoreboard.SkriptUtils;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class StatsFetcher {

    private final Login plugin;

    public StatsFetcher(Login plugin) {
        this.plugin = plugin;
    }

    public Map<String, Double> getTopStats(Statistic statistic, int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null) continue;
            if (!LeaderboardModule.isShowOps() && player.isOp()) continue;

            int statValue = player.getStatistic(statistic);
            if (statValue > 0) {
                statsMap.put(player.getName(), (double) statValue);
            }
        }
        return sortByValue(statsMap, limit);
    }

    public Map<String, Double> getTopBalances(int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();
        Economy economy = null;

        // --- FIX: Retrieve Vault Economy from ServicesManager directly ---
        if (plugin.getServer().getPluginManager().isPluginEnabled("Vault")) {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        }

        if (economy == null) return statsMap;

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null) continue;
            if (!LeaderboardModule.isShowOps() && player.isOp()) continue;

            if (economy.hasAccount(player)) {
                double balance = economy.getBalance(player);
                if (balance > 0) {
                    statsMap.put(player.getName(), balance);
                }
            }
        }
        return sortByValue(statsMap, limit);
    }

    public Map<String, Double> getTopCredits(int limit) {
        // Connect to SQLite database created in CreditsDatabase
        Map<String, Double> statsMap = new java.util.HashMap<>();
        String dbPath = plugin.getDataFolder() + "/database/credits.db";
        String url = "jdbc:sqlite:" + dbPath;

        try (Connection conn = DriverManager.getConnection(url);
             PreparedStatement ps = conn.prepareStatement("SELECT uuid, amount FROM player_credits ORDER BY amount DESC")) {

            ResultSet rs = ps.executeQuery();
            int count = 0;
            while (rs.next() && count < limit * 2) { // Fetch extra to account for OPs
                String uuidStr = rs.getString("uuid");
                double amount = rs.getDouble("amount");

                OfflinePlayer p = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr));
                if (p.getName() != null) {
                    if (!LeaderboardModule.isShowOps() && p.isOp()) continue;
                    statsMap.put(p.getName(), amount);
                    count++;
                }
                if (statsMap.size() >= limit) break;
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to fetch top credits: " + e.getMessage());
        }
        return sortByValue(statsMap, limit);
    }

    public Map<String, Double> getTopTokens(int limit) {
        // Assuming TokenManager or DB exists. Placeholder implementation based on file list.
        Map<String, Double> statsMap = new java.util.HashMap<>();
        // Logic to fetch from TokenDatabase would go here.
        return statsMap;
    }

    public Map<String, Double> getTopParkour(int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();
        // Placeholder for Parkour system
        return statsMap;
    }

    public Map<String, Double> getTopSkriptVar(String varPattern, int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();

        if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) return statsMap;

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null) continue;
            if (!LeaderboardModule.isShowOps() && player.isOp()) continue;

            String varName = varPattern.replace("%uuid%", player.getUniqueId().toString());
            Object varValue = SkriptUtils.getVar(varName);

            if (varValue instanceof Number) {
                double value = ((Number) varValue).doubleValue();
                if (value > 0) {
                    statsMap.put(player.getName(), value);
                }
            }
        }
        return sortByValue(statsMap, limit);
    }

    private Map<String, Double> sortByValue(Map<String, Double> unsortedMap, int limit) {
        return unsortedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
}