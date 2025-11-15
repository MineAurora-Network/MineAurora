package me.login.pets;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.*;

public class PetDebug {

    public enum Cat {
        SUMMON,
        AI,
        TARGET,
        FEEDING,
        COMBAT,
        CAPTURE,
        GENERAL
    }

    // Master toggle for all debugging
    private static boolean MASTER = true;

    // Optional: per-player debug categories
    private static final Map<UUID, EnumSet<Cat>> playerCategories = new HashMap<>();

    // -----------------------------
    // MASTER + CATEGORY CONTROL
    // -----------------------------

    public static void setMaster(boolean enable) {
        MASTER = enable;
        Bukkit.getLogger().info("[PET-DEBUG] MASTER = " + enable);
    }

    public static void setCategory(Cat cat, boolean enable) {
        Bukkit.getLogger().info("[PET-DEBUG] Category " + cat.name() + " = " + enable);

        for (UUID id : playerCategories.keySet()) {
            if (enable) playerCategories.get(id).add(cat);
            else playerCategories.get(id).remove(cat);
        }
    }

    // Per-player category control
    public static void setCategory(Player player, Cat cat, boolean enable) {
        UUID id = player.getUniqueId();
        playerCategories.putIfAbsent(id, EnumSet.noneOf(Cat.class));

        if (enable) playerCategories.get(id).add(cat);
        else playerCategories.get(id).remove(cat);

        Bukkit.getLogger().info("[PET-DEBUG][PLAYER:" + player.getName() + "] Category "
                + cat + " = " + enable);
    }

    // -----------------------------
    // DEBUG METHODS
    // -----------------------------

    /** Global debug — always allowed */
    public static void debug(Cat category, String msg) {
        if (!MASTER) return;
        Bukkit.getLogger().info("[PET-DEBUG][" + category.name() + "] " + msg);
    }

    /** Debug tied to player (Player object version) */
    public static void debugOwner(Player player, Cat category, String msg) {
        if (!MASTER || player == null) return;

        UUID id = player.getUniqueId();
        playerCategories.putIfAbsent(id, EnumSet.allOf(Cat.class));

        if (!playerCategories.get(id).contains(category)) return;

        Bukkit.getLogger().info("[PET-DEBUG][" + category + "][PLAYER:" + player.getName() + "] " + msg);
    }

    /** Debug tied to player (UUID version — REQUIRED BY YOUR CODE) */
    public static void debugOwner(UUID uuid, Cat category, String msg) {
        if (!MASTER || uuid == null) return;

        Player player = Bukkit.getPlayer(uuid);

        // If player is offline OR you still want logs → log anyway
        String playerName = (player != null ? player.getName() : uuid.toString());

        playerCategories.putIfAbsent(uuid, EnumSet.allOf(Cat.class));
        if (!playerCategories.get(uuid).contains(category)) return;

        Bukkit.getLogger().info("[PET-DEBUG][" + category + "][PLAYER:" + playerName + "] " + msg);
    }
}