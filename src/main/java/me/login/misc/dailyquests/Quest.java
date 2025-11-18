package me.login.misc.dailyquests;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public class Quest {

    private final String id;
    private final QuestType type;
    private final String objectiveDescription;
    private final QuestObjective objective;
    private final Material objectiveMaterial;
    private final EntityType objectiveEntity;
    private final int requiredAmount;
    private final double rewardCash;
    private final int rewardTokens;

    public Quest(String id, QuestType type, String objectiveDescription, QuestObjective objective, Material objectiveMaterial, EntityType objectiveEntity, int requiredAmount, double rewardCash, int rewardTokens) {
        this.id = id;
        this.type = type;
        this.objectiveDescription = objectiveDescription;
        this.objective = objective;
        this.objectiveMaterial = objectiveMaterial;
        this.objectiveEntity = objectiveEntity;
        this.requiredAmount = requiredAmount;
        this.rewardCash = rewardCash;
        this.rewardTokens = rewardTokens;
    }

    // Getters
    public String getId() {
        return id;
    }

    public QuestType getType() {
        return type;
    }

    public String getObjectiveDescription() {
        return objectiveDescription;
    }

    public QuestObjective getObjective() {
        return objective;
    }

    public Material getObjectiveMaterial() {
        return objectiveMaterial;
    }

    public EntityType getObjectiveEntity() {
        return objectiveEntity;
    }

    public int getRequiredAmount() {
        return requiredAmount;
    }

    public double getRewardCash() {
        return rewardCash;
    }

    public int getRewardTokens() {
        return rewardTokens;
    }
}