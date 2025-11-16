package me.login.pets.data;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

public class Pet {

    private final UUID ownerUuid;
    private final EntityType petType;
    private String customName;
    private long cooldownEndTime;
    private int level;
    private double xp;

    // --- NEW: Pet Inventory ---
    private ItemStack[] armorContents;
    private ItemStack weaponContent;


    public Pet(UUID ownerUuid, EntityType petType, String customName, long cooldownEndTime, int level, double xp, String armorBase64, String weaponBase64) {
        this.ownerUuid = ownerUuid;
        this.petType = petType;
        this.customName = (customName == null || customName.isEmpty()) ? getDefaultName() : customName;
        this.cooldownEndTime = cooldownEndTime;
        this.level = level < 1 ? 1 : level;
        this.xp = xp;

        this.armorContents = deserializeArmor(armorBase64);
        this.weaponContent = deserializeWeapon(weaponBase64);
    }

    // Overload constructor for database loading
    public Pet(UUID ownerUuid, EntityType petType, String customName, long cooldownEndTime, int level, double xp) {
        this(ownerUuid, petType, customName, cooldownEndTime, level, xp, null, null);
    }

    public Pet(UUID ownerUuid, EntityType petType) {
        this(ownerUuid, petType, null, 0, 1, 0.0, null, null);
    }

    // --- Getters ---
    public UUID getOwnerUuid() { return ownerUuid; }
    public EntityType getPetType() { return petType; }
    public String getCustomName() { return customName; }
    public long getCooldownEndTime() { return cooldownEndTime; }
    public int getLevel() { return level; }
    public double getXp() { return xp; }

    // --- Setters ---
    public void setCustomName(String customName) { this.customName = customName; }
    public void setCooldownEndTime(long cooldownEndTime) { this.cooldownEndTime = cooldownEndTime; }
    public void setLevel(int level) { this.level = level; }
    public void setXp(double xp) { this.xp = xp; }

    // --- Getters/Setters for Inventory ---
    public ItemStack[] getArmorContents() { return armorContents; }
    public ItemStack getWeaponContent() { return weaponContent; }
    public void setArmorContents(ItemStack[] armorContents) { this.armorContents = armorContents; }
    public void setWeaponContent(ItemStack weaponContent) { this.weaponContent = weaponContent; }

    // --- Helper Methods (Re-added) ---

    public String getDisplayName() {
        return (customName != null && !customName.isEmpty()) ? customName : getDefaultName();
    }

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

    // --- Serialization Methods ---

    public String serializeArmor() {
        return serializeItemStackArray(this.armorContents);
    }

    public String serializeWeapon() {
        if (this.weaponContent == null) return null;
        return serializeItemStackArray(new ItemStack[]{this.weaponContent});
    }

    private ItemStack[] deserializeArmor(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[4];
        ItemStack[] items = deserializeItemStackArray(data);
        return (items != null) ? items : new ItemStack[4];
    }

    private ItemStack deserializeWeapon(String data) {
        if (data == null || data.isEmpty()) return null;
        ItemStack[] items = deserializeItemStackArray(data);
        return (items != null && items.length > 0) ? items[0] : null;
    }

    public static String serializeItemStackArray(ItemStack[] items) {
        if (items == null) return null;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream)) {

            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                if (item != null && item.getType() != Material.AIR) {
                    dataOutput.writeObject(item);
                } else {
                    dataOutput.writeObject(null);
                }
            }
            return Base64Coder.encodeLines(outputStream.toByteArray());

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static ItemStack[] deserializeItemStackArray(String data) {
        if (data == null) return null;
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream)) {

            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            return items;

        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }
}