package me.login.misc.tokens;

import me.login.Login;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class TokenShopNPCListener implements Listener {

    private final Login plugin;
    private final TokenShopGUI gui;
    private final int npcId;

    public TokenShopNPCListener(Login plugin, TokenShopGUI gui) {
        this.plugin = plugin;
        this.gui = gui;
        this.npcId = plugin.getConfig().getInt("token-shop-npc-id", -1); // Default to -1
        if (this.npcId == -1) {
            plugin.getLogger().warning("token-shop-npc-id is not set in config.yml. NPC clicks won't work.");
        }
    }

    @EventHandler
    public void onNpcRightClick(NPCRightClickEvent event) {
        Player player = event.getClicker();
        NPC clickedNpc = event.getNPC();

        if (clickedNpc.getId() == npcId) {
            gui.openGUI(player);
        }
    }
}