package me.login.misc.dailyquests.listeners;

import me.login.misc.dailyquests.QuestsGui;
import me.login.misc.dailyquests.QuestsModule;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class QuestsNpcListener implements Listener {

    private final QuestsModule module;
    private final QuestsGui questsGui;
    private final int npcId;

    public QuestsNpcListener(QuestsModule module) {
        this.module = module;
        this.questsGui = module.getQuestsGui();
        this.npcId = module.getPlugin().getConfig().getInt("quests-npc-id", -1);
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent e) {
        if (npcId == -1) return; // NPC ID not set

        if (e.getNPC().getId() == npcId) {
            questsGui.openMainQuestGui(e.getClicker());
        }
    }
}