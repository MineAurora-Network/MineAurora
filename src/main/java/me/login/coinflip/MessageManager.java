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
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public class MessageManager {

    private final Login plugin;
    private final Component serverPrefix;
    private final List<String> broadcastWorlds;

    private File dataFile;
    private FileConfiguration dataConfig;

    public MessageManager(Login plugin) {
        this.plugin = plugin;

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

        // Load data file for toggles
        setupDataFile();
    }

    private void setupDataFile() {
        dataFile = new File(plugin.getDataFolder(), "coinflip-data.yml");
        if (!dataFile.exists()) {
            try {
                dataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create coinflip-data.yml!");
            }
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);
    }

    public void saveToggle(UUID uuid, boolean value) {
        dataConfig.set("players." + uuid.toString(), value);
        try {
            dataConfig.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save coinflip-data.yml!");
        }
    }

    public boolean getToggle(UUID uuid) {
        // Default to true (on)
        return dataConfig.getBoolean("players." + uuid.toString(), true);
    }

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
     * Broadcasts a message to all players in the configured worlds who have toggles enabled.
     * @param component The Component to broadcast.
     */
    public void broadcast(Component component) {
        Component message = serverPrefix.append(component);

        for (String worldName : broadcastWorlds) {
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                for (Player player : world.getPlayers()) {
                    if (getToggle(player.getUniqueId())) {
                        player.sendMessage(message);
                    }
                }
            }
        }
    }
}