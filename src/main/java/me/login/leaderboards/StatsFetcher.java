package me.login.leaderboards;

import me.login.scoreboard.SkriptUtils; // --- ADDED IMPORT ---
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class to fetch and sort Minecraft statistics for all players.
 */
public class StatsFetcher {

    public static Map<String, Integer> getTopStats(Statistic statistic, int limit) {
        Map<String, Integer> statsMap = new java.util.HashMap<>();

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            int statValue = player.getStatistic(statistic);
            if (statValue > 0) {
                if (player.getName() != null) {
                    statsMap.put(player.getName(), statValue);
                }
            }
        }

        return statsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    public static Map<String, Double> getTopBalances(Economy economy, int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();

        if (economy == null) {
            Bukkit.getLogger().warning("[Leaderboards] Vault Economy object is null. Cannot fetch balances.");
            return statsMap;
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() != null && economy.hasAccount(player)) {
                double balance = economy.getBalance(player);
                if (balance > 0) {
                    statsMap.put(player.getName(), balance);
                }
            }
        }

        return statsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    // --- ADDED NEW METHOD FOR SKRIPT ---
    /**
     * Gets a map of top players for a given Skript variable.
     *
     * @param varPattern The variable pattern (e.g., "credits.%uuid%")
     * @param limit      The number of players to return
     * @return A sorted LinkedHashMap with PlayerName -> Value
     */
    public static Map<String, Double> getTopSkriptVar(String varPattern, int limit) {
        Map<String, Double> statsMap = new java.util.HashMap<>();

        // Check if Skript is running
        if (!Bukkit.getPluginManager().isPluginEnabled("Skript")) {
            Bukkit.getLogger().warning("[Leaderboards] Skript not found. Cannot fetch Skript variable leaderboards.");
            return statsMap;
        }

        for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
            if (player.getName() == null) {
                continue;
            }

            // Construct the variable name from the pattern
            String varName = varPattern.replace("%uuid%", player.getUniqueId().toString());

            // Get the value using your SkriptUtils
            Object varValue = SkriptUtils.getVar(varName);

            // Check if the value is a number
            if (varValue instanceof Number) {
                double value = ((Number) varValue).doubleValue();
                if (value > 0) {
                    statsMap.put(player.getName(), value);
                }
            }
        }

        // Sort and return
        return statsMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .limit(limit)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }
    // --- END ADD ---
}