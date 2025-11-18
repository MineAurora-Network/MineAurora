package me.login.misc.dailyquests;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag; // IMPORTED
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

public class QuestsGui {

    private final QuestsModule module;
    private final QuestManager manager;
    public static final String GUI_TITLE = "Daily Quests";
    private final ItemStack FILLER_GLASS = createFillerGlass();

    public QuestsGui(QuestsModule module) {
        this.module = module;
        this.manager = module.getQuestManager();
    }

    public void openMainQuestGui(Player p) {
        PlayerQuestData data = manager.getPlayerQuestData(p);
        if (data == null) {
            p.sendMessage(manager.getServerPrefix().append(Component.text("Your quest data is still loading, please try again in a moment.", NamedTextColor.RED)));
            return;
        }

        // This is the new logic: check for a reset every time the GUI is opened.
        manager.checkAndResetDailies(data);

        // --- FIX: Changed 18 (2 rows) to 27 (3 rows) ---
        Inventory inv = Bukkit.createInventory(null, 27, Component.text(GUI_TITLE)); // 3 rows

        // Fill with glass
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, FILLER_GLASS);
        }

        // Slot 11: Easy (Middle of 2nd row)
        inv.setItem(11, createQuestItem(data, QuestType.EASY));
        // Slot 13: Hard (Middle of 2nd row)
        inv.setItem(13, createQuestItem(data, QuestType.HARD));
        // Slot 15: Extreme (Middle of 2nd row)
        inv.setItem(15, createQuestItem(data, QuestType.EXTREME));

        p.openInventory(inv);
    }

    private ItemStack createQuestItem(PlayerQuestData data, QuestType type) {
        Material mat;
        Component name;
        Quest quest = null;
        boolean isCompleted = data.getCompletedQuestTypes().contains(type);
        boolean isActive = false;
        boolean canAccept = data.getActiveQuest() == null && !isCompleted;

        switch (type) {
            case EASY:
                mat = Material.LIME_WOOL;
                name = Component.text("Easy Quest", NamedTextColor.GREEN, TextDecoration.BOLD);
                quest = data.getDailyEasyQuest();
                break;
            case HARD:
                mat = Material.YELLOW_WOOL;
                name = Component.text("Hard Quest", NamedTextColor.YELLOW, TextDecoration.BOLD);
                quest = data.getDailyHardQuest();
                break;
            case EXTREME:
                mat = Material.RED_WOOL;
                name = Component.text("Extreme Quest", NamedTextColor.RED, TextDecoration.BOLD);
                quest = data.getDailyExtremeQuest();
                break;
            default:
                mat = Material.BARRIER;
                name = Component.text("Error", NamedTextColor.RED);
        }

        if (quest == null) {
            mat = Material.BARRIER;
            name = Component.text("No Quest Available", NamedTextColor.RED);
        } else if (data.getActiveQuest() != null && data.getActiveQuest().getId().equals(quest.getId())) {
            isActive = true;
            mat = (type == QuestType.EASY) ? Material.LIME_STAINED_GLASS_PANE : (type == QuestType.HARD) ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE;
        }


        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();

        // --- FIX: Remove italic from display name ---
        meta.displayName(name.decoration(TextDecoration.ITALIC, false));

        // --- FIX: Add ItemFlags to hide default Bukkit lore (like "Dyed") ---
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DYE);

        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty()); // Empty line for spacing

        if (quest == null) {
            lore.add(Component.text("Could not assign a new quest.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        } else if (isCompleted) {
            lore.add(Component.text("Objective: ", NamedTextColor.GRAY)
                    .append(Component.text(quest.getObjectiveDescription()).decoration(TextDecoration.STRIKETHROUGH, true))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("COMPLETED!", NamedTextColor.GREEN, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        } else if (isActive) {
            lore.add(Component.text("Objective: ", NamedTextColor.GRAY)
                    .append(Component.text(quest.getObjectiveDescription(), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("Progress: ", NamedTextColor.GRAY)
                    .append(Component.text(data.getActiveQuestProgress() + " / " + quest.getRequiredAmount(), NamedTextColor.YELLOW))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            lore.add(Component.text("ACTIVE QUEST", NamedTextColor.AQUA, TextDecoration.BOLD).decoration(TextDecoration.ITALIC, false));
        } else {
            // Not completed, not active
            lore.add(Component.text("Objective: ", NamedTextColor.GRAY)
                    .append(Component.text(quest.getObjectiveDescription(), NamedTextColor.WHITE))
                    .decoration(TextDecoration.ITALIC, false));
            lore.add(Component.empty());
            addRewardsToLore(lore, quest); // This method now also removes italics

            if (canAccept) {
                lore.add(Component.empty());
                lore.add(Component.text("SHIFT-CLICK", NamedTextColor.YELLOW, TextDecoration.BOLD)
                        .append(Component.text(" to accept this quest.", NamedTextColor.GRAY))
                        .decoration(TextDecoration.ITALIC, false));
            } else {
                lore.add(Component.empty());
                lore.add(Component.text("Another quest is already active.", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false));
            }
        }

        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void addRewardsToLore(List<Component> lore, Quest quest) {
        // --- FIX: All lines now have italics removed ---
        lore.add(Component.text("Rewards:", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        if (quest.getRewardCash() > 0) {
            lore.add(Component.text("- ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("$" + String.format("%,.0f", quest.getRewardCash()), NamedTextColor.AQUA))
                    .decoration(TextDecoration.ITALIC, false));
        }
        if (quest.getRewardTokens() > 0) {
            lore.add(Component.text("- ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(quest.getRewardTokens() + " Tokens", NamedTextColor.GOLD))
                    .decoration(TextDecoration.ITALIC, false));
        }
    }

    private static ItemStack createFillerGlass() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" ").decoration(TextDecoration.ITALIC, false)); // Remove italic from glass
        item.setItemMeta(meta);
        return item;
    }
}