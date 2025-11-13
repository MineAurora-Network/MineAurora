package me.login.misc.generator;

import me.login.Login;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class GenListener implements Listener {

    private final Login plugin;
    private final GenManager manager;
    private final GenItemManager itemManager;

    public GenListener(Login plugin, GenManager manager, GenItemManager itemManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemManager = itemManager;
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        String tierId = itemManager.getTierFromItem(event.getItemInHand());
        if (tierId == null) return;

        // Check Permissions/Limits
        org.bukkit.entity.Player p = event.getPlayer();
        if (!p.hasPermission("admin.genplace")) {
            int currentPlaced = (int) manager.getActiveGenerators().values().stream().filter(g -> g.ownerUUID.equals(p.getUniqueId().toString())).count();
            int limit = 5;
            if (p.hasPermission("phantom.genplace")) limit = 18;
            else if (p.hasPermission("supreme.genplace")) limit = 15;
            else if (p.hasPermission("immortal.genplace")) limit = 13;
            else if (p.hasPermission("overlord.genplace")) limit = 11;
            else if (p.hasPermission("ace.genplace")) limit = 9;
            else if (p.hasPermission("elite.genplace")) limit = 7;

            // Check custom limit (placeholder for DB or config based limits, simpler to just stick to perms for now per prompt)

            if (currentPlaced >= limit) {
                p.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>You have reached your generator limit of " + limit + "!"));
                event.setCancelled(true);
                return;
            }
        }

        manager.placeGenerator(p, event.getBlock().getLocation(), tierId);
        p.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Generator placed!"));
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.breakGenerator(event.getPlayer(), event.getBlock().getLocation())) {
            event.setDropItems(false); // Manager drops the custom item
            event.getPlayer().sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Generator broken."));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        // Upgrade Logic
        manager.upgradeGenerator(event.getPlayer(), event.getClickedBlock().getLocation());
    }
}