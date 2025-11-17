package me.login.misc.rtp;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class RTPCooldownManager {

    // Cooldown in milliseconds (3 minutes)
    private static final long COOLDOWN_MS = TimeUnit.MINUTES.toMillis(3);
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    /**
     * Puts a player on cooldown.
     */
    public void setCooldown(Player player) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Checks if a player is on cooldown.
     *
     * @return The remaining cooldown time in seconds, or 0 if not on cooldown.
     */
    public long getRemainingCooldown(Player player) {
        if (player.hasPermission("mineaurora.rtp.bypasscooldown")) {
            return 0;
        }

        Long lastUsed = cooldowns.get(player.getUniqueId());
        if (lastUsed == null) {
            return 0; // Not on cooldown
        }

        long timeElapsed = System.currentTimeMillis() - lastUsed;
        if (timeElapsed >= COOLDOWN_MS) {
            cooldowns.remove(player.getUniqueId()); // Cooldown expired
            return 0;
        }

        // Return remaining time in seconds
        return TimeUnit.MILLISECONDS.toSeconds(COOLDOWN_MS - timeElapsed);
    }
}