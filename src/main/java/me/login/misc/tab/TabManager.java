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
        loadConfig();
        plugin.getServer().getPluginManager().registerEvents(new TabListener(this), plugin);
    }

    /**
     * Loads the header and footer from the config.yml.
     */
    public void loadConfig() {
        plugin.reloadConfig(); // Make sure we have the latest config
        FileConfiguration config = plugin.getConfig();

        // Default values in case the config is missing
        List<String> defaultHeader = List.of("&bWelcome to the &lHub&r!");
        List<String> defaultFooter = List.of("&ePlayers Online: &f" + Bukkit.getOnlinePlayers().size()); // Note: This size is static, for a dynamic count you'd need PAPI or to update it in the task.

        // Load hub header
        this.hubHeader = ChatColor.translateAlternateColorCodes('&',
                String.join("\n", config.getStringList("tablist.hub.header"))
        );

        // Load hub footer
        this.hubFooter = ChatColor.translateAlternateColorCodes('&',
                String.join("\n", config.getStringList("tablist.hub.footer"))
        );

        // Add this to your config.yml to customize it:
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
        // This is important for keeping player visibility (hide/show) in sync
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
     * It applies the correct tablist rules based on the player's world.
     */
    public void updatePlayer(Player player) {
        if (player == null || !player.isOnline()) {
            return;
        }

        String worldName = player.getWorld().getName();

        try {
            if (worldName.equalsIgnoreCase("login")) {
                applyLoginTab(player);
            } else if (worldName.equalsIgnoreCase("hub")) {
                applyHubTab(player);
            } else {
                applyDefaultTab(player);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to update tab for " + player.getName() + " in world " + worldName);
            e.printStackTrace();
        }
    }

    /**
     * This method is called by the periodic updater for all players.
     */
    protected void updateAllPlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            updatePlayer(player);
        }
    }

    /**
     * World: "login"
     * - Only show the player themselves.
     * - No header/footer.
     * - Hide all other players.
     */
    private void applyLoginTab(Player player) {
        // Set an empty header/footer
        player.setPlayerListHeaderFooter("", ""); // <-- CORRECTED (was null, null)

        // Hide all other players from this player
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.equals(player)) {
                player.hidePlayer(plugin, other);
            } else {
                player.showPlayer(plugin, other); // Make sure they can see themselves
            }
        }
    }

    /**
     * World: "hub"
     * - Show only other players in the "hub".
     * - Show custom header/footer from config.
     */
    private void applyHubTab(Player player) {
        // Set the custom hub header/footer
        player.setPlayerListHeaderFooter(hubHeader, hubFooter);

        // Manage player visibility
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(player) || other.getWorld().getName().equalsIgnoreCase("hub")) {
                // Show players who are also in the hub (and self)
                player.showPlayer(plugin, other);
            } else {
                // Hide players who are not in the hub
                player.hidePlayer(plugin, other);
            }
        }
    }

    /**
     * World: "other" (any world not 'login' or 'hub')
     * - Show all players.
     * - Clear header/footer to let the "TAB" plugin take control.
     */
    private void applyDefaultTab(Player player) {
        // Clear our custom header/footer.
        // The "TAB" plugin should take over from here.
        player.setPlayerListHeaderFooter("", ""); // <-- CORRECTED (was null, null)

        // Make sure this player can see all other players
        // The "TAB" plugin will then handle sorting/display.
        for (Player other : Bukkit.getOnlinePlayers()) {
            player.showPlayer(plugin, other);
        }
    }

    // Getter for the plugin instance
    public Login getPlugin() {
        return plugin;
    }
}