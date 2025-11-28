package me.login.misc.tab;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TabManager {

    private final Login plugin;
    private final MiniMessage miniMessage;
    private BukkitTask tabUpdaterTask;

    // We store the raw string templates so we can replace placeholders (like %hub_online%) every second
    private String hubHeaderRaw;
    private String hubFooterRaw;

    // Pattern to find legacy ampersand codes (e.g., &a, &7, &l)
    private final Pattern legacyPattern = Pattern.compile("&([0-9a-fk-or])");

    public TabManager(Login plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(new TabListener(this), plugin);
    }

    public void loadConfig() {
        plugin.reloadConfig();
        FileConfiguration config = plugin.getConfig();

        // Join the list into a single string with newlines
        // We do NOT translate colors here yet, because we want MiniMessage to handle everything later
        this.hubHeaderRaw = String.join("<newline>", config.getStringList("tablist.hub.header"));
        this.hubFooterRaw = String.join("<newline>", config.getStringList("tablist.hub.footer"));

        // Log for debugging
        plugin.getLogger().info("[TabManager] Header Template: " + hubHeaderRaw);
    }

    public void startUpdater() {
        if (this.tabUpdaterTask != null && !this.tabUpdaterTask.isCancelled()) {
            this.tabUpdaterTask.cancel();
        }
        this.tabUpdaterTask = new TabUpdater(this).runTaskTimer(plugin, 0L, 20L);
    }

    public void stopUpdater() {
        if (this.tabUpdaterTask != null && !this.tabUpdaterTask.isCancelled()) {
            this.tabUpdaterTask.cancel();
            this.tabUpdaterTask = null;
        }
    }

    public void updateAllPlayers() {
        int hubOnline = 0;
        int globalOnline = Bukkit.getOnlinePlayers().size(); // Calculate global count

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase("hub")) {
                hubOnline++;
            }
        }

        // Pre-calculate footer strings to save performance, replacing placeholders
        String finalFooterString = hubFooterRaw
                .replace("%hub_online%", String.valueOf(hubOnline))
                .replace("%global_online%", String.valueOf(globalOnline));

        // Parse them into Components properly handling Mixed formats (& and <gradient>)
        Component finalHeaderComp = parseMixedContent(hubHeaderRaw);
        Component finalFooterComp = parseMixedContent(finalFooterString);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            // 1. Set Header/Footer if in managed world
            if (isManagedWorld(player.getWorld())) {
                updatePlayerHeaderFooter(player, finalHeaderComp, finalFooterComp);
            }

            // 2. Visibility Logic
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.isOnline()) continue;
                if (player.equals(other)) continue;

                if (shouldPlayerSeeOther(player, other)) {
                    player.showPlayer(plugin, other);
                } else {
                    player.hidePlayer(plugin, other);
                }
            }
        }
    }

    /**
     * Updates the tablist header/footer for the player using Adventure Components.
     * This supports RGB/Gradients perfectly.
     */
    private void updatePlayerHeaderFooter(Player player, Component header, Component footer) {
        String playerWorld = player.getWorld().getName();

        if (playerWorld.equalsIgnoreCase("login")) {
            player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        } else if (playerWorld.equalsIgnoreCase("hub")) {
            player.sendPlayerListHeaderAndFooter(header, footer);
        }
    }

    public boolean isManagedWorld(org.bukkit.World world) {
        String name = world.getName();
        return name.equalsIgnoreCase("hub") || name.equalsIgnoreCase("login");
    }

    public void resetTabList(Player player) {
        player.sendPlayerListHeaderAndFooter(Component.empty(), Component.empty());
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.isOnline() && !other.equals(player)) {
                player.showPlayer(plugin, other);
            }
        }
    }

    private boolean shouldPlayerSeeOther(Player player, Player other) {
        String playerWorld = player.getWorld().getName();
        String otherWorld = other.getWorld().getName();

        if (playerWorld.equalsIgnoreCase("login")) return false;
        if (otherWorld.equalsIgnoreCase("login")) return false;

        if (playerWorld.equalsIgnoreCase("hub")) {
            return otherWorld.equalsIgnoreCase("hub");
        }
        return true;
    }

    /**
     * Handles strings that contain both Legacy codes (&7) and MiniMessage tags (<gradient>).
     * It converts legacy codes to MiniMessage tags so the entire string can be parsed safely.
     */
    private Component parseMixedContent(String text) {
        if (text == null || text.isEmpty()) return Component.empty();

        // 1. Convert &7 -> <gray>, &l -> <bold>, etc.
        String converted = convertLegacyColors(text);

        // 2. Parse with MiniMessage to handle gradients and the newly converted tags
        return miniMessage.deserialize(converted);
    }

    /**
     * Replaces legacy '&' codes with MiniMessage tags.
     */
    private String convertLegacyColors(String text) {
        Matcher matcher = legacyPattern.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            String code = matcher.group(1).toLowerCase();
            String replacement = switch (code) {
                case "0" -> "<black>";
                case "1" -> "<dark_blue>";
                case "2" -> "<dark_green>";
                case "3" -> "<dark_aqua>";
                case "4" -> "<dark_red>";
                case "5" -> "<dark_purple>";
                case "6" -> "<gold>";
                case "7" -> "<gray>";
                case "8" -> "<dark_gray>";
                case "9" -> "<blue>";
                case "a" -> "<green>";
                case "b" -> "<aqua>";
                case "c" -> "<red>";
                case "d" -> "<light_purple>";
                case "e" -> "<yellow>";
                case "f" -> "<white>";
                case "k" -> "<obfuscated>";
                case "l" -> "<bold>";
                case "m" -> "<strikethrough>";
                case "n" -> "<underlined>";
                case "o" -> "<italic>";
                case "r" -> "<reset>";
                default -> "";
            };
            matcher.appendReplacement(sb, replacement);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public Login getPlugin() {
        return plugin;
    }
}