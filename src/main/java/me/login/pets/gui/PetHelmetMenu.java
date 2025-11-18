package me.login.pets.gui;

import me.login.Login;
import me.login.pets.data.Pet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class PetHelmetMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Pet pet;
    private final Login plugin;

    // The single slot for the helmet (Middle of 2nd row)
    public static final int HELMET_SLOT = 13;

    public PetHelmetMenu(Player player, Pet pet, Login plugin) {
        this.pet = pet;
        this.plugin = plugin;

        Component title = MiniMessage.miniMessage().deserialize("<red><bold>Equip Helmet Required!</bold></red>");
        this.inventory = Bukkit.createInventory(this, 27, title); // 3 rows

        initializeItems();
    }

    private void initializeItems() {
        // 1. Create filler glass
        ItemStack filler = GuiUtils.createGuiItem(plugin, Material.RED_STAINED_GLASS_PANE, " ", Collections.emptyList());

        // 2. Fill ALL slots first
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // 3. Clear the middle slot (13)
        inventory.setItem(HELMET_SLOT, null);

        // 4. Check if the pet already has a helmet in its data and place it there
        // We assume index 3 of the armor array is the helmet (Boots, Leg, Chest, Helm)
        ItemStack[] currentArmor = pet.getArmorContents();
        if (currentArmor != null && currentArmor.length > 3 && currentArmor[3] != null) {
            inventory.setItem(HELMET_SLOT, currentArmor[3]);
        }

        // 5. Add an info icon? (Optional, but helpful)
        ItemStack info = GuiUtils.createGuiItem(plugin, Material.PAPER,
                "<yellow><bold>Sunlight Protection</bold></yellow>",
                Collections.singletonList("<gray>Undead pets burn in sunlight!</gray><gray>Place a helmet here to protect them.</gray>")
        );
        inventory.setItem(4, info);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public Pet getPet() {
        return pet;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}