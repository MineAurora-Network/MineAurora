package me.login.pets;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Handles sending all pet-related messages, titles, and action bars to players.
 * Uses MiniMessage format.
 */
public class PetMessageHandler {

    private final Login plugin;
    private final PetsConfig petsConfig;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private String prefix;
    private String prefix2;

    public PetMessageHandler(Login plugin, PetsConfig petsConfig) {
        this.plugin = plugin;
        this.petsConfig = petsConfig;
        this.prefix = plugin.getConfig().getString("server_prefix", "<gray>[<gold>Server</gold>]<reset> ");
        this.prefix2 = plugin.getConfig().getString("server_prefix_2", "&f"); // Fallback
    }

    /**
     * Sends a prefixed MiniMessage to a player.
     * @param player The player to send to.
     * @param message The MiniMessage string.
     */
    public void sendPlayerMessage(Player player, String message) {
        player.sendMessage(mm.deserialize(prefix + message));
    }

    // --- FIXED: Added missing method ---
    public void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(mm.deserialize(prefix + message));
    }

    /**
     * Sends a title and subtitle to a player.
     * @param player The player.
     * @param titleMessage The MiniMessage string for the title.
     * @param subtitleMessage The MiniMessage string for the subtitle.
     */
    public void sendTitle(Player player, String titleMessage, String subtitleMessage) {
        Component title = mm.deserialize(titleMessage, Placeholder.unparsed("prefix", prefix2));
        Component subtitle = mm.deserialize(subtitleMessage, Placeholder.unparsed("prefix", prefix2));

        Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000));
        Title titleObj = Title.title(title, subtitle, times);

        player.showTitle(titleObj);
    }

    /**
     * Sends an action bar message to a player.
     * @param player The player.
     * @param message The MiniMessage string.
     */
    public void sendActionBar(Player player, String message) {
        player.sendActionBar(mm.deserialize(message, Placeholder.unparsed("prefix", prefix2)));
    }
}