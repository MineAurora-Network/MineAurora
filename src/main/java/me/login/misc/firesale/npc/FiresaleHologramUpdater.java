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

public class FiresaleHologramUpdater extends BukkitRunnable {

    private final FiresaleManager firesaleManager;
    @SuppressWarnings("unused")
    private final Login plugin;
    private final NPC npc;
    private String lastText = "";
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd HH:mm").withZone(ZoneId.systemDefault());

    public FiresaleHologramUpdater(Login plugin, FiresaleManager firesaleManager, NPC npc) {
        this.plugin = plugin;
        this.firesaleManager = firesaleManager;
        this.npc = npc;
    }

    @Override
    public void run() {
        if (npc == null || !npc.isSpawned()) return;

        List<Firesale> sales = firesaleManager.getActiveSales();
        Firesale active = sales.isEmpty() ? null : sales.get(0);

        StringBuilder newName = new StringBuilder();

        if (active == null) {
            // Idle State
            newName.append("§7§lFiresale Merchant\n")
                    .append("§r§7No active sales\n")
                    .append("§r§7Check back later!");
        }
        else if (active.getStatus() == SaleStatus.PENDING) {
            // Pending State
            newName.append("§6§lUPCOMING FIRESALE\n")
                    .append("§r§eStarts: §f").append(fmt.format(active.getStartTime())).append("\n")
                    .append("§r§7Get ready!");
        }
        else if (active.getStatus() == SaleStatus.ACTIVE) {
            // Active State
            String itemName = "Unknown Item";
            if (active.getItem() != null) {
                if (active.getItem().getItemMeta() != null && active.getItem().getItemMeta().hasDisplayName()) {
                    // Get name, stripping italics if legacy codes are used in name, otherwise forcing reset
                    itemName = active.getItem().getItemMeta().getDisplayName();
                } else {
                    itemName = active.getItem().getType().toString().replace("_", " ");
                }
            }

            newName.append("§c§lFIRESALE ACTIVE!\n")
                    .append("§r§fItem: §b").append(itemName).append("\n")
                    .append("§r§fPrice: §6").append((int)active.getPrice()).append(" Credits\n")
                    .append("§r§e§nClick to View!");
        }
        else {
            // Cooldown / Ended
            newName.append("§7§lFiresale Ended\n")
                    .append("§r§7Restocking items...");
        }

        String newText = newName.toString();

        // Update NPC name if changed
        // REMOVED: .replace("\n", " ") so lines are preserved
        if (!newText.equals(lastText)) {
            npc.setName(newText);
            lastText = newText;
        }
    }
}