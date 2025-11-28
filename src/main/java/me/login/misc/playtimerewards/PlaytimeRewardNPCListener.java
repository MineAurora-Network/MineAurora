package me.login.misc.playtimerewards;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class PlaytimeRewardNPCListener implements Listener {

    private final Login plugin;
    private final PlaytimeRewardGUI gui;
    private final PlaytimeRewardManager manager;
    private final int npcId;

    public PlaytimeRewardNPCListener(Login plugin, PlaytimeRewardGUI gui, PlaytimeRewardManager manager) {
        this.plugin = plugin;
        this.gui = gui;
        this.manager = manager;
        this.npcId = plugin.getConfig().getInt("playtime-reward-npc-id", -1);
    }

    @EventHandler
    public void onNPCRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC clickedNpc = event.getNPC();

        if (clickedNpc.getId() == npcId) {
            event.setCancelled(true);

            if (manager.getPlayerData(player.getUniqueId()) == null) {
                player.sendMessage(manager.getPrefix().append(manager.getMiniMessage().deserialize("<red>Your playtime data is still loading. Please try again in a moment.</red>")));
                return;
            }

            // FIX: Open page 0 (Page 1), not page 1 (Page 2)
            gui.openGUI(player, 0);
        }
    }
}