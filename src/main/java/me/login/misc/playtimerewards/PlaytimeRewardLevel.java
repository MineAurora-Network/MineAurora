package me.login.misc.playtimerewards;

/**
 * A simple data record to hold reward information for each level.
 *
 * @param level The level number (e.g., 1, 2, 3...)
 * @param timeRequiredSeconds The total playtime in seconds required to unlock this level.
 * @param coinReward The amount of coins awarded for this level.
 * @param tokenReward The amount of tokens awarded for this level.
 */
public record PlaytimeRewardLevel(
        int level,
        long timeRequiredSeconds,
        long coinReward,
        int tokenReward
) {}