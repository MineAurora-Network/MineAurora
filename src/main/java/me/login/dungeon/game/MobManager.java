package me.login.dungeon.game;

import me.login.Login;
import me.login.dungeon.utils.DungeonUtils;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

public class MobManager {

    public static final NamespacedKey DUNGEON_MOB_KEY = new NamespacedKey(Login.getPlugin(Login.class), "dungeon_mob");
    public static final NamespacedKey MUTATION_KEY = new NamespacedKey(Login.getPlugin(Login.class), "mob_mutation");
    public static final NamespacedKey UNDEAD_KEY = new NamespacedKey(Login.getPlugin(Login.class), "dungeon_undead");

    public static boolean IS_SPAWNING = false;
    public static final String KEY_TEXTURE = "9fd108383dfa5b02e86635609541520e4e158952d68c1c8f8f200ec7e88642d";
    public static final String TRAP_HEAD_TEXTURE = "f49e1a3110e35eaf742e3c134fa7e89e49b148f23f8e50aff26c57ce5afca9c9";
    public static final String BUFF_ORB_TEXTURE = "e93e2068617872c542ecda1d27df4ece91c699907bf327c4ddb85309412d3939";

    private static final Random random = new Random();

    public MobManager(Login plugin) {}

    public enum MobMutation {
        ZOMBIE_SOLDIER("Zombie Soldier", Color.fromRGB(0xFF9326)),
        ZOMBIE_KNIGHT("Zombie Knight", Color.fromRGB(0xFFE526)),
        CORRUPTED_ZOMBIE("Corrupted Zombie", Color.fromRGB(0x4AFF26)),
        ZOMBIE_LORD("Zombie Lord", Color.fromRGB(0xFF2626)),
        SKELETON_SOLDIER("Skeleton Soldier", Color.fromRGB(0xCF7223)),
        SKELETON_MASTER("Skeleton Master", Color.fromRGB(0xF76111)),
        SKELETON_LORD("Skeleton Lord", Color.fromRGB(0x11F79F)),
        WITHER_GUARD("Wither Guard", null),
        WITHERMANCER("Withermancer", null),
        END_GUARD("End Guard", null),
        NONE("Mob", null);

        String name;
        Color color;
        MobMutation(String name, Color color) { this.name = name; this.color = color; }
    }

    // ... (spawnRoomMob, pickMobType, applyMutation, updateMobName, applyArmor, createColoredArmor, setAttribute, spawnBoss, spawnMinion match original, omitted for brevity)
    public static Entity spawnRoomMob(Location loc, int roomId) { IS_SPAWNING = true; try { EntityType type = pickMobType(roomId); LivingEntity entity = (LivingEntity) loc.getWorld().spawnEntity(loc, type); if (entity instanceof Zombie) { ((Zombie) entity).setBaby(false); } entity.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1); entity.setRemoveWhenFarAway(false); applyMutation(entity, type); return entity; } finally { IS_SPAWNING = false; } }
    private static EntityType pickMobType(int roomId) { if (roomId <= 2) { if (random.nextDouble() < 0.80) { return random.nextBoolean() ? EntityType.ZOMBIE : EntityType.SKELETON; } else { return random.nextBoolean() ? EntityType.WITHER_SKELETON : EntityType.ENDERMAN; } } else { List<EntityType> types = Arrays.asList(EntityType.ZOMBIE, EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.ENDERMAN); return types.get(random.nextInt(types.size())); } }
    private static void applyMutation(LivingEntity entity, EntityType type) { MobMutation mutation = MobMutation.NONE; if (type == EntityType.ZOMBIE) { double r = random.nextDouble(); if (r < 0.30) mutation = MobMutation.ZOMBIE_SOLDIER; else if (r < 0.55) mutation = MobMutation.ZOMBIE_KNIGHT; else if (r < 0.80) mutation = MobMutation.CORRUPTED_ZOMBIE; else mutation = MobMutation.ZOMBIE_LORD; applyArmor(entity, mutation.color); if (mutation == MobMutation.ZOMBIE_SOLDIER) { setAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, 0.35); } else if (mutation == MobMutation.ZOMBIE_LORD) { entity.getEquipment().setItemInMainHand(new ItemStack(Material.IRON_SWORD)); setAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, 0.35); setAttribute(entity, Attribute.GENERIC_ATTACK_DAMAGE, 6.0); } } else if (type == EntityType.SKELETON) { double r = random.nextDouble(); if (r < 0.40) mutation = MobMutation.SKELETON_SOLDIER; else if (r < 0.70) mutation = MobMutation.SKELETON_MASTER; else mutation = MobMutation.SKELETON_LORD; applyArmor(entity, mutation.color); entity.getEquipment().setItemInMainHand(new ItemStack(Material.BOW)); if (mutation == MobMutation.SKELETON_LORD) { setAttribute(entity, Attribute.GENERIC_MAX_HEALTH, 40.0); entity.setHealth(40.0); setAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, 0.3); } } else if (type == EntityType.WITHER_SKELETON) { if (random.nextBoolean()) { mutation = MobMutation.WITHER_GUARD; ItemStack bow = new ItemStack(Material.BOW); bow.addEnchantment(Enchantment.FLAME, 1); entity.getEquipment().setItemInMainHand(bow); } else { mutation = MobMutation.WITHERMANCER; entity.getEquipment().setItemInMainHand(new ItemStack(Material.STONE_SWORD)); setAttribute(entity, Attribute.GENERIC_ATTACK_DAMAGE, 10.0); } } else if (type == EntityType.ENDERMAN) { mutation = MobMutation.END_GUARD; setAttribute(entity, Attribute.GENERIC_MOVEMENT_SPEED, 0.4); setAttribute(entity, Attribute.GENERIC_ATTACK_DAMAGE, 10.0); } entity.getPersistentDataContainer().set(MUTATION_KEY, PersistentDataType.STRING, mutation.name()); updateMobName(entity); }
    public static void updateMobName(LivingEntity entity) { if (entity.getPersistentDataContainer().has(MUTATION_KEY, PersistentDataType.STRING)) { String name = entity.getPersistentDataContainer().get(MUTATION_KEY, PersistentDataType.STRING); MobMutation m = MobMutation.valueOf(name); double health = Math.round(entity.getHealth() * 10.0) / 10.0; entity.customName(Component.text(m.name + " ", m.color != null ? NamedTextColor.nearestTo(me.login.dungeon.utils.DungeonUtils.bukkitToTextColor(m.color)) : NamedTextColor.RED).append(Component.text("[HP: " + health + "]", NamedTextColor.GRAY))); entity.setCustomNameVisible(true); } }
    private static void applyArmor(LivingEntity entity, Color color) { if (color == null) return; entity.getEquipment().setHelmet(createColoredArmor(Material.LEATHER_HELMET, color)); entity.getEquipment().setChestplate(createColoredArmor(Material.LEATHER_CHESTPLATE, color)); entity.getEquipment().setLeggings(createColoredArmor(Material.LEATHER_LEGGINGS, color)); entity.getEquipment().setBoots(createColoredArmor(Material.LEATHER_BOOTS, color)); }
    private static ItemStack createColoredArmor(Material mat, Color color) { ItemStack item = new ItemStack(mat); LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta(); meta.setColor(color); item.setItemMeta(meta); return item; }
    private static void setAttribute(LivingEntity entity, Attribute attr, double value) { if (entity.getAttribute(attr) != null) { entity.getAttribute(attr).setBaseValue(value); } }
    public static Zombie spawnBoss(Location loc) { IS_SPAWNING = true; try { Zombie boss = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE); boss.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1); boss.setAdult(); boss.setRemoveWhenFarAway(false); setAttribute(boss, Attribute.GENERIC_MAX_HEALTH, 500.0); boss.setHealth(500.0); setAttribute(boss, Attribute.GENERIC_MOVEMENT_SPEED, 0.25); setAttribute(boss, Attribute.GENERIC_ATTACK_DAMAGE, 12.0); boss.getEquipment().setHelmet(new ItemStack(Material.DIAMOND_HELMET)); boss.getEquipment().setChestplate(new ItemStack(Material.DIAMOND_CHESTPLATE)); boss.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD)); boss.customName(Component.text("§c§lDungeon Boss §7[HP: 500]")); boss.setCustomNameVisible(true); return boss; } finally { IS_SPAWNING = false; } }
    public static Entity spawnMinion(Location loc) { IS_SPAWNING = true; try { Zombie minion = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE); minion.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1); minion.setBaby(true); setAttribute(minion, Attribute.GENERIC_MAX_HEALTH, 20.0); minion.customName(Component.text("§cMinion")); return minion; } finally { IS_SPAWNING = false; } }

    public static Entity spawnUndeadSkeleton(Location loc) {
        IS_SPAWNING = true;
        try {
            Skeleton skel = (Skeleton) loc.getWorld().spawnEntity(loc, EntityType.SKELETON);
            // Feature 6: Add DUNGEON_MOB_KEY so they don't burn in sun (checked in Listener)
            skel.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
            skel.getPersistentDataContainer().set(UNDEAD_KEY, PersistentDataType.BYTE, (byte) 1);

            skel.customName(Component.text("Undead Skeleton", NamedTextColor.DARK_RED));
            skel.setCustomNameVisible(true);

            ItemStack bow = new ItemStack(Material.BOW);
            bow.addEnchantment(Enchantment.POWER, 3);
            skel.getEquipment().setItemInMainHand(bow);

            setAttribute(skel, Attribute.GENERIC_ATTACK_DAMAGE, 8.0);
            return skel;
        } finally {
            IS_SPAWNING = false;
        }
    }

    public static Entity spawnCryptZombie(Location loc) {
        IS_SPAWNING = true;
        try {
            Zombie zombie = (Zombie) loc.getWorld().spawnEntity(loc, EntityType.ZOMBIE);
            zombie.getPersistentDataContainer().set(DUNGEON_MOB_KEY, PersistentDataType.BYTE, (byte) 1);
            zombie.setAdult();
            zombie.customName(Component.text("Crypt Zombie", NamedTextColor.DARK_GRAY));
            zombie.setCustomNameVisible(true);

            zombie.getEquipment().setHelmet(new ItemStack(Material.TURTLE_HELMET));
            zombie.getEquipment().setChestplate(null);
            zombie.getEquipment().setLeggings(null);
            zombie.getEquipment().setBoots(null);

            zombie.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, Integer.MAX_VALUE, 0, false, false));

            return zombie;
        } finally {
            IS_SPAWNING = false;
        }
    }
}