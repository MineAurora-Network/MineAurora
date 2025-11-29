package me.login.lifesteal.prestige;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class HeartPrestigeNPCListener implements Listener {

    private final int npcId;
    private final HeartPrestigeGUI gui;

    public HeartPrestigeNPCListener(Login plugin, HeartPrestigeGUI gui) {
        this.gui = gui;
        this.npcId = plugin.getConfig().getInt("prestige-npc-id", -1);
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            gui.open(event.getClicker());
        }
    }
}