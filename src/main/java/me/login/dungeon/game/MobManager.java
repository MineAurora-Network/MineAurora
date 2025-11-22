package me.login.dungeon.game;

import me.login.Login;
import me.login.dungeon.gui.DungeonGUI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Random;

public class MobManager {

    private static final Random random = new Random();
    public static final NamespacedKey DUNGEON_MOB_KEY = new NamespacedKey(Login.getPlugin(Login.class), "dungeon_mob");
    public static boolean IS_SPAWNING = false;

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    public static Entity spawnRoomMob(Location loc, int roomNumber) {
        if (loc == null || loc.getWorld() == null) return null;

        EntityType[] types = {EntityType.ZOMBIE, EntityType.WITHER_SKELETON, EntityType.HUSK, EntityType.VINDICATOR};
        EntityType type = types[random.nextInt(types.length)];

        IS_SPAWNING = true;
        Entity entity = loc.getWorld().spawnEntity(loc, type);
        IS_SPAWNING = false;

        if (!(entity instanceof LivingEntity)) return entity;
        LivingEntity le = (LivingEntity) entity;

        tagMob(le);

        // --- NO BABIES FIX ---
        if (le instanceof Zombie) ((Zombie) le).setBaby(false);
        if (le instanceof Ageable) ((Ageable) le).setAdult();
        // ---------------------

        String typeName = formatTypeName(type);
        double maxHealth = 20.0 + (roomNumber * 2);
        if (le.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            le.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(maxHealth);
        }
        le.setHealth(maxHealth);

        updateMobName(le, typeName);

        if (roomNumber >= 4 && roomNumber <= 6) {
            applyArmor(le, roomNumber);
        }

        return entity;
    }

    public static Zombie spawnBoss(Location loc) {
        IS_SPAWNING = true;
        Zombie boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        IS_SPAWNING = false;

        tagMob(boss);
        boss.setAdult(); // Boss is always adult

        if (boss.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null) {
            boss.getAttribute(Attribute.GENERIC_MAX_HEALTH).setBaseValue(400);
        }
        boss.setHealth(400);
        if (boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE) != null) {
            boss.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(10);
        }

        boss.getEquipment().setHelmet(createColoredArmor(Material.LEATHER_HELMET, Color.BLACK));
        boss.getEquipment().setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE, Color.BLACK));
        boss.getEquipment().setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS, Color.BLACK));
        boss.getEquipment().setBoots(createColoredArmor(Material.LEATHER_BOOTS, Color.BLACK));
        boss.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));

        updateMobName(boss, "Dungeon Boss");
        return boss;
    }

    public static Zombie spawnMinion(Location loc) {
        IS_SPAWNING = true;
        Zombie minion = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
        IS_SPAWNING = false;

        tagMob(minion);
        minion.setBaby(); // Minions ARE babies (per original design)

        minion.getEquipment().setHelmet(makeUnbreakable(Material.GOLDEN_HELMET));
        minion.getEquipment().setChestplate(makeUnbreakable(Material.GOLDEN_CHESTPLATE));
        minion.getEquipment().setLeggings(makeUnbreakable(Material.GOLDEN_LEGGINGS));
        minion.getEquipment().setBoots(makeUnbreakable(Material.GOLDEN_BOOTS));
        minion.getEquipment().setItemInMainHand(makeUnbreakable(Material.GOLDEN_SWORD));

        updateMobName(minion, "Boss Minion");
        return minion;
    }

    private static void tagMob(LivingEntity entity) {
        entity.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
        entity.setCustomNameVisible(true);
    }

    public static void updateMobName(LivingEntity entity) {
        String baseName = "Corrupted " + formatTypeName(entity.getType());
        if (entity instanceof Zombie && ((Zombie)entity).isBaby()) baseName = "Boss Minion";
        if (entity instanceof Zombie && ((Zombie)entity).getEquipment().getHelmet().getType() == Material.LEATHER_HELMET
                && ((LeatherArmorMeta)((Zombie)entity).getEquipment().getHelmet().getItemMeta()).getColor().equals(Color.BLACK)) {
            baseName = "Dungeon Boss";
        }
        updateMobName(entity, baseName);
    }

    public static void updateMobName(LivingEntity entity, String baseName) {
        int current = (int) Math.ceil(entity.getHealth());
        int max = (int) entity.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue();
        String format = "&a" + baseName + " &8(&c" + current + "&8/&c" + max + "&8)";
        entity.customName(LEGACY.deserialize(format));
    }

    public static ItemStack getKey() {
        Material mat = Material.getMaterial("OMINOUS_TRIAL_KEY");
        if (mat == null) mat = Material.TRIPWIRE_HOOK;

        ItemStack key = new ItemStack(mat);
        ItemMeta meta = key.getItemMeta();
        meta.displayName(Component.text("Ominous Trial Key", NamedTextColor.DARK_RED).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(DungeonGUI.ITEM_ID_KEY, PersistentDataType.STRING, "ominous_key");
        key.setItemMeta(meta);
        return key;
    }

    public static boolean isKey(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        // We strictly check the PDC ID, material check is secondary
        String id = item.getItemMeta().getPersistentDataContainer().get(DungeonGUI.ITEM_ID_KEY, PersistentDataType.STRING);
        return "ominous_key".equals(id);
    }

    private static String formatTypeName(EntityType type) {
        String name = type.name().toLowerCase().replace("_", " ");
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void applyArmor(LivingEntity entity, int room) {
        if (entity.getEquipment() == null) return;
        if (room == 4) {
            entity.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD));
        } else if (room == 5) {
            entity.getEquipment().setHelmet(new ItemStack(Material.IRON_HELMET));
            entity.getEquipment().setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
        } else if (room == 6) {
            entity.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET));
            entity.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE));
            entity.getEquipment().setLeggings(new ItemStack(Material.DIAMOND_LEGGINGS));
            entity.getEquipment().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
            entity.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
        }
    }

    private static ItemStack createColoredArmor(Material mat, Color color) {
        ItemStack item = new ItemStack(mat);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        meta.setColor(color);
        meta.setUnbreakable(true);
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeUnbreakable(Material mat) {
        ItemStack item = new ItemStack(mat);
        item.editMeta(m -> m.setUnbreakable(true));
        return item;
    }
}