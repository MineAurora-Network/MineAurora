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
    private final int npcId;

    public DailyRewardNPCListener(Login plugin, DailyRewardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        // Load NPC ID from config
        this.npcId = plugin.getConfig().getInt("daily-reward-npc-id", -1); // Default to -1
        if (this.npcId == -1) {
            plugin.getLogger().warning("daily-reward-npc-id is not set in config.yml. NPC clicks won't work.");
        }
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC clickedNpc = event.getNPC();

        if (clickedNpc.getId() == npcId) {
            // Same logic as the command
            manager.attemptClaim(player);
        }
    }
}