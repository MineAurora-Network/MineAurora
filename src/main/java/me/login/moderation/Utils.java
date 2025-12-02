package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
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

import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    private static final Pattern DURATION_PATTERN = Pattern.compile("(?i)(\\d+)([smhdwy])");
    public static final String STAFF_BROADCAST_PREFIX_LEGACY = "&f[&9Staff&f] ";
    private static LuckPerms luckPermsApi;

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static Component getServerPrefix(Login plugin) {
        String prefixString = plugin.getConfig().getString("server_prefix", "<gray>[<#1998FF>Server<gray>] ");
        try {
            return MiniMessage.miniMessage().deserialize(prefixString);
        } catch (Exception e) {
            return Component.text("[Server] ");
        }
    }

    public static void sendComponent(CommandSender sender, Component message) {
        sender.sendMessage(message);
    }

    public static void broadcastToStaff(String message) {
        String fullMessage = color(STAFF_BROADCAST_PREFIX_LEGACY + message);
        Component componentMessage = LegacyComponentSerializer.legacyAmpersand().deserialize(fullMessage);
        Bukkit.broadcast(componentMessage, "staff.staff");
    }

    public static boolean canPunish(CommandSender sender, OfflinePlayer target) {
        if (!(sender instanceof Player)) return true;
        Player staff = (Player) sender;
        if (staff.getUniqueId().equals(target.getUniqueId())) return false;
        if (staff.isOp()) return true;
        if (target.isOp()) return false;
        int senderWeight = getLuckPermsRankWeight(staff);
        int targetWeight = getLuckPermsRankWeight(target);
        return senderWeight > targetWeight;
    }

    /**
     * Gets the weight of the player's primary group from LuckPerms.
     * Returns 0 if LuckPerms is missing or no weight is set.
     */
    public static int getLuckPermsRankWeight(OfflinePlayer player) {
        if (luckPermsApi == null) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) luckPermsApi = provider.getProvider();
        }

        if (luckPermsApi == null) return 0;

        try {
            User user = luckPermsApi.getUserManager().loadUser(player.getUniqueId()).join();
            if (user == null) return 0;

            String groupName = user.getPrimaryGroup();
            Group group = luckPermsApi.getGroupManager().getGroup(groupName);

            if (group != null) {
                // Return the weight if present, otherwise 0
                return group.getWeight().orElse(0);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static long parseDuration(String durationStr) {
        if (durationStr == null || durationStr.equalsIgnoreCase("perm") || durationStr.equalsIgnoreCase("permanent")) return -1;
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
            } catch (NumberFormatException e) {}
        }
        return found ? totalMillis : 0;
    }

    public static String formatDuration(long millis) {
        if (millis == -1) return "Permanent";
        if (millis < 1000) return "0s";
        long days = TimeUnit.MILLISECONDS.toDays(millis); millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis); millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis); millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("d ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0) sb.append(minutes).append("m ");
        if (seconds > 0) sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    public static String formatTimeLeft(long endTime) {
        if (endTime == -1) return "Permanent";
        long millisLeft = endTime - System.currentTimeMillis();
        return millisLeft <= 0 ? "Expired" : formatDuration(millisLeft);
    }

    public static String formatDurationShort(long millis) {
        if (millis == -1) return "Permanent";
        if (millis < 60000) return "0 minutes";
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append(hours == 1 ? " hour" : " hours");
        if (minutes > 0) {
            if (hours > 0) sb.append(" and ");
            sb.append(minutes).append(minutes == 1 ? " minute" : " minutes");
        }
        return sb.length() == 0 ? "0 minutes" : sb.toString();
    }

    public static String formatTimeLeftShort(long end) {
        if(end == -1) return "Permanent";
        long diff = end - System.currentTimeMillis();
        return diff <= 0 ? "Expired" : formatDurationShort(diff);
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
        ItemStack filler = getFillerGlass();
        for (int i = 9; i <= 17; i++) inv.setItem(i, filler);
    }
}