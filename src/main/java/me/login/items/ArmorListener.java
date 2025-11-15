package me.login.items;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class ArmorListener implements Listener {

    // Reference to ArmorManager needed for the new event
    private final ArmorManager armorManager;

    public ArmorListener(Login plugin, ArmorManager armorManager) {
        this.armorManager = armorManager;
    }

    /**
     * Blocks vanilla crafting recipes (like dyeing) for custom armor.
     */
    @EventHandler
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        boolean hasCustomArmor = false;
        boolean hasVanillaDye = false;

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (armorManager.isCustomArmor(item)) {
                hasCustomArmor = true;
            } else if (item.getType().name().endsWith("_DYE")) {
                hasVanillaDye = true;
            }
        }

        // If a player tries to craft custom armor with a vanilla dye, block it.
        if (hasCustomArmor && hasVanillaDye) {
            inventory.setResult(null);
        }
    }

    /**
     * Handles custom durability for our armor.
     */
    @EventHandler
    public void onItemDamage(PlayerItemDamageEvent event) {
        ItemStack item = event.getItem();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        if (!pdc.has(ArmorManager.MAX_DURABILITY_KEY, PersistentDataType.INTEGER) ||
                !pdc.has(ArmorManager.CURRENT_DURABILITY_KEY, PersistentDataType.INTEGER)) {
            return;
        }

        event.setCancelled(true);

        int current = pdc.get(ArmorManager.CURRENT_DURABILITY_KEY, PersistentDataType.INTEGER);
        int max = pdc.get(ArmorManager.MAX_DURABILITY_KEY, PersistentDataType.INTEGER);
        int damage = event.getDamage();

        int newCurrent = current - damage;

        if (newCurrent <= 0) {
            Player player = event.getPlayer();
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1f, 1f);
            player.sendActionBar(MiniMessage.miniMessage().deserialize("<red>Your armor piece has broken!"));
            item.setAmount(0);
            return;
        }

        pdc.set(ArmorManager.CURRENT_DURABILITY_KEY, PersistentDataType.INTEGER, newCurrent);
        updateLore(meta, newCurrent, max);
        item.setItemMeta(meta);
    }

    private void updateLore(ItemMeta meta, int current, int max) {
        List<Component> originalLore = meta.lore();
        if (originalLore == null) return;

        List<Component> newLore = new ArrayList<>();
        String durPattern = "Durability:";

        for (Component line : originalLore) {
            String plainText = PlainTextComponentSerializer.plainText().serialize(line);

            if (plainText.contains(durPattern)) {
                String newLine = "<white>Durability: <green>" + current + "<gray>/<green>" + max;
                newLore.add(MiniMessage.miniMessage().deserialize(newLine).decoration(TextDecoration.ITALIC, false));
            } else {
                newLore.add(line.decoration(TextDecoration.ITALIC, false)); // Ensure no italics
            }
        }
        meta.lore(newLore);
    }
}