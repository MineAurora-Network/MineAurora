package me.login.misc.dailyquests.listeners;

import me.login.misc.dailyquests.QuestManager;
import me.login.misc.dailyquests.QuestObjective;
import me.login.misc.dailyquests.QuestsModule;
import org.bukkit.Material;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.EnumSet;
import java.util.Set;

public class QuestProgressListener implements Listener {

    private final QuestManager questManager;

    private static final Set<Material> HARVESTABLE_CROPS = EnumSet.of(
            Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS,
            Material.NETHER_WART, Material.COCOA
    );

    public QuestProgressListener(QuestsModule module) {
        this.questManager = module.getQuestManager();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        questManager.loadPlayerData(e.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e) {
        questManager.unloadPlayerData(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Material mat = e.getBlock().getType();

        // Handle Harvest
        if (HARVESTABLE_CROPS.contains(mat) && e.getBlock().getBlockData() instanceof Ageable) {
            Ageable ageable = (Ageable) e.getBlock().getBlockData();
            if (ageable.getAge() == ageable.getMaximumAge()) {
                questManager.handleProgress(p, QuestObjective.HARVEST, mat, null);
                return; // Don't also count it as a break
            }
        }

        // Handle Break / Mine
        questManager.handleProgress(p, QuestObjective.BREAK_BLOCK, mat, null);
        questManager.handleProgress(p, QuestObjective.MINE, mat, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent e) {
        Material placedMaterial = e.getItemInHand().getType();

        // Handle planting seeds (e.g., WHEAT_SEEDS)
        questManager.handleProgress(e.getPlayer(), QuestObjective.PLANT, placedMaterial, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player killer = e.getEntity().getKiller();
            questManager.handleProgress(killer, QuestObjective.KILL_MOB, null, e.getEntityType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent e) {
        if (e.getEntity().getKiller() != null) {
            Player killer = e.getEntity().getKiller();
            questManager.handleProgress(killer, QuestObjective.PLAYER_KILL, null, EntityType.PLAYER);
        }
    }
}