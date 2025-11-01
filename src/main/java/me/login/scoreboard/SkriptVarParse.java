package me.login.scoreboard;

import org.bukkit.OfflinePlayer; // <-- IMPORT OFFLINEPLAYER
import org.bukkit.entity.Player;

/**
 * Utility class to parse Skript variable placeholders.
 */
public class SkriptVarParse {

    /**
     * Parses a Skript variable string, replacing placeholders
     * like %player% and %player's uuid% with actual player data.
     *
     * @param player  The player to get data from.
     * @param varName The raw variable string (e.g., "credits.%player's uuid%").
     * @return The parsed variable string (e.g., "credits.123e4567-...").
     */

    // --- THIS IS THE FIX ---
    // Change 'Player player' to 'OfflinePlayer player'
    public static String parse(OfflinePlayer player, String varName) {
        // --- END OF FIX ---

        if (varName == null || varName.isEmpty()) {
            return varName;
        }

        // If no player is provided, we can't parse player placeholders.
        if (player == null) {
            return varName;
        }

        String parsedVar = varName;

        // Replace %player% with the player's name
        // This works for OfflinePlayer
        if (parsedVar.contains("%player%")) {
            parsedVar = parsedVar.replace("%player%", player.getName());
        }

        // Replace %player's uuid% with the player's UUID
        // This also works for OfflinePlayer
        if (parsedVar.contains("%player's uuid%")) {
            parsedVar = parsedVar.replace("%player's uuid%", player.getUniqueId().toString());
        }

        return parsedVar;
    }
}