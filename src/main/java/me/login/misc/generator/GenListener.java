package me.login.misc.generator;

import me.login.Login;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

        org.bukkit.entity.Player p = event.getPlayer();

        // Check Limits
        int currentPlaced = (int) manager.getActiveGenerators().values().stream()
                .filter(g -> g.ownerUUID.equals(p.getUniqueId().toString())).count();
        int limit = manager.getPlayerLimit(p);

        if (currentPlaced >= limit) {
            p.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>You have reached your generator limit of " + limit + "!"));
            event.setCancelled(true);
            return;
        }

        manager.placeGenerator(p, event.getBlock().getLocation(), tierId);

        // Chat Message
        p.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Generator placed!"));

        // Title (Requested Feature)
        sendGenTitle(p, "<green>Generator Placed!");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (manager.breakGenerator(event.getPlayer(), event.getBlock().getLocation())) {
            event.setDropItems(false);
            event.getPlayer().sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Generator broken."));

            // Title (Requested Feature)
            sendGenTitle(event.getPlayer(), "<red>Generator Broken!");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getPlayer().isSneaking()) return;
        if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;

        manager.upgradeGenerator(event.getPlayer(), event.getClickedBlock().getLocation());
    }

    private void sendGenTitle(org.bukkit.entity.Player player, String subtitleMinimessage) {
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getConfig().getString("server_prefix_2")),
                plugin.getComponentSerializer().deserialize(subtitleMinimessage)
        );
        player.showTitle(title);
    }
}