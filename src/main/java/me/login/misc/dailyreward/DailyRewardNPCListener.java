package me.login.misc.dailyreward;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class DailyRewardNPCListener implements Listener {

    private final Login plugin;
    private final DailyRewardManager manager;
    private final DailyRewardGUI gui;
    private final int npcId;

    public DailyRewardNPCListener(Login plugin, DailyRewardManager manager, DailyRewardGUI gui) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        // Load NPC ID from config
        this.npcId = plugin.getConfig().getInt("daily-reward-npc-id", -1); // Default to -1
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC clickedNpc = event.getNPC();

        if (clickedNpc.getId() == npcId) {
            // Open GUI directly if they have permission, otherwise claim default
            if (player.hasPermission("mineaurora.dailyreward.rank")) {
                gui.openGUI(player);
            } else {
                manager.claimDefaultReward(player);
            }
        }
    }
}