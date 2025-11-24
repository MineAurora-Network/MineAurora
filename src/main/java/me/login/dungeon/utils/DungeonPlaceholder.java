package me.login.dungeon.utils;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.login.Login;
import me.login.dungeon.game.GameManager;
import me.login.dungeon.game.GameSession;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DungeonPlaceholder extends PlaceholderExpansion {

    private final Login plugin;
    private final GameManager gameManager;

    public DungeonPlaceholder(Login plugin, GameManager gameManager) {
        this.plugin = plugin;
        this.gameManager = gameManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dungeon";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Login";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (!player.isOnline()) return "";
        Player p = (Player) player;
        GameSession session = gameManager.getSession(p);

        if (session == null) return "N/A";

        if (params.equalsIgnoreCase("current_room")) {
            return String.valueOf(session.getCurrentRoomId() + 1);
        }
        if (params.equalsIgnoreCase("mobs_left")) {
            return String.valueOf(session.getMobsLeft());
        }
        // CHANGED: Using getTimeLeft() for both placeholders to ensure consistency with the countdown
        if (params.equalsIgnoreCase("time_elapsed") || params.equalsIgnoreCase("time_left")) {
            return session.getTimeLeft();
        }

        return null;
    }
}