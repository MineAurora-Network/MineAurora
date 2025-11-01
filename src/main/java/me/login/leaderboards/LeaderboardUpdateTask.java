// src/main/java/me/login/leaderboards/LeaderboardUpdateTask.java
package me.login.leaderboards;

import org.bukkit.scheduler.BukkitRunnable;

public class LeaderboardUpdateTask extends BukkitRunnable {

    private final LeaderboardDisplayManager manager;

    public LeaderboardUpdateTask(LeaderboardDisplayManager manager) {
        this.manager = manager;
    }

    @Override
    public void run() {
        manager.updateAllDisplays();
    }
}