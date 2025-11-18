package me.login.misc.dailyquests.listeners;

import me.login.misc.dailyquests.QuestManager;
import me.login.misc.dailyquests.QuestType;
import me.login.misc.dailyquests.QuestsGui;
import me.login.misc.dailyquests.QuestsModule;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;

public class QuestsGuiListener implements Listener {

    private final QuestManager manager;

    public QuestsGuiListener(QuestsModule module) {
        this.manager = module.getQuestManager();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (!e.getView().title().equals(Component.text(QuestsGui.GUI_TITLE))) {
            return;
        }

        // Always cancel clicks in this GUI
        e.setCancelled(true);

        // Check if it's a player
        if (!(e.getWhoClicked() instanceof Player)) {
            return;
        }

        // We only care about shift-clicks to accept
        if (!e.isShiftClick()) {
            return;
        }

        Player player = (Player) e.getWhoClicked();
        int slot = e.getSlot();

        switch (slot) {
            case 11: // Easy
                manager.acceptQuest(player, QuestType.EASY);
                break;
            case 13: // Hard
                manager.acceptQuest(player, QuestType.HARD);
                break;
            case 15: // Extreme
                manager.acceptQuest(player, QuestType.EXTREME);
                break;
        }
    }
}