package me.login.misc.milestones;

import me.login.Login;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MilestoneListener implements Listener {

    private final MilestoneManager manager;
    private final MilestoneDatabase database;

    public MilestoneListener(MilestoneManager manager, MilestoneDatabase database) {
        this.manager = manager;
        this.database = database;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        // Reset Victim Streak
        manager.resetStreak(victim);

        // Increment Killer Streak (if killer is a player)
        if (killer != null && !killer.equals(victim)) {
            manager.incrementStreak(killer);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        database.loadPlayerData(event.getPlayer().getUniqueId(), manager);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.saveData(event.getPlayer().getUniqueId());
    }
}