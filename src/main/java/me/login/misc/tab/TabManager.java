package me.login.misc.tab;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;

public class TabManager {

    private final Login plugin;
    private BukkitTask tabUpdaterTask;
    private String hubHeader;
    private String hubFooter;

    public TabManager(Login plugin) {
        this.plugin = plugin;
        loadConfig(); // Load config first
        // Register listener AFTER loading config
        plugin.getServer().getPluginManager().registerEvents(new TabListener(this), plugin);
    }

    /**
     * Loads the header and footer from the plugin's own config.yml.
     */
    public void loadConfig() {
        plugin.reloadConfig(); // Make sure we have the latest config
        FileConfiguration config = plugin.getConfig();

        // Default values in case the config is missing
        List<String> defaultHeader = List.of("&bWelcome to the &lHub&r!", "&eplay.yourserver.com");
        List<String> defaultFooter = List.of("", "&ePlayers in Hub: &f%hub_online%");

        // Load hub header
        // This loads from *your* plugin's config.yml, NOT the TAB plugin's config.
        this.hubHeader = ChatColor.translateAlternateColorCodes('&',
                String.join("\n", config.getStringList("tablist.hub.header"))
        );

        // Load hub footer
        this.hubFooter = ChatColor.translateAlternateColorCodes('&',
                String.join("\n", config.getStringList("tablist.hub.footer"))
        );

        // --- FOR DEBUGGING: Log what was loaded ---
        plugin.getLogger().info("[TabManager] Loaded Hub Header: " + this.hubHeader.replace("\n", " | "));
        plugin.getLogger().info("[TabManager] Loaded Hub Footer: " + this.hubFooter.replace("\n", " | "));

        // Add this to your config.yml (src/main/resources/config.yml) to customize it:
        // tablist:
        //   hub:
        //     header:
        //       - "&bWelcome to the &lHub&r!"
        //       - ""
        //     footer:
        //       - ""
        //       - "&eHave fun!"
    }

    /**
     * Starts the repeating task that updates the tablist for all players.
     */
    public void startUpdater() {
        if (this.tabUpdaterTask != null && !this.tabUpdaterTask.isCancelled()) {
            this.tabUpdaterTask.cancel();
        }
        // Run the updater every 20 ticks (1 second)
        this.tabUpdaterTask = new TabUpdater(this).runTaskTimer(plugin, 0L, 20L);
    }

    /**
     * Stops the tab updater task.
     */
    public void stopUpdater() {
        if (this.tabUpdaterTask != null && !this.tabUpdaterTask.isCancelled()) {
            this.tabUpdaterTask.cancel();
            this.tabUpdaterTask = null;
        }
    }

    /**
     * This is the main logic method, called by the listener and the updater.
     * It updates the Header/Footer *and* player visibility for all players.
     */
    public void updateAllPlayers() {
        int hubOnline = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase("hub")) {
                hubOnline++;
            }
        }
        String finalHubFooter = hubFooter.replace("%hub_online%", String.valueOf(hubOnline));

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            // 1. Set Header/Footer based on the player's world
            updatePlayerHeaderFooter(player, finalHubFooter);

            // 2. Update player visibility (who 'player' can see)
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.isOnline()) continue;

                if (player.equals(other)) {
                    player.showPlayer(plugin, other); // Always show self
                    continue;
                }

                // Decide if 'player' should be able to see 'other'
                if (shouldPlayerSeeOther(player, other)) {
                    player.showPlayer(plugin, other);
                } else {
                    player.hidePlayer(plugin, other);
                }
            }
        }
    }

    /**
     * Helper method to set the header/footer for a single player.
     * @param player The player to update.
     * @param dynamicHubFooter The footer with placeholders parsed.
     */
    private void updatePlayerHeaderFooter(Player player, String dynamicHubFooter) {
        String playerWorld = player.getWorld().getName();

        if (playerWorld.equalsIgnoreCase("login")) {
            // VANILLA: Set blank header/footer
            player.setPlayerListHeaderFooter("", "");
        } else if (playerWorld.equalsIgnoreCase("hub")) {
            // HUB: Set custom header/footer
            player.setPlayerListHeaderFooter(hubHeader, dynamicHubFooter);
        } else {
            // OTHER WORLDS (lifesteal, etc.):
            // DO NOTHING. Let the TAB plugin control the header/footer.
            // Setting it to "" here would break the TAB plugin.
        }
    }

    /**
     * The core visibility logic.
     * @param player The viewing player.
     * @param other The player being viewed.
     * @return true if 'player' should see 'other', false otherwise.
     */
    private boolean shouldPlayerSeeOther(Player player, Player other) {
        String playerWorld = player.getWorld().getName();
        String otherWorld = other.getWorld().getName();

        // Rule 1: 'login' players see NO ONE (except themselves, handled in updateAllPlayers)
        if (playerWorld.equalsIgnoreCase("login")) {
            return false;
        }

        // Rule 2: NO ONE sees 'login' players
        if (otherWorld.equalsIgnoreCase("login")) {
            return false;
        }

        // Rule 3: 'hub' players only see other 'hub' players
        if (playerWorld.equalsIgnoreCase("hub")) {
            return otherWorld.equalsIgnoreCase("hub");
        }

        // Rule 4: 'default' (lifesteal, etc.) players
        // Let the TAB plugin handle visibility.
        // We return true, so TAB can decide to show or hide them based on its own config.
        // (We already handled the 'login' case in Rule 2).
        return true;
    }

    // Getter for the plugin instance
    public Login getPlugin() {
        return plugin;
    }
}