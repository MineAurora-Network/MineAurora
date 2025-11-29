package me.login.level.listener;

import me.login.level.LevelManager;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

public class BlockBreakListener implements Listener {

    private final LevelManager manager;

    public BlockBreakListener(LevelManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();

        // Mining: 1XP (No Silk Touch)
        if (!isCrop(event.getBlock().getType())) {
            if (tool.containsEnchantment(Enchantment.SILK_TOUCH)) {
                return; // No XP for silk touch
            }
            manager.addXp(player, 1, "Mining");
        }
        // Farming: 1XP (Fully Grown)
        else {
            if (event.getBlock().getBlockData() instanceof Ageable ageable) {
                if (ageable.getAge() == ageable.getMaximumAge()) {
                    manager.addXp(player, 1, "Farming");
                }
            }
        }
    }

    private boolean isCrop(Material mat) {
        return mat == Material.WHEAT || mat == Material.CARROTS || mat == Material.POTATOES ||
                mat == Material.BEETROOTS || mat == Material.NETHER_WART || mat == Material.COCOA;
    }
}