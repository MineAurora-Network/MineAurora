package me.login.level;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class LevelNPCListener implements Listener {

    private final int npcId;
    private final LevelGUI gui;

    public LevelNPCListener(Login plugin, LevelGUI gui) {
        this.gui = gui;
        this.npcId = plugin.getConfig().getInt("lifesteal-level-npc-id", -1);
    }

    @EventHandler
    public void onNpcClick(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            gui.open(event.getClicker());
        }
    }
}