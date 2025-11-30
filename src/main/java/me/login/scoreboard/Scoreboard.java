package me.login.scoreboard;

import me.clip.placeholderapi.PlaceholderAPI;
import me.login.Login;
import me.login.level.LevelManager;
import me.login.misc.tokens.TokenManager;
import me.login.premiumfeatures.credits.CreditsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.List;
import java.util.UUID;

public class Scoreboard {

    private final Login plugin;
    private final Player player;
    private final org.bukkit.scoreboard.Scoreboard scoreboard;
    private final Objective objective;

    private final CreditsManager creditsManager;
    private final LevelManager levelManager;
    private final TokenManager tokenManager;

    private long cachedTokens = 0;
    private long lastTokenUpdate = 0;

    public Scoreboard(Login plugin, Player player, String title) {
        this.plugin = plugin;
        this.player = player;

        this.creditsManager = plugin.getCreditsModule().getManager();
        this.levelManager = plugin.getLevelModule().getManager();
        this.tokenManager = plugin.getTokenModule().getTokenManager();

        ScoreboardManager manager = Bukkit.getScoreboardManager();

        // Determine scoreboard instance
        if (player.getScoreboard().equals(manager.getMainScoreboard())) {
            this.scoreboard = manager.getNewScoreboard();
        } else {
            this.scoreboard = player.getScoreboard();
        }

        // Initialize or retrieve Objective
        Objective tempObj = this.scoreboard.getObjective("LoginSB");
        if (tempObj == null) {
            tempObj = this.scoreboard.registerNewObjective("LoginSB", "dummy", toLegacyComponent(title));
            tempObj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            tempObj.displayName(toLegacyComponent(title));
        }

        // Final assignment
        this.objective = tempObj;

        player.setScoreboard(this.scoreboard);
    }

    public void clear() {
        for (Team team : scoreboard.getTeams()) {
            team.unregister();
        }
        for (String entry : scoreboard.getEntries()) {
            scoreboard.resetScores(entry);
        }
    }

    public void setTitle(String title) {
        String parsed = PlaceholderAPI.setPlaceholders(player, title);
        objective.displayName(toLegacyComponent(parsed));
    }

    public void updateLines(List<String> rawLines) {
        if (!player.isOnline()) return;

        UUID uuid = player.getUniqueId();

        String credits = String.valueOf(creditsManager.getBalance(player));
        String level = String.valueOf(levelManager.getLevel(player));

        if (System.currentTimeMillis() - lastTokenUpdate > 2000) {
            lastTokenUpdate = System.currentTimeMillis();
            tokenManager.getTokenBalance(uuid).thenAccept(val -> cachedTokens = val);
        }
        String tokens = String.valueOf(cachedTokens);

        int lineIndex = 0;

        for (String line : rawLines) {
            String parsed = PlaceholderAPI.setPlaceholders(player, line);

            parsed = parsed.replace("{credits." + uuid + "}", credits);
            parsed = parsed.replace("{lifesteal_level_" + uuid + "}", level);

            parsed = parsed.replace("%tokens%", tokens);
            parsed = parsed.replace("%credits%", credits);
            parsed = parsed.replace("%level%", level);

            if (parsed.contains("Tokens: ...") || parsed.contains("Tokens: &a...")) {
                parsed = parsed.replace("...", tokens);
            }

            updateLine(lineIndex, parsed);
            lineIndex++;
        }

        for (int i = rawLines.size(); i < 15; i++) {
            String teamName = "line_" + i;
            Team team = scoreboard.getTeam(teamName);
            if (team != null) {
                for (String entry : team.getEntries()) scoreboard.resetScores(entry);
                team.unregister();
            }
        }
    }

    private void updateLine(int index, String text) {
        int score = 15 - index;
        String teamName = "line_" + index;

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
            String entry = ChatColor.values()[index].toString();
            team.addEntry(entry);
            objective.getScore(entry).setScore(score);
        }

        team.setPrefix(color(text));
    }

    private Component toLegacyComponent(String text) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(text);
    }

    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }
}