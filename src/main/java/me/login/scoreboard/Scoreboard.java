package me.login.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages a custom scoreboard for a single player,
 * with support for Skript variables and PAPI.
 */
public class Scoreboard {

    private final Player player;
    private final org.bukkit.scoreboard.Scoreboard bukkitScoreboard;
    private final Objective objective;

    // Used to track lines and prevent flickering
    private final List<String> oldEntries = new ArrayList<>();

    public Scoreboard(Player player, String title) {
        this.player = player;

        if (player.getScoreboard().equals(Bukkit.getScoreboardManager().getMainScoreboard())) {
            this.bukkitScoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        } else {
            this.bukkitScoreboard = player.getScoreboard();
        }

        // Process title initially too
        String processedTitle = processLine(title); // Process PAPI/Skript here
        String finalTitle = ChatColor.translateAlternateColorCodes('&', processedTitle); // Then colors

        this.objective = this.bukkitScoreboard.registerNewObjective(
                "mainboard",
                "dummy",
                finalTitle
        );
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        player.setScoreboard(this.bukkitScoreboard);
    }

    /**
     * Updates the title of the scoreboard.
     */
    public void setTitle(String title) {
        // --- UPDATED ---
        // 1. Process Placeholders (PAPI and Skript)
        String processedTitle = processLine(title);
        // 2. Translate Color Codes
        String coloredTitle = ChatColor.translateAlternateColorCodes('&', processedTitle);
        // --- END UPDATED ---

        // Only update if the title actually changed
        if (!objective.getDisplayName().equals(coloredTitle)) {
            objective.setDisplayName(coloredTitle);
        }
    }

    /**
     * Updates all lines on the scoreboard.
     */
    public void updateLines(List<String> lines) {
        if (!player.isOnline()) {
            return;
        }

        // --- ADDED FIX ---
        // If the player isn't looking at our board (e.g., it was cleared),
        // force them to look at it before we update the lines.
        if (!player.getScoreboard().equals(this.bukkitScoreboard)) {
            player.setScoreboard(this.bukkitScoreboard);
        }
        // --- END FIX ---

        List<String> newEntries = new ArrayList<>();
        int score = lines.size();

        for (String line : lines) {
            // Process placeholders BEFORE color
            String processedLine = processLine(line);
            String finalLine = getFinalLine(processedLine);

            Score s = this.objective.getScore(finalLine);
            s.setScore(score);
            newEntries.add(finalLine);
            score--;
        }

        // Remove old scores
        for (String oldEntry : oldEntries) {
            if (!newEntries.contains(oldEntry)) {
                this.bukkitScoreboard.resetScores(oldEntry);
            }
        }
        this.oldEntries.clear();
        this.oldEntries.addAll(newEntries);
    }

    /**
     * Processes a single line, parsing Skript and PAPI placeholders.
     * Also used for the title now.
     */
    private String processLine(String line) {
        String processed = line;
        String rawPlaceholder = "%statistic_time_played%"; // Keep for playtime formatting

        // 1. Parse your custom Skript placeholders: {varname}
        if (processed.contains("{") && processed.contains("}")) {
            try {
                // Find all occurrences, not just the first one
                int startIndex = processed.indexOf("{");
                while (startIndex != -1) {
                    int endIndex = processed.indexOf("}", startIndex + 1);
                    if (endIndex == -1) break; // No closing brace found

                    String varNameRaw = processed.substring(startIndex + 1, endIndex);

                    // 1a. Parse %player's uuid% inside the var name
                    String parsedVarName = SkriptVarParse.parse(this.player, varNameRaw);

                    // 1b. Get the value from Skript
                    Object varValue = SkriptUtils.getVar(parsedVarName);
                    String varString = (varValue != null) ? varValue.toString() : "0";

                    // 1c. Replace the placeholder
                    processed = processed.substring(0, startIndex) + varString + processed.substring(endIndex + 1);

                    // Look for the next placeholder
                    startIndex = processed.indexOf("{");
                }
            } catch (Exception e) {
                // Don't change the line if Skript parsing fails, just log it maybe?
                // plugin.getLogger().warning("Failed to parse Skript variable in line: " + line);
            }
        }


        // 2. Parse PlaceholderAPI placeholders: %placeholder%
        boolean isPlaytimeLine = line.contains(rawPlaceholder); // Check original line
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            // Pass the player object to parse player-specific placeholders too
            processed = PlaceholderAPI.setPlaceholders(this.player, processed);
        }

        // 3. Custom Playtime Formatting
        if (isPlaytimeLine) {
            if (processed.contains(rawPlaceholder)) {
                // PAPI failed or is still processing, show a default
                processed = processed.replace(rawPlaceholder, "0h");
            } else {
                // PAPI succeeded. The line might be like: "... &b1d 2h 34m 47s"
                // Extract and format the time
                try {
                    String timePart = extractTimeFromLine(processed); // Helper function might be needed
                    if(timePart != null) {
                        String formattedTime = formatPlaytime(timePart); // Format to "Xd Yh"
                        processed = processed.replace(timePart, formattedTime);
                    }
                } catch (Exception e) {
                    // Failsafe
                }
            }
        }

        // 4. Parse color codes AFTER placeholders
        // We moved this from setTitle/updateLines into here for consistency
        return ChatColor.translateAlternateColorCodes('&', processed);
    }

    // --- NEW HELPER METHODS for Playtime ---
    /**
     * Extracts the time string (e.g., "1d 2h 34m 47s") from the end of a processed line.
     * Assumes time is the last part of the string after color codes.
     */
    private String extractTimeFromLine(String processedLine) {
        // Strip colors first to make splitting easier
        String stripped = ChatColor.stripColor(processedLine);
        String[] parts = stripped.split(" ");
        List<String> timeSegments = new ArrayList<>();

        for (int i = parts.length - 1; i >= 0; i--) {
            String part = parts[i];
            if (part.matches("\\d+[dhms]")) { // Matches "1d", "2h", "34m", "47s"
                timeSegments.add(0, part);
            } else {
                break; // Stop when we hit something that's not a time segment
            }
        }
        return timeSegments.isEmpty() ? null : String.join(" ", timeSegments);
    }

    /**
     * Formats a time string like "1d 2h 34m 47s" into "1d 2h".
     */
    private String formatPlaytime(String timeString) {
        String[] parts = timeString.split(" ");
        if (parts.length >= 2) {
            // "1d 2h ..." -> "1d 2h"
            if (parts[0].endsWith("d") && parts[1].endsWith("h")) {
                return parts[0] + " " + parts[1];
            }
            // "2h 34m ..." -> "2h"
            if(parts[0].endsWith("h")){
                return parts[0];
            }
        } else if (parts.length == 1) {
            // "1d" -> "1d 0h"
            if (parts[0].endsWith("d")) {
                return parts[0] + " 0h";
            }
            // "2h", "34m", "47s" -> return as is (or maybe format "0h 34m" etc?)
            // For now, just return it.
            return parts[0];
        }
        return "0h"; // Default if parsing fails
    }
    // --- END NEW HELPER METHODS ---


    /**
     * Truncates a string to Bukkit's scoreboard line limit.
     * Applies AFTER color codes.
     */
    private String getFinalLine(String processedLine) {
        // Bukkit counts color codes in the limit before 1.13(?)
        // Safer to truncate based on visible characters if needed, but 40 is usually safe now.
        if (processedLine.length() > 40) {
            // Basic truncate - might cut off color codes mid-sequence
            return processedLine.substring(0, 40);

            // A more robust (but complex) method would parse colors:
            // return fixLength(processedLine);
        }
        return processedLine;
    }

    /**
     * Clears the scoreboard for the player.
     */
    public void clear() {
        if (player.isOnline()) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    // Optional: More robust length fixer if needed
    /*
    private static String fixLength(String str) {
        if (str.length() <= 40) return str;
        StringBuilder sb = new StringBuilder();
        int count = 0;
        ChatColor lastColor = null;
        for (int i = 0; i < str.length() && count < 40; i++) {
            char c = str.charAt(i);
            if (c == ChatColor.COLOR_CHAR && i + 1 < str.length()) {
                char code = str.charAt(i + 1);
                ChatColor color = ChatColor.getByChar(code);
                if (color != null) {
                    sb.append(color);
                    if (!color.isFormat()) { // Keep last non-format color
                        lastColor = color;
                    }
                    i++; // Skip the color code char
                    continue;
                }
            }
            sb.append(c);
            count++;
        }
        // If the string was cut off, re-apply the last color code
        if (count == 40 && lastColor != null) {
             // Maybe add the last color back? Depends on desired behavior
             // This can get complex with formatting codes (&l, &o etc.)
        }
        return sb.toString();
    }
    */
}