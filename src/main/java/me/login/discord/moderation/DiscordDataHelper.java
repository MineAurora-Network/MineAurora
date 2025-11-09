package me.login.discord.moderation; // <-- CHANGED

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase; // <-- CHANGED
import me.login.moderation.Utils;
import me.login.scoreboard.SkriptUtils;
import me.login.scoreboard.SkriptVarParse;
import net.dv8tion.jda.api.EmbedBuilder;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordDataHelper {

    private final Login plugin;
    private final Economy economy;
    private final DiscordLinkDatabase linkDb;

    public DiscordDataHelper(Login plugin, Economy economy, DiscordLinkDatabase linkDb) {
        this.plugin = plugin;
        this.economy = economy;
        this.linkDb = linkDb;
    }

    /**
     * Gets a player's primary LuckPerms group name.
     */
    public String getLuckPermsRank(OfflinePlayer player) {
        if (player == null) return "Unknown";
        try {
            var lp = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class).getProvider();
            var user = lp.getUserManager().loadUser(player.getUniqueId()).join();
            if (user == null) return "Unknown";
            String rank = user.getPrimaryGroup();
            return (rank != null) ? rank : "Unknown";
        } catch (Exception e) {
            e.printStackTrace();
            return "Unknown";
        }
    }

    /**
     * Formats playtime from ticks to a "Xh Ym" string.
     */
    public String formatPlaytime(OfflinePlayer p) {
        long ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE); // Renamed in 1.16+
        long seconds = ticks / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        return String.format("%dh %dm", hours, minutes);
    }

    /**
     * Gets a player's Vault balance.
     */
    public long getPlayerBalance(OfflinePlayer p) {
        if (economy == null) return 0L;
        return (long) economy.getBalance(p);
    }

    /**
     * Gets a Skript variable as a long.
     */
    public long getSkriptVarAsLong(OfflinePlayer p, String varName) {
        if (plugin.getServer().getPluginManager().getPlugin("Skript") == null) {
            return 0L;
        }
        String parsedVarName = varName.replace("%player's uuid%", p.getUniqueId().toString());
        String skriptValue = SkriptVarParse.getSkriptVar(parsedVarName);
        if (skriptValue == null || skriptValue.equals("<none>")) {
            return 0L;
        }
        try {
            return Long.parseLong(skriptValue.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * Gets a formatted string for a specific statistic.
     */
    public String getStatString(OfflinePlayer p, String category) {
        String name = p.getName();
        if (name == null) name = "Unknown";
        long value;
        switch (category) {
            case "kills":
                value = p.getStatistic(Statistic.PLAYER_KILLS);
                return String.format("**%s** - %s Kills", name, String.format("%,d", value));
            case "deaths":
                value = p.getStatistic(Statistic.DEATHS);
                return String.format("**%s** - %s Deaths", name, String.format("%,d", value));
            case "playtime":
                String playtime = formatPlaytime(p);
                return String.format("**%s** - %s", name, playtime);
            case "balance":
                value = getPlayerBalance(p);
                return String.format("**%s** - $%s", name, String.format("%,d", value));
            case "credits":
                value = getSkriptVarAsLong(p, "credits_%player's uuid%");
                return String.format("**%s** - %s Credits", name, String.format("%,d", value));
            case "lifesteal":
                value = getSkriptVarAsLong(p, "lifesteal_level_%player's uuid%");
                return String.format("**%s** - Level %s", name, String.format("%,d", value));
            case "mobkills":
                value = p.getStatistic(Statistic.MOB_KILLS);
                return String.format("**%s** - %s Mob Kills", name, String.format("%,d", value));
            default:
                return String.format("**%s** - N/A", name);
        }
    }
}