package me.login.misc.dailyquests;

import java.util.EnumSet;
import java.util.UUID;

/**
 * Stores the daily quest state for a player.
 * This has been rewritten to support a single active quest.
 */
public class PlayerQuestData {

    private final UUID playerUUID;
    private long lastQuestResetTimestamp;

    // The 3 quests offered to the player for the day
    private Quest dailyEasyQuest;
    private Quest dailyHardQuest;
    private Quest dailyExtremeQuest;

    // The single quest the player has accepted
    private Quest activeQuest;
    private int activeQuestProgress;

    // Tracks which types (EASY, HARD, EXTREME) have been completed today
    private EnumSet<QuestType> completedQuestTypes;

    public PlayerQuestData(UUID playerUUID) {
        this.playerUUID = playerUUID;
        this.lastQuestResetTimestamp = 0;
        this.completedQuestTypes = EnumSet.noneOf(QuestType.class);
    }

    // Getters and Setters
    public UUID getPlayerUUID() {
        return playerUUID;
    }

    public long getLastQuestResetTimestamp() {
        return lastQuestResetTimestamp;
    }

    public void setLastQuestResetTimestamp(long lastQuestResetTimestamp) {
        this.lastQuestResetTimestamp = lastQuestResetTimestamp;
    }

    public Quest getDailyEasyQuest() {
        return dailyEasyQuest;
    }

    public void setDailyEasyQuest(Quest dailyEasyQuest) {
        this.dailyEasyQuest = dailyEasyQuest;
    }

    public Quest getDailyHardQuest() {
        return dailyHardQuest;
    }

    public void setDailyHardQuest(Quest dailyHardQuest) {
        this.dailyHardQuest = dailyHardQuest;
    }

    public Quest getDailyExtremeQuest() {
        return dailyExtremeQuest;
    }

    public void setDailyExtremeQuest(Quest dailyExtremeQuest) {
        this.dailyExtremeQuest = dailyExtremeQuest;
    }

    public Quest getActiveQuest() {
        return activeQuest;
    }

    public void setActiveQuest(Quest activeQuest) {
        this.activeQuest = activeQuest;
    }

    public int getActiveQuestProgress() {
        return activeQuestProgress;
    }

    public void setActiveQuestProgress(int activeQuestProgress) {
        this.activeQuestProgress = activeQuestProgress;
    }

    public EnumSet<QuestType> getCompletedQuestTypes() {
        return completedQuestTypes;
    }

    public void setCompletedQuestTypes(EnumSet<QuestType> completedQuestTypes) {
        this.completedQuestTypes = completedQuestTypes;
    }

    /**
     * Resets all daily data, but keeps the new quests.
     */
    public void resetDailyData() {
        this.activeQuest = null;
        this.activeQuestProgress = 0;
        this.completedQuestTypes.clear();
        this.lastQuestResetTimestamp = System.currentTimeMillis();
    }
}