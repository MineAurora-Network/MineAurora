package me.login.discordcommand;

import me.login.Login;
import me.login.discordlinking.DiscordLinkDatabase;
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
            var lp = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class);
            if (lp != null) {
                net.luckperms.api.model.user.User user = lp.getProvider().getUserManager().loadUser(player.getUniqueId()).join();
                if (user != null) {
                    String primaryGroup = user.getPrimaryGroup();
                    if (primaryGroup != null) {
                        return primaryGroup.substring(0, 1).toUpperCase() + primaryGroup.substring(1);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to get LuckPerms rank for " + player.getName() + ": " + e.getMessage());
        }
        return "Member"; // Default
    }

    /**
     * Gets a player's balance as a whole number.
     */
    public long getPlayerBalance(OfflinePlayer player) {
        if (player == null || economy == null) return 0;
        return (long) economy.getBalance(player);
    }

    /**
     * Gets a Skript variable as a whole number.
     */
    public long getSkriptVarAsLong(OfflinePlayer player, String varNameTemplate) {
        if (player == null) return 0;
        try {
            String parsedVarName = SkriptVarParse.parse(player, varNameTemplate);
            Object value = SkriptUtils.getVar(parsedVarName);
            if (value instanceof Number) {
                return ((Number) value).longValue();
            } else if (value instanceof String) {
                return Long.parseLong((String) value);
            }
        } catch (Exception e) {
            // Don't spam console, just return 0
        }
        return 0;
    }

    /**
     * Formats playtime from ticks into "X hours, Y minutes".
     */
    public String formatPlaytime(OfflinePlayer player) {
        if (player == null) return "0 minutes";
        long ticks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long seconds = ticks / 20;
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        return String.format("%d hours, %d minutes", hours, minutes);
    }

    /**
     * Builds the complete embed for the /balance command. (Helper for CommandManager)
     */
    public EmbedBuilder buildBalanceEmbed(OfflinePlayer target) {
        long balance = getPlayerBalance(target);
        String rank = getLuckPermsRank(target);

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(target.getName() + "'s Balance");
        eb.setColor(Color.GREEN);
        eb.addField("Player", rank + " " + target.getName(), false);
        eb.addField("Balance", "$" + String.format("%,d", balance), false); // No decimals
        return eb;
    }

    /**
     * Builds the complete embed for the /profile command.
     */
    public EmbedBuilder buildProfileEmbed(OfflinePlayer target) {
        String rank = getLuckPermsRank(target);
        String playtime = formatPlaytime(target);
        long balance = getPlayerBalance(target);
        // --- MODIFIED LINES ---
        long credits = getSkriptVarAsLong(target, "credits_%player's uuid%"); // Assuming credits also uses underscore
        long lifesteal = getSkriptVarAsLong(target, "lifesteal_level_%player's uuid%"); // Using your exact variable name
        // --- END MODIFICATION ---
        long kills = target.getStatistic(Statistic.PLAYER_KILLS);
        long deaths = target.getStatistic(Statistic.DEATHS);
        String firstJoin = new SimpleDateFormat("MMM dd, yyyy").format(new Date(target.getFirstPlayed()));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle(rank + " " + target.getName() + "'s Profile");
        eb.setColor(Color.decode("#42ACF1")); // Blue color

        eb.addField("💰 Balance", "$" + String.format("%,d", balance), true);
        eb.addField("✨ Credits", String.format("%,d", credits), true);
        eb.addField("❤️ Lifesteal", String.format("%,d", lifesteal), true);

        eb.addField("⚔️ Kills", String.format("%,d", kills), true);
        eb.addField("💀 Deaths", String.format("%,d", deaths), true);
        eb.addField("⏳ Playtime", playtime, true);

        eb.setFooter("First Joined: " + firstJoin);
        return eb;
    }

    /**
     * Builds the complete embed for the /leaderboard command.
     */
    public EmbedBuilder buildLeaderboardEmbed(String category) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.decode("#F0DE47")); // Gold color

        List<OfflinePlayer> allPlayers = Arrays.asList(Bukkit.getOfflinePlayers());

        Comparator<OfflinePlayer> comparator;
        String title;
        String fieldName;

        switch(category) {
            case "kills":
                title = "Top Kills";
                fieldName = "Kills";
                comparator = Comparator.comparingLong(p -> p.getStatistic(Statistic.PLAYER_KILLS));
                break;
            case "deaths":
                title = "Top Deaths";
                fieldName = "Deaths";
                comparator = Comparator.comparingLong(p -> p.getStatistic(Statistic.DEATHS));
                break;
            case "playtime":
                title = "Top Playtime";
                fieldName = "Playtime";
                comparator = Comparator.comparingLong(p -> p.getStatistic(Statistic.PLAY_ONE_MINUTE));
                break;
            case "balance":
                title = "Top Balance";
                fieldName = "Balance";
                comparator = Comparator.comparingLong(this::getPlayerBalance);
                break;
            case "credits":
                title = "Top Credits";
                fieldName = "Credits";
                // --- MODIFIED LINE ---
                comparator = Comparator.comparingLong(p -> getSkriptVarAsLong(p, "credits_%player's uuid%"));
                break;
            case "lifesteal":
                title = "Top Lifesteal Level";
                fieldName = "Level";
                // --- MODIFIED LINE ---
                comparator = Comparator.comparingLong(p -> getSkriptVarAsLong(p, "lifesteal_level_%player's uuid%"));
                break;
            case "mobkills":
                title = "Top Mob Kills";
                fieldName = "Mob Kills";
                comparator = Comparator.comparingLong(p -> p.getStatistic(Statistic.MOB_KILLS));
                break;
            default:
                eb.setTitle("Error");
                eb.setDescription("Invalid leaderboard category. Use: `kills`, `deaths`, `playtime`, `balance`, `credits`, `lifesteal`, `mobkills`");
                return eb;
        }

        eb.setTitle("🏆 " + title);

        List<String> top10 = allPlayers.stream()
                .sorted(comparator.reversed()) // Sort descending
                .limit(10)
                .map(p -> formatLeaderboardEntry(p, category))
                .collect(Collectors.toList());

        StringBuilder description = new StringBuilder();
        for (int i = 0; i < top10.size(); i++) {
            description.append(String.format("`%d.` %s\n", i + 1, top10.get(i)));
        }

        eb.setDescription(description.toString());
        return eb;
    }

    /**
     * Helper to format a single line on the leaderboard.
     */
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
                // --- MODIFIED LINE ---
                value = getSkriptVarAsLong(p, "credits_%player's uuid%");
                return String.format("**%s** - %s Credits", name, String.format("%,d", value));
            case "lifesteal":
                // --- MODIFIED LINE ---
                value = getSkriptVarAsLong(p, "lifesteal_level_%player's uuid%");
                return String.format("**%s** - Level %s", name, String.format("%,d", value));
            case "mobkills":
                value = p.getStatistic(Statistic.MOB_KILLS);
                return String.format("**%s** - %s Mob Kills", name, String.format("%,d", value));
            default:
                return name;
        }
    }
}