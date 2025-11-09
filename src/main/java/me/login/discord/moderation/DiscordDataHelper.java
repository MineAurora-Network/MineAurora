package me.login.discord.moderation;

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase;
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

    public String getLuckPermsRank(OfflinePlayer player) {
        if (player == null) return "Unknown";
        try {
            var lp = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class).getProvider();
            var user = lp.getUserManager().loadUser(player.getUniqueId()).join();
            if (user == null) return "Unknown";
            return user.getPrimaryGroup();
        } catch (Exception e) {
            plugin.getLogger().warning("Error getting LuckPerms rank: " + e.getMessage());
            return "Unknown";
        }
    }

    public String getLeaderboard(String category, int limit) {
        OfflinePlayer[] allPlayers = Bukkit.getOfflinePlayers();

        Comparator<OfflinePlayer> comparator = switch (category) {
            case "kills" -> Comparator.comparingLong(p -> p.getStatistic(Statistic.PLAYER_KILLS));
            case "deaths" -> Comparator.comparingLong(p -> p.getStatistic(Statistic.DEATHS));
            case "playtime" -> Comparator.comparingLong(p -> p.getStatistic(Statistic.PLAY_ONE_MINUTE)); // Ticks
            case "balance" -> Comparator.comparingLong(this::getPlayerBalance);
            case "credits" -> Comparator.comparingLong(p -> getSkriptVarAsLong(p, "credits_%player's uuid%"));
            case "lifesteal" -> Comparator.comparingLong(p -> getSkriptVarAsLong(p, "lifesteal_level_%player's uuid%"));
            case "mobkills" -> Comparator.comparingLong(p -> p.getStatistic(Statistic.MOB_KILLS));
            default -> null;
        };

        if (comparator == null) {
            return "Error: Invalid leaderboard category `" + category + "`.";
        }

        List<OfflinePlayer> sortedPlayers = Arrays.stream(allPlayers)
                .filter(OfflinePlayer::hasPlayedBefore)
                .sorted(comparator.reversed())
                .limit(limit)
                .toList();

        StringBuilder lb = new StringBuilder();
        int rank = 1;
        for (OfflinePlayer p : sortedPlayers) {
            String entry = formatLeaderboardEntry(p, category);
            lb.append(String.format("`%d.` %s\n", rank++, entry));
        }
        return lb.toString();
    }

    private String formatLeaderboardEntry(OfflinePlayer p, String category) {
        String name = p.getName() != null ? p.getName() : "Unknown";
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
                return String.format("**%s** - Unknown Data", name);
        }
    }

    private long getPlayerBalance(OfflinePlayer p) {
        if (economy == null) return 0;
        return (long) economy.getBalance(p);
    }

    private String formatPlaytime(OfflinePlayer p) {
        long ticks = p.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long seconds = ticks / 20;
        return Utils.formatDuration(seconds * 1000);
    }

    private long getSkriptVarAsLong(OfflinePlayer p, String varNamePattern) {
        if (p.getUniqueId() == null) return 0;

        String varName = varNamePattern.replace("%player's uuid%", p.getUniqueId().toString());
        varName = varName.replace("%player%", p.getName());

        if (Bukkit.getPluginManager().getPlugin("Skript") == null) {
            return 0;
        }

        try {
            Object value = SkriptVarParse.getVariable(varName);

            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                try {
                    return Long.parseLong((String) value);
                } catch (NumberFormatException e) {
                    return 0;
                }
            }
        } catch (Exception e) {
        }
        return 0;
    }

    public EmbedBuilder getPlayerStatsEmbed(OfflinePlayer p) {
        String name = p.getName() != null ? p.getName() : "Unknown";

        String rank = getLuckPermsRank(p);
        String playtime = formatPlaytime(p);
        String linked = linkDb.isLinked(p.getUniqueId()) ? "✅ Linked" : "❌ Not Linked";

        long kills = p.getStatistic(Statistic.PLAYER_KILLS);
        long deaths = p.getStatistic(Statistic.DEATHS);
        long mobKills = p.getStatistic(Statistic.MOB_KILLS);

        String balance = String.format("$%,d", getPlayerBalance(p));
        String credits = String.format("%,d", getSkriptVarAsLong(p, "credits_%player's uuid%"));

        String lifestealLvl = String.format("%,d", getSkriptVarAsLong(p, "lifesteal_level_%player's uuid%"));

        String lastPlayed = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(p.getLastPlayed()));

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.CYAN)
                .setAuthor(name + "'s Stats", null, "https://crafatar.com/avatars/" + p.getUniqueId() + "?overlay")
                .setThumbnail("https://crafatar.com/renders/head/" + p.getUniqueId() + "?overlay")
                .addField("Rank", "`" + rank + "`", true)
                .addField("Playtime", "`" + playtime + "`", true)
                .addField("Discord", "`" + linked + "`", true)
                .addField("Balance", "`" + balance + "`", true)
                .addField("Credits", "`" + credits + "`", true)
                .addField("Lifesteal Lvl", "`" + lifestealLvl + "`", true)
                .addField("Kills/Deaths", String.format("`%d K / %d D`", kills, deaths), true)
                .addField("Mob Kills", "`" + mobKills + "`", true)
                .setFooter("Last Played: " + lastPlayed);

        return eb;
    }
}