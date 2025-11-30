package me.login.lifesteal.prestige;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class HeartPrestigeNPCListener implements Listener {

    private final Login plugin;
    private final HeartPrestigeGUI gui;
    private final int npcId;

    public HeartPrestigeNPCListener(Login plugin, HeartPrestigeGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.npcId = plugin.getConfig().getInt("prestige-npc-id", -1);
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            // FIXED: Calls 'open' instead of 'openMenu' to match the GUI class
            gui.open(event.getClicker());
        }
    }
}