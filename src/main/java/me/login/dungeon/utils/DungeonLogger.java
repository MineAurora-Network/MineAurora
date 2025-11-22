package me.login.dungeon.utils;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.bukkit.Bukkit;

public class DungeonLogger {

    private final Login plugin;
    private final JDA jda;
    private final long adminLogChannelId;
    private final long gameLogChannelId;

    public DungeonLogger(Login plugin) {
        this.plugin = plugin;
        // Assuming getLagClearLogger() is available in main class
        if (plugin.getLagClearLogger() != null) {
            this.jda = plugin.getLagClearLogger().getJDA();
        } else {
            this.jda = null;
        }

        this.adminLogChannelId = plugin.getConfig().getLong("dungeon-admin-log-channel-id", 0);
        this.gameLogChannelId = plugin.getConfig().getLong("dungeon-game-log-channel-id", 0);
    }

    public void logAdmin(String message) {
        // Console log
        plugin.getLogger().info("[Dungeon Admin] " + message);
        // Discord log
        sendLog(adminLogChannelId, "**[Admin Command]** " + message);
    }

    public void logGame(String message) {
        // Console log
        plugin.getLogger().info("[Dungeon Game] " + message);
        // Discord log
        sendLog(gameLogChannelId, "**[Dungeon Event]** " + message);
    }

    public void logDrop(String player, String item, String rarity) {
        String msg = "**[DROP]** Player `" + player + "` obtained **" + item + "** (" + rarity + ")!";
        plugin.getLogger().info("[Dungeon Drop] " + player + " got " + item + " (" + rarity + ")");
        sendLog(gameLogChannelId, msg);
    }

    private void sendLog(long channelId, String message) {
        if (jda == null || jda.getStatus() != JDA.Status.CONNECTED || channelId == 0) {
            return;
        }

        // Run async to avoid blocking main thread
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            MessageChannel channel = jda.getTextChannelById(channelId);
            if (channel != null) {
                channel.sendMessage(message).queue();
            }
        });
    }
}