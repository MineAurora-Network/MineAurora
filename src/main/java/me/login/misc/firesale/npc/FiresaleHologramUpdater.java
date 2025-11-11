package me.login.misc.firesale.npc;

import me.login.Login;
import me.login.misc.firesale.FiresaleManager;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import net.citizensnpcs.api.npc.NPC;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Handles automatic Citizens NPC name updates for the FireSale system.
 * No external HologramTrait dependency required.
 */
public class FiresaleHologramUpdater extends BukkitRunnable {

    private final FiresaleManager firesaleManager;
    private final Login plugin;
    private final NPC npc;
    private String lastText = "";

    public FiresaleHologramUpdater(Login plugin, FiresaleManager firesaleManager, NPC npc) {
        this.plugin = plugin;
        this.firesaleManager = firesaleManager;
        this.npc = npc;
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) return;

        // Get first active or pending firesale
        List<Firesale> sales = firesaleManager.getActiveSales();
        Firesale active = sales.isEmpty() ? null : sales.get(0);

        StringBuilder newName = new StringBuilder();

        if (active == null) {
            newName.append("§7I'm exploring the void...\n§7looking for hot deals!");
        }
        else if (active.getStatus() == SaleStatus.PENDING) { // PENDING instead of UPCOMING
            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMMM yyyy HH:mm")
                    .withZone(ZoneId.systemDefault());
            newName.append("§6Upcoming Firesale\n§eStarts: ")
                    .append(fmt.format(active.getStartTime()));
        }
        else if (active.getStatus() == SaleStatus.ACTIVE) {
            newName.append("§cNerd, come grab deals before they vanish into the void!");
        }
        else {
            newName.append("§7Deals are cooling down...");
        }

        String newText = newName.toString();

        // Update NPC name only if changed
        if (!newText.equals(lastText)) {
            npc.setName(newText.replace("\n", " "));
            lastText = newText;
        }
    }
}
