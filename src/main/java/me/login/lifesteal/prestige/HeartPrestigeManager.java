package me.login.lifesteal.prestige;

import me.login.Login;
import me.login.lifesteal.ItemManager;
import me.login.lifesteal.LifestealManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class HeartPrestigeManager {

    private final Login plugin;
    private final LifestealManager lifestealManager;
    private final ItemManager itemManager;
    private final HeartPrestigeLogger logger;

    // Config: Base cost 25, +5 per level
    private final int STARTING_COST = 25;
    private final int COST_INCREMENT = 5;
    private final int MAX_PRESTIGE_LEVEL = 7; // Slots 10-16 = 7 levels

    public HeartPrestigeManager(Login plugin, LifestealManager lifestealManager, ItemManager itemManager, HeartPrestigeLogger logger) {
        this.plugin = plugin;
        this.lifestealManager = lifestealManager;
        this.itemManager = itemManager;
        this.logger = logger;
    }

    public int getPrestigeLevel(Player player) {
        return lifestealManager.getPrestigeLevel(player.getUniqueId());
    }

    public int getCostForNextLevel(int currentLevel) {
        return STARTING_COST + (currentLevel * COST_INCREMENT);
    }

    public boolean canPrestige(Player player) {
        return getPrestigeLevel(player) < MAX_PRESTIGE_LEVEL;
    }

    public void attemptPrestige(Player player) {
        int currentLevel = getPrestigeLevel(player);

        if (currentLevel >= MAX_PRESTIGE_LEVEL) {
            player.sendMessage(itemManager.formatMessage("<red>You have reached the maximum prestige level!"));
            return;
        }

        int nextLevel = currentLevel + 1;
        int heartItemCost = getCostForNextLevel(currentLevel);

        // Check inventory for Heart Items
        if (!hasEnoughHearts(player, heartItemCost)) {
            Component msg = Component.text("You do not have enough heart items!", NamedTextColor.RED)
                    .append(Component.newline())
                    .append(Component.text("Required: " + heartItemCost + " Hearts in inventory.", NamedTextColor.YELLOW));
            player.sendMessage(itemManager.formatMessage(msg));
            return;
        }

        // Proceed with Prestige
        removeHeartItems(player, heartItemCost);

        // Update Level
        lifestealManager.setPrestigeLevel(player.getUniqueId(), nextLevel);

        // Effect: Max Health Cap increases automatically in LifestealManager.getMaxHearts()
        // We also grant 1 heart immediately as a "reward" or fill the new slot?
        // "increase 1 from max heart limit" -> done via logic.
        // "add 5 hearts per slot" -> handled in cost logic.

        // Give feedback
        Component successMsg = Component.text("Prestige Successful!", NamedTextColor.GREEN)
                .append(Component.newline())
                .append(Component.text("New Max Heart Limit: ", NamedTextColor.GRAY))
                .append(Component.text((lifestealManager.getMaxHearts(player.getUniqueId())), NamedTextColor.GOLD));

        player.sendMessage(itemManager.formatMessage(successMsg));

        logger.logPrestige(player.getName(), nextLevel, heartItemCost);
    }

    private boolean hasEnoughHearts(Player player, int amount) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(itemManager.heartItemKey, PersistentDataType.BYTE)) {
                    count += item.getAmount();
                }
            }
        }
        return count >= amount;
    }

    private void removeHeartItems(Player player, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() != Material.AIR && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(itemManager.heartItemKey, PersistentDataType.BYTE)) {
                    if (item.getAmount() <= remaining) {
                        remaining -= item.getAmount();
                        item.setAmount(0);
                    } else {
                        item.setAmount(item.getAmount() - remaining);
                        remaining = 0;
                    }
                    if (remaining <= 0) break;
                }
            }
        }
    }
}