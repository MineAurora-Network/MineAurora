package me.login.lifesteal.prestige;

import me.login.Login;
import me.login.lifesteal.ItemManager;
import me.login.lifesteal.LifestealManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.UUID;

public class HeartPrestigeManager {

    private final Login plugin;
    private final LifestealManager lifestealManager;
    private final ItemManager itemManager;
    private final HeartPrestigeLogger logger;

    public HeartPrestigeManager(Login plugin, LifestealManager lifestealManager, ItemManager itemManager, HeartPrestigeLogger logger) {
        this.plugin = plugin;
        this.lifestealManager = lifestealManager;
        this.itemManager = itemManager;
        this.logger = logger;
    }

    public int getPrestigeLevel(UUID uuid) {
        return lifestealManager.getPrestigeLevel(uuid);
    }

    public int getPrestigeLevel(Player player) {
        return getPrestigeLevel(player.getUniqueId());
    }

    public void attemptPrestige(Player player, int targetTier) {
        UUID uuid = player.getUniqueId();
        int currentLevel = getPrestigeLevel(uuid);

        // Ensure they are buying the next immediate level
        if (targetTier != currentLevel + 1) {
            player.sendMessage(itemManager.formatMessage("<red>Invalid prestige attempt."));
            return;
        }

        int cost = getCostForNextLevel(targetTier);

        // --- INVENTORY CHECK LOGIC ---
        int heartsFound = countHeartsInInventory(player);

        if (heartsFound >= cost) {
            // Remove Items
            if (removeHeartsFromInventory(player, cost)) {
                // Set new prestige level
                lifestealManager.setPrestigeLevel(uuid, targetTier);

                // Increase the max heart limit and update health immediately
                lifestealManager.updatePlayerHealth(player);

                player.sendMessage(itemManager.formatMessage("<green><bold>PRESTIGE UP!</bold> <gray>You are now Prestige Level <white>" + targetTier + "<gray>!"));
                player.sendMessage(itemManager.formatMessage("<green>Your max heart limit is now " + lifestealManager.getMaxHearts(uuid) + "!"));

                if (logger != null) {
                    logger.logPrestige(player.getName(), targetTier, cost);
                }
            }
        } else {
            player.sendMessage(itemManager.formatMessage("<red>You do not have enough Heart items in your inventory! (Need " + cost + ", Have " + heartsFound + ")"));
        }
    }

    private int countHeartsInInventory(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isHeartItem(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private boolean removeHeartsFromInventory(Player player, int amountToRemove) {
        int remaining = amountToRemove;
        ItemStack[] contents = player.getInventory().getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (isHeartItem(item)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null); // Remove stack
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
            }
            if (remaining <= 0) break;
        }
        return remaining == 0;
    }

    private boolean isHeartItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        if (!item.hasItemMeta()) return false;
        // Check for the specific PersistentData tag assigned in ItemManager to identify real hearts
        return item.getItemMeta().getPersistentDataContainer().has(itemManager.heartItemKey, PersistentDataType.BYTE);
    }

    // Logic to calculate cost
    public int getCostForNextLevel(int tier) {
        // Base 25 hearts for first prestige, +5 for each subsequent level
        // Tier 1 = 25, Tier 2 = 30, etc.
        return 25 + ((tier - 1) * 5);
    }

    public LifestealManager getLifestealManager() {
        return lifestealManager;
    }
}