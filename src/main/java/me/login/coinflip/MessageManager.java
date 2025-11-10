package me.login.coinflip;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map; // <-- ADD IMPORT
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap; // <-- ADD IMPORT
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {

    private final Login plugin;
    private final CoinflipDatabase database; // [Req 3]
    private final Component serverPrefix;
    private final List<String> broadcastWorlds;
    private final Map<UUID, Boolean> toggleCache = new ConcurrentHashMap<>(); // <-- ADD CACHE

    // [Req 3] Removed dataFile and dataConfig

    public MessageManager(Login plugin, CoinflipDatabase database) {
        this.plugin = plugin;
        this.database = database; // [Req 3]

        // Load prefixes
        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        String legacyPrefixString = plugin.getConfig().getString("server-prefix-2", "&b&lᴍɪɴᴇᴀᴜʀᴏʀᴀ&f: ");

        Component parsedPrefix;
        try {
            parsedPrefix = MiniMessage.miniMessage().deserialize(prefixString + " ");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to parse 'server_prefix' with MiniMessage. Using fallback 'server-prefix-2'.");
            parsedPrefix = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyPrefixString + " ");
        }
        this.serverPrefix = parsedPrefix;

        // Load broadcast worlds from config
        this.broadcastWorlds = plugin.getConfig().getStringList("coinflip.broadcast-worlds");
        if (this.broadcastWorlds.isEmpty()) {
            plugin.getLogger().warning("No 'coinflip.broadcast-worlds' defined in config.yml. Broadcasts will not be sent.");
        }

        // [Req 3] Removed setupDataFile() call
    }

    // [Req 3] Removed setupDataFile()

    // --- FIX FOR SERVER FREEZE ---
    public void saveToggle(UUID uuid, boolean value) {
        // Update the cache immediately
        toggleCache.put(uuid, value);

        // [Req 3] Save to database
        database.saveMessageToggle(uuid, value).exceptionally(ex -> {
            plugin.getLogger().log(Level.SEVERE, "Failed to save coinflip message toggle for " + uuid, ex);
            return null;
        });
    }

    public boolean getToggle(UUID uuid) {
        // Read from the cache. This is instantaneous and safe.
        // Defaults to true if not in cache (e.g., player just joined)
        return toggleCache.getOrDefault(uuid, true);
    }
    // --- END FIX ---

    // --- ADD METHODS FOR CACHE LISTENER ---
    public void addPlayerToCache(UUID uuid, boolean value) {
        toggleCache.put(uuid, value);
    }

    public void removePlayerFromCache(UUID uuid) {
        toggleCache.remove(uuid);
    }
    // --- END ADD ---


    /**
     * Formats a legacy message string with the MiniMessage prefix.
     * @param legacyText The legacy text (e.g., "&cError!")
     * @return A formatted Component.
     */
    public Component format(String legacyText) {
        return serverPrefix.append(LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText));
    }

    /**
     * Sends a formatted message to a player.
     * @param player The player.
     * @param legacyText The legacy text (e.g., "&cError!").
     */
    public void send(Player player, String legacyText) {
        player.sendMessage(format(legacyText));
    }

    /**
     * [Req 2] Sends multiple lines of text as a single message with one prefix.
     * @param player The player.
     * @param legacyLines The legacy text lines (e.g., "&eLine 1", "&eLine 2").
     */
    public void send(Player player, String... legacyLines) {
        if (legacyLines.length == 0) {
            return;
        }
        if (legacyLines.length == 1) {
            send(player, legacyLines[0]);
            return;
        }

        // Join lines with MiniMessage newline tag
        String joinedLegacyText = Arrays.stream(legacyLines)
                .collect(Collectors.joining("<br>"));

        player.sendMessage(serverPrefix.append(LegacyComponentSerializer.legacyAmpersand().deserialize(joinedLegacyText)));
    }

    /**
     * [Req 2] Sends multiple lines of text as a single message with one prefix.
     * @param player The player.
     * @param legacyLines The list of legacy text lines.
     */
    public void send(Player player, List<String> legacyLines) {
        send(player, legacyLines.toArray(new String[0]));
    }


    /**
     * Broadcasts a message to all players in the configured worlds who have toggles enabled.
     * @param component The Component to broadcast.
     */
    public void broadcast(Component component) {
        Component message = serverPrefix.append(component);

        for (String worldName : broadcastWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    // This now reads from the cache and is extremely fast
                    if (getToggle(player.getUniqueId())) {
                        player.sendMessage(message);
                    }
                }
            }
        }
    }
}