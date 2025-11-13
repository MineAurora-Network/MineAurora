package me.login.pets.data;

import org.bukkit.entity.EntityType;
import java.util.UUID;

public class Pet {

    private final UUID ownerUuid;
    private final EntityType petType;
    private String customName;
    private long cooldownEndTime;

    // --- NEW: Leveling System ---
    private int level;
    private double xp;

    public Pet(UUID ownerUuid, EntityType petType, String customName, long cooldownEndTime, int level, double xp) {
        this.ownerUuid = ownerUuid;
        this.petType = petType;
        this.customName = (customName == null || customName.isEmpty()) ? getDefaultName() : customName;
        this.cooldownEndTime = cooldownEndTime;
        this.level = level < 1 ? 1 : level;
        this.xp = xp;
    }

    public Pet(UUID ownerUuid, EntityType petType) {
        this(ownerUuid, petType, null, 0, 1, 0.0);
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public EntityType getPetType() { return petType; }
    public String getCustomName() { return customName; }
    public long getCooldownEndTime() { return cooldownEndTime; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }

    public String getDisplayName() {
        return (customName != null && !customName.isEmpty()) ? customName : getDefaultName();
    }

    public void setCustomName(String customName) { this.customName = customName; }
    public void setCooldownEndTime(long cooldownEndTime) { this.cooldownEndTime = cooldownEndTime; }

    public void setLevel(int level) { this.level = level; }
    public void setXp(double xp) { this.xp = xp; }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < this.cooldownEndTime;
    }

    public long getRemainingCooldownSeconds() {
        if (!isOnCooldown()) return 0;
        return (this.cooldownEndTime - System.currentTimeMillis()) / 1000;
    }

    public String getDefaultName() {
        String[] parts = petType.name().split("_");
        StringBuilder nameBuilder = new StringBuilder();
        for (String part : parts) {
            nameBuilder.append(part.substring(0, 1).toUpperCase())
                    .append(part.substring(1).toLowerCase())
                    .append(" ");
        }
        return nameBuilder.toString().trim();
    }
}