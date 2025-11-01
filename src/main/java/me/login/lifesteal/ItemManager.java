package me.login.lifesteal;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

public class ItemManager {

    private final Login plugin;
    private final MiniMessage miniMessage;
    private FileConfiguration itemsConfig;

    private final Component serverPrefix;
    private final WebhookClient logWebhook;

    public final NamespacedKey heartItemKey;
    public final NamespacedKey beaconItemKey;

    public ItemManager(Login plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.heartItemKey = new NamespacedKey(plugin, "lifesteal_heart");
        this.beaconItemKey = new NamespacedKey(plugin, "lifesteal_beacon");

        loadItemsConfig();

        // Load prefix from main config.yml
        String prefixString = plugin.getConfig().getString("server-prefix", "<gray>[Lifesteal]</gray> ");
        this.serverPrefix = miniMessage.deserialize(prefixString);

        // Load webhook
        this.logWebhook = initializeWebhook("lifesteal-system-log-webhook", "Lifesteal-System");
    }

    private void loadItemsConfig() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            plugin.getLogger().info("Creating default items.yml...");
            plugin.saveResource("items.yml", false);
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
    }

    // Call this if you add a /lifesteal reload command
    public void reloadConfigs() {
        loadItemsConfig();
        plugin.reloadConfig();
        // Note: Can't reload prefix or webhook without re-initializing this class
    }

    public Component formatMessage(String message) {
        return serverPrefix.append(miniMessage.deserialize(message));
    }

    public Component formatMessage(Component component) {
        return serverPrefix.append(component);
    }

    public ItemStack getHeartItem(int amount) {
        return createItem(
                Material.RED_DYE,
                amount,
                itemsConfig.getString("heart-item.name", "<red>Heart</red>"),
                itemsConfig.getStringList("heart-item.lore"),
                heartItemKey
        );
    }

    public ItemStack getReviveBeaconItem(int amount) {
        return createItem(
                Material.BEACON,
                amount,
                itemsConfig.getString("revive-beacon.name", "<aqua>Revive Beacon</aqua>"),
                itemsConfig.getStringList("revive-beacon.lore"),
                beaconItemKey
        );
    }

    private ItemStack createItem(Material material, int amount, String name, List<String> lore, NamespacedKey key) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        // Use MiniMessage for name
        meta.displayName(miniMessage.deserialize(name));

        // Use MiniMessage for lore
        List<Component> componentLore = lore.stream()
                .map(miniMessage::deserialize)
                .collect(Collectors.toList());
        meta.lore(componentLore);

        // Add PDC tag to identify the item
        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    // --- Webhook ---
    public void sendLog(String message) {
        if (logWebhook != null) {
            logWebhook.send("[Lifesteal] " + message).exceptionally(error -> {
                plugin.getLogger().warning("Failed send lifesteal webhook: " + error.getMessage());
                return null;
            });
        }
    }

    private WebhookClient initializeWebhook(String configKey, String systemName) {
        String url = plugin.getConfig().getString(configKey);
        if (url == null || url.isEmpty() || url.equals("YOUR_WEBHOOK_URL_HERE")) {
            plugin.getLogger().info(systemName + " Webhook disabled (check '" + configKey + "').");
            return null;
        }
        try {
            WebhookClientBuilder builder = new WebhookClientBuilder(url);
            builder.setThreadFactory((job) -> {
                Thread t = new Thread(job);
                t.setName(systemName + "-WH");
                t.setDaemon(true);
                return t;
            });
            plugin.getLogger().info(systemName + " Webhook enabled.");
            return builder.build();
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid Webhook URL [" + systemName + "]: " + e.getMessage());
        }
        return null;
    }

    public void closeWebhook() {
        if (logWebhook != null) {
            logWebhook.close();
        }
    }

    // --- Adventure Component Helpers ---
    public static String toLegacy(Component component) {
        return LegacyComponentSerializer.legacySection().serialize(component);
    }

    public static Component fromLegacy(String legacyString) {
        return LegacyComponentSerializer.legacySection().deserialize(legacyString);
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }
}