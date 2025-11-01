//
// File: me/login/moderation/Utils.java
// (Updated with new time formatters)
//
package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.query.QueryOptions;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(\\d+)([smhdwy])");

    // Simple legacy prefix for staff-to-staff broadcasts
    public static final String STAFF_BROADCAST_PREFIX_LEGACY = "&f[&9Staff&f] ";

    // Caching LuckPerms API
    private static LuckPerms luckPermsApi;
    private static boolean luckPermsChecked = false;

    /**
     * Translates legacy chat color codes.
     * @param text The string to translate
     * @return The color-translated string
     */
    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Gets the server prefix from config and parses it with MiniMessage.
     * @param plugin The main plugin instance
     * @return The Component for the server prefix
     */
    public static Component getServerPrefix(Login plugin) {
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<#1998FF>Server<gray>] ");
        try {
            return MiniMessage.miniMessage().deserialize(prefixString);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Failed to parse 'server_prefix' with MiniMessage. Using default.", e);
            return Component.text("[Server] ", net.kyori.adventure.text.format.NamedTextColor.GRAY);
        }
    }

    /**
     * Sends a Component message to a player or CommandSender.
     * @param sender The recipient
     * @param message The Component to send
     */
    public static void sendComponent(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    /**
     * Broadcasts a message to all staff members with the 'staff.staff' permission.
     * Uses the simple legacy [Staff] prefix.
     * @param message The message to send (will be colorized)
     */
    public static void broadcastToStaff(String message) {
        // We use legacy here for the simple [Staff] prefix as requested
        String fullMessage = color(STAFF_BROADCAST_PREFIX_LEGACY + message);
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(fullMessage);
        Bukkit.broadcast(componentMessage, "staff.staff");
    }

    /**
     * Gets the rank weight of a player from LuckPerms.
     * @param player The player to check
     * @return The player's rank weight, or 0 if not found or LuckPerms is missing.
     */
    public static int getLuckPermsRankWeight(OfflinePlayer player) {
        if (!luckPermsChecked) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                luckPermsApi = provider.getProvider();
            }
            luckPermsChecked = true;
        }

        if (luckPermsApi == null) {
            return 0; // LuckPerms not found
        }

        try {
            User user = luckPermsApi.getUserManager().loadUser(player.getUniqueId()).join();
            if (user == null) {
                return 0;
            }

            // Get the user's "weight" context data
            QueryOptions queryOptions = luckPermsApi.getContextManager().getStaticQueryOptions();

            // Get the nullable string from the API
            String weightString = user.getCachedData().getMetaData(queryOptions).getMetaValue("weight");
            // Wrap it in an Optional to safely handle it
            Optional<String> weight = Optional.ofNullable(weightString);

            return weight.map(s -> {
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }).orElse(0); // Default to 0 if no weight is set

        } catch (Exception e) {
            Bukkit.getLogger().log(Level.SEVERE, "Error getting LuckPerms rank weight for " + player.getName(), e);
            return 0;
        }
    }

    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) {
            return -1; // Permanent
        }
        Matcher matcher = DURATION_PATTERN.matcher(durationStr);
        long totalMillis = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            try {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2).toLowerCase();
                switch (unit) {
                    case "s": totalMillis += TimeUnit.SECONDS.toMillis(value); break;
                    case "m": totalMillis += TimeUnit.MINUTES.toMillis(value); break;
                    case "h": totalMillis += TimeUnit.HOURS.toMillis(value); break;
                    case "d": totalMillis += TimeUnit.DAYS.toMillis(value); break;
                    case "w": totalMillis += TimeUnit.DAYS.toMillis(value * 7); break;
                    case "y": totalMillis += TimeUnit.DAYS.toMillis(value * 365); break;
                }
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return found ? totalMillis : 0;
    }

    public static String formatDuration(long millis) {
        if (millis == -1) return "Permanent";
        if (millis < 1000) return "0 seconds";

        long days = TimeUnit.MILLISECONDS.toDays(millis); millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis); millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis); millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append(days == 1 ? " day" : " days").append(", ");
        if (hours > 0) sb.append(hours).append(hours == 1 ? " hour" : " hours").append(", ");
        if (minutes > 0) sb.append(minutes).append(minutes == 1 ? " minute" : " minutes").append(", ");
        if (seconds > 0 || sb.length() == 0) sb.append(seconds).append(seconds == 1 ? " second" : " seconds");

        if (sb.length() > 2 && sb.substring(sb.length() - 2).equals(", ")) {
            sb.setLength(sb.length() - 2);
        }
        return sb.toString();
    }

    public static String formatTimeLeft(long endTime) {
        if (endTime == -1) return "Permanent";
        long millisLeft = endTime - System.currentTimeMillis();
        if (millisLeft <= 0) return "Expired";
        return formatDuration(millisLeft);
    }

    public static ItemStack getFillerGlass() {
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = glass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            glass.setItemMeta(meta);
        }
        return glass;
    }

    public static void fillGlassPanes(Inventory inv) {
        ItemStack filler = Utils.getFillerGlass();
        for (int i = 9; i <= 17; i++) inv.setItem(i, filler);
        // We no longer fill the bottom row
    }

    // --- NEW METHOD 1 ---
    /**
     * Formats a duration in milliseconds into the "h (hour) and m (minute)" format.
     * @param millis The duration in milliseconds
     * @return A string like "1 hour and 30 minutes", "30 minutes", or "Permanent"
     */
    public static String formatDurationShort(long millis) {
        if (millis == -1) {
            return "Permanent";
        }

        if (millis < 60000) { // Less than one minute
            return "0 minutes";
        }

        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(hours).append(hours == 1 ? " hour" : " hours");
        }

        if (minutes > 0) {
            if (hours > 0) sb.append(" and ");
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }

        // Handle case where it's exactly 1 hour, 0 minutes
        if (hours > 0 && minutes == 0) {
            return sb.toString();
        }

        // Handle case where it's 0 hours, X minutes
        if (hours == 0 && minutes > 0) {
            return sb.toString();
        }

        // Default case if something's empty (shouldn't happen with < 60k check)
        if (sb.length() == 0) {
            return "0 minutes";
        }

        return sb.toString();
    }

    // --- NEW METHOD 2 ---
    /**
     * Formats the remaining time from an end timestamp into the "h (hour) and m (minute)" format.
     * @param endTime The timestamp (in millis) when the period ends
     * @return A string like "1 hour and 30 minutes", "30 minutes", "Permanent", or "Expired"
     */
    public static String formatTimeLeftShort(long endTime) {
        if (endTime == -1) {
            return "Permanent";
        }
        long millisLeft = endTime - System.currentTimeMillis();
        if (millisLeft <= 0) {
            return "Expired";
        }
        return formatDurationShort(millisLeft);
    }
}