package me.login.pets;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.time.Duration;

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
        this.prefix2 = plugin.getConfig().getString("server_prefix_2", "&f");
    }

    public void sendPlayerMessage(Player player, String message) {
        player.sendMessage(mm.deserialize(prefix + message));
    }

    public void sendConsoleMessage(String message) {
        Bukkit.getConsoleSender().sendMessage(mm.deserialize(prefix + message));
    }

    // --- FIXED: Renamed to sendPlayerTitle and used default times ---
    public void sendPlayerTitle(Player player, String titleMessage, String subtitleMessage) {
        sendPlayerTitle(player, titleMessage, subtitleMessage, 500, 3000, 1000);
    }

    // --- FIXED: Added overload for sendPlayerTitle with custom times (in milliseconds) ---
    public void sendPlayerTitle(Player player, String titleMessage, String subtitleMessage, int fadeInMs, int stayMs, int fadeOutMs) {
        Component title = mm.deserialize(titleMessage, Placeholder.unparsed("prefix", prefix2));
        Component subtitle = mm.deserialize(subtitleMessage, Placeholder.unparsed("prefix", prefix2));

        Title.Times times = Title.Times.times(
                Duration.ofMillis(fadeInMs),
                Duration.ofMillis(stayMs),
                Duration.ofMillis(fadeOutMs)
        );
        Title titleObj = Title.title(title, subtitle, times);

        player.showTitle(titleObj);
    }

    // --- FIXED: Renamed to sendPlayerActionBar ---
    public void sendPlayerActionBar(Player player, String message) {
        player.sendActionBar(mm.deserialize(message, Placeholder.unparsed("prefix", prefix2)));
    }
}