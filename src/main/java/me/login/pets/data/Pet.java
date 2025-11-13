package me.login.pets.data;

import org.bukkit.entity.EntityType;

import java.util.UUID;

/**
 * Data class (POJO) representing a captured pet.
 * Stores information about the pet's owner, type, custom name, and cooldown status.
 */
public class Pet {

    private final UUID ownerUuid;
    private final EntityType petType;
    private String customName;
    private long cooldownEndTime; // Timestamp (millis) when the cooldown expires

    public Pet(UUID ownerUuid, EntityType petType, String customName, long cooldownEndTime) {
        this.ownerUuid = ownerUuid;
        this.petType = petType;
        this.customName = (customName == null || customName.isEmpty()) ? getDefaultName() : customName;
        this.cooldownEndTime = cooldownEndTime;
    }

    public Pet(UUID ownerUuid, EntityType petType) {
        this(ownerUuid, petType, null, 0);
    }

    public UUID getOwnerUuid() {
        return ownerUuid;
    }

    public EntityType getPetType() {
        return petType;
    }

    public String getCustomName() {
        return customName;
    }

    public long getCooldownEndTime() {
        return cooldownEndTime;
    }

    /**
     * Gets the display name, which is either the custom name or the default entity type name.
     * @return The display name for this pet.
     */
    public String getDisplayName() {
        return (customName != null && !customName.isEmpty()) ? customName : getDefaultName();
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public void setCooldownEndTime(long cooldownEndTime) {
        this.cooldownEndTime = cooldownEndTime;
    }

    /**
     * Checks if the pet is currently on cooldown.
     * @return true if on cooldown, false otherwise.
     */
    public boolean isOnCooldown() {
        return System.currentTimeMillis() < this.cooldownEndTime;
    }

    /**
     * Gets the remaining cooldown time in seconds.
     * @return Remaining seconds, or 0 if not on cooldown.
     */
    public long getRemainingCooldownSeconds() {
        if (!isOnCooldown()) {
            return 0;
        }
        return (this.cooldownEndTime - System.currentTimeMillis()) / 1000;
    }

    /**
     * Generates a default, capitalized name from the EntityType.
     * e.g., PIGLIN_BRUTE -> "Piglin Brute"
     * @return A formatted, default name.
     */
    public String getDefaultName() { // --- FIXED: Changed from private to public ---
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