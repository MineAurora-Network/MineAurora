package me.login.coinflip;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class CoinflipNpcListener implements Listener {

    private final Login plugin;
    private final CoinflipMenu coinflipMenu;
    private final int npcId; // [Req 7]

    public CoinflipNpcListener(Login plugin, CoinflipMenu coinflipMenu) {
        this.plugin = plugin;
        this.coinflipMenu = coinflipMenu;
        // [Req 7] Load NPC ID from config
        this.npcId = plugin.getConfig().getInt("coinflip-npc-id", 2); // Default to 2
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC clickedNpc = event.getNPC();

        if (clickedNpc.getId() == npcId) {
            // Open the main coinflip menu for the player
            coinflipMenu.openMainMenu(player, 0); // Open page 0
        }
    }
}