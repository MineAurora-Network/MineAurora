package me.login.misc.milestones;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class MilestoneNPCListener implements Listener {

    private final Login plugin;
    private final MilestoneGUI gui;
    private final int npcId;

    public MilestoneNPCListener(Login plugin, MilestoneGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.npcId = plugin.getConfig().getInt("milestone-npc-id", -1);
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            gui.open(event.getClicker());
        }
    }
}