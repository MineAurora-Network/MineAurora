package me.login.level.listener;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.login.level.LevelManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class LevelChatListener implements Listener {

    private final LevelManager manager;
    private final MiniMessage mm;

    public LevelChatListener(LevelManager manager) {
        this.manager = manager;
        this.mm = MiniMessage.miniMessage();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        // Format: %player_level% %player's rank prefix% %player's name%&7: <message>

        // 1. Get Level Prefix
        // LevelManager already constructs the colorized level string, e.g. "&8[&a10&8] "
        // We might want to adjust LevelManager to return just the formatted component.
        Component levelPrefix = manager.getLevelPrefixComponent(event.getPlayer());

        // 2. Get Rank Prefix (Delegate to manager which talks to LuckPerms)
        Component rankPrefix = manager.getRankPrefixComponent(event.getPlayer());

        // 3. Player Name
        Component playerName = event.getPlayer().displayName();

        // 4. Separator
        Component separator = LegacyComponentSerializer.legacyAmpersand().deserialize("&7: ");

        // 5. Message (Original message content)
        Component message = event.message();

        // Combine: Level + " " + Rank + " " + Name + ": " + Message
        // Note: Spacing is often handled inside the prefixes themselves or added here.
        // Assuming prefixes might not have trailing spaces, we add them.

        event.renderer((source, sourceDisplayName, msg, viewer) ->
                levelPrefix
                        .append(Component.space())
                        .append(rankPrefix)
                        .append(Component.space())
                        .append(sourceDisplayName)
                        .append(separator)
                        .append(msg)
        );
    }
}