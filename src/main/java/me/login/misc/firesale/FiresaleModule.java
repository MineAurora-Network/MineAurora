package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.command.FiresaleCommand;
import me.login.misc.firesale.database.FiresaleDatabase;
import me.login.misc.firesale.gui.FiresaleGUI;
import me.login.misc.firesale.item.FiresaleItemManager;
import me.login.misc.firesale.npc.FiresaleHologramUpdater;
import org.bukkit.Bukkit;
import net.citizensnpcs.api.npc.NPC;

import java.util.logging.Level;

public class FiresaleModule {
    private final Login plugin;

    private FiresaleDatabase firesaleDatabase;
    private FiresaleItemManager firesaleItemManager;
    private FiresaleManager firesaleManager;
    private FiresaleLogger firesaleLogger;
    private FiresaleListener firesaleListener;
    private FiresaleGUI firesaleGUI;
    private NPC firesaleNpc;

    public FiresaleModule(Login plugin) {
        this.plugin = plugin;
    }

    public boolean init() {
        plugin.getLogger().info("Initializing FiresaleModule...");

        try {
            // 1. Database
            firesaleDatabase = new FiresaleDatabase(plugin);
            firesaleDatabase.init();

            // 2. Items
            firesaleItemManager = new FiresaleItemManager(plugin);

            // 3. Logger - FIX: Changed getJDA() to getJda()
            firesaleLogger = new FiresaleLogger(plugin.getJda(), plugin.getConfig().getString("firesale.discord-log-channel", ""));

            // 4. Manager
            firesaleManager = new FiresaleManager(plugin, firesaleDatabase, firesaleLogger, firesaleItemManager);
            firesaleManager.loadSales();
            firesaleManager.startScheduler();

            // 5. GUI
            firesaleGUI = new FiresaleGUI(plugin, firesaleManager, firesaleDatabase);

            // 6. Listener & NPC
            int npcId = plugin.getConfig().getInt("firesale-npc-id", -1);

            firesaleListener = new FiresaleListener(plugin, firesaleManager, firesaleGUI, npcId);
            Bukkit.getPluginManager().registerEvents(firesaleListener, plugin);

            // 7. NPC Hologram Logic
            if (npcId != -1) {
                try {
                    if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                        this.firesaleNpc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
                        if (this.firesaleNpc != null) {
                            firesaleManager.setNpc(this.firesaleNpc);

                            // Start hologram updater
                            FiresaleHologramUpdater updater = new FiresaleHologramUpdater(plugin, firesaleManager, this.firesaleNpc);
                            updater.runTaskTimer(plugin, 0L, 20L * 5); // Run every 5 seconds

                            plugin.getLogger().info("Firesale NPC linked to ID " + npcId);
                        } else {
                            plugin.getLogger().warning("Firesale NPC ID " + npcId + " not found in Citizens registry.");
                        }
                    } else {
                        plugin.getLogger().warning("Citizens plugin not found! Firesale NPC will not work.");
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("Error hooking into Citizens: " + t.getMessage());
                }
            }

            // 8. Command
            FiresaleCommand cmd = new FiresaleCommand(plugin, firesaleManager, firesaleItemManager);
            if (plugin.getCommand("firesale") != null) {
                plugin.getCommand("firesale").setExecutor(cmd);
                plugin.getCommand("firesale").setTabCompleter(cmd);
            }

            plugin.getLogger().info("FiresaleModule initialized successfully.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "FiresaleModule initialization failed: " + t.getMessage(), t);
            return false;
        }
    }

    public void disable() {
        if (firesaleManager != null) firesaleManager.shutdown();
        if (firesaleDatabase != null) firesaleDatabase.close();
    }

    public FiresaleManager getManager() {
        return firesaleManager;
    }
}