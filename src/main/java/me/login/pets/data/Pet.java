package me.login.pets.data;

import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;

public class Pet {

    private final UUID ownerUuid;
    private final EntityType petType;
    private String displayName;
    private long cooldownEndTime;
    private int level;
    private double xp;

    private double hunger;
    private double health; // --- NEW: Health Field ---

    private final double maxHunger = 20.0;

    private ItemStack[] armorContents;
    private ItemStack weaponContent;
    private ItemStack attributeContent;

    public Pet(UUID ownerUuid, EntityType petType, String displayName, long cooldownEndTime, int level, double xp, double hunger, double health, String armorBase64, String weaponBase64) {
        this.ownerUuid = ownerUuid;
        this.petType = petType;
        this.displayName = (displayName != null && !displayName.isEmpty()) ? displayName : getDefaultName();
        this.cooldownEndTime = cooldownEndTime;
        this.level = level;
        this.xp = xp;
        this.hunger = Math.max(0, Math.min(hunger, maxHunger));
        this.health = health; // 0 means full/default if not set yet
        this.armorContents = deserializeItems(armorBase64);
        this.weaponContent = deserializeItem(weaponBase64);
    }

    // Constructor for loading old data (defaults health to 20)
    public Pet(UUID ownerUuid, EntityType petType, String displayName, long cooldownEndTime, int level, double xp, String armorBase64, String weaponBase64) {
        this(ownerUuid, petType, displayName, cooldownEndTime, level, xp, 20.0, 20.0, armorBase64, weaponBase64);
    }

    public String getDefaultName() {
        String name = petType.name().toLowerCase().replace("_", " ");
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public UUID getOwnerUuid() { return ownerUuid; }
    public EntityType getPetType() { return petType; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public boolean isOnCooldown() {
        return System.currentTimeMillis() < cooldownEndTime;
    }

    public long getCooldownEndTime() {
        return cooldownEndTime;
    }

    public long getRemainingCooldownSeconds() {
        if (!isOnCooldown()) return 0;
        return (cooldownEndTime - System.currentTimeMillis()) / 1000;
    }

    public void setCooldownEndTime(long cooldownEndTime) { this.cooldownEndTime = cooldownEndTime; }

    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; }

    public double getXp() { return xp; }
    public void setXp(double xp) { this.xp = xp; }

    // --- Hunger & Health ---
    public double getHunger() { return hunger; }
    public void setHunger(double hunger) {
        this.hunger = Math.max(0, Math.min(hunger, maxHunger));
    }
    public double getMaxHunger() { return maxHunger; }

    public double getHealth() { return health; }
    public void setHealth(double health) { this.health = health; }

    // Inventory
    public ItemStack[] getArmorContents() { return armorContents; }
    public void setArmorContents(ItemStack[] armorContents) { this.armorContents = armorContents; }

    public ItemStack getWeaponContent() { return weaponContent; }
    public void setWeaponContent(ItemStack weaponContent) { this.weaponContent = weaponContent; }

    public ItemStack getAttributeContent() { return attributeContent; }
    public void setAttributeContent(ItemStack attributeContent) { this.attributeContent = attributeContent; }

    // --- Serialization Helpers ---

    public String serializeArmor() { return serializeItems(armorContents); }
    public String serializeWeapon() { return serializeItem(weaponContent); }
    public String serializeAttribute() { return serializeItem(attributeContent); }

    public static String serializeItems(ItemStack[] items) {
        if (items == null) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeInt(items.length);
            for (ItemStack item : items) {
                dataOutput.writeObject(item);
            }
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static String serializeItem(ItemStack item) {
        if (item == null) return "";
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(item);
            dataOutput.close();
            return Base64Coder.encodeLines(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        }
    }

    public static ItemStack[] deserializeItems(String data) {
        if (data == null || data.isEmpty()) return new ItemStack[4];
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            int size = dataInput.readInt();
            ItemStack[] items = new ItemStack[size];
            for (int i = 0; i < size; i++) {
                items[i] = (ItemStack) dataInput.readObject();
            }
            dataInput.close();
            return items;
        } catch (Exception e) {
            return new ItemStack[4];
        }
    }

    public static ItemStack deserializeItem(String data) {
        if (data == null || data.isEmpty()) return null;
        try {
            ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(data));
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (Exception e) {
            return null;
        }
    }
}