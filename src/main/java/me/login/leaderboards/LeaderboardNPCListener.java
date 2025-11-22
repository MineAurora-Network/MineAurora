package me.login.leaderboards;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LeaderboardNPCListener implements Listener {

    private final Login plugin;
    private final LeaderboardGUI gui;

    public LeaderboardNPCListener(Login plugin, LeaderboardGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @EventHandler
    public void onNPCClick(NPCRightClickEvent event) {
        int configId = plugin.getConfig().getInt("player-starts-npc-id", -1);

        // specific check for the configured ID
        if (configId != -1 && event.getNPC().getId() == configId) {
            gui.openMenu(event.getClicker());
        }
    }
}