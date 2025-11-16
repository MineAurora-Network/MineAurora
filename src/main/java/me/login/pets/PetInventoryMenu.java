package me.login.pets;

import me.login.pets.data.Pet;
import net.kyori.adventure.text.Component;
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

public class PetInventoryMenu implements InventoryHolder {

    private final Inventory inventory;
    private final Pet pet;
    private final boolean isAdmin;

    // GUI Slots
    public static final int[] ARMOR_SLOTS = {10, 11, 12, 13};
    public static final int WEAPON_SLOT = 15;

    public PetInventoryMenu(Player player, Pet pet, boolean isAdmin) {
        this.pet = pet;
        this.isAdmin = isAdmin;

        String titleKey = isAdmin ? "<dark_red><bold>ADMIN: " : "<bold>";
        Component title = MiniMessage.miniMessage().deserialize(titleKey + pet.getDisplayName() + "'s Gear</bold>");
        this.inventory = Bukkit.createInventory(this, 27, title); // 3 rows

        initializeItems();
    }

    private void initializeItems() {
        // 1. Create filler glass
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(MiniMessage.miniMessage().deserialize(" "));
        filler.setItemMeta(meta);

        // 2. Fill inventory with glass
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }

        // 3. Set empty slots
        for (int slot : ARMOR_SLOTS) {
            inventory.setItem(slot, null);
        }
        inventory.setItem(WEAPON_SLOT, null);

        // 4. Load saved items
        ItemStack[] armor = pet.getArmorContents();
        if (armor != null) {
            for (int i = 0; i < ARMOR_SLOTS.length; i++) {
                if (i < armor.length && armor[i] != null) {
                    inventory.setItem(ARMOR_SLOTS[i], armor[i]);
                }
            }
        }
        if (pet.getWeaponContent() != null) {
            inventory.setItem(WEAPON_SLOT, pet.getWeaponContent());
        }

        // 5. Add helper items
        ItemStack armorHelper = new ItemStack(Material.ARMOR_STAND);
        ItemMeta armorMeta = armorHelper.getItemMeta();
        armorMeta.displayName(MiniMessage.miniMessage().deserialize("<green>Armor Slots</green>"));
        armorMeta.lore(Collections.singletonList(MiniMessage.miniMessage().deserialize("<gray>Place pet armor here.</gray>")));
        armorHelper.setItemMeta(armorMeta);
        inventory.setItem(1, armorHelper);

        ItemStack weaponHelper = new ItemStack(Material.IRON_SWORD);
        ItemMeta weaponMeta = weaponHelper.getItemMeta();
        weaponMeta.displayName(MiniMessage.miniMessage().deserialize("<green>Weapon Slot</green>"));
        weaponMeta.lore(Collections.singletonList(MiniMessage.miniMessage().deserialize("<gray>Place a Sword or Axe here.</gray>")));
        weaponHelper.setItemMeta(weaponMeta);
        inventory.setItem(6, weaponHelper);

        if (isAdmin) {
            ItemStack adminHelper = new ItemStack(Material.REDSTONE_BLOCK);
            ItemMeta adminMeta = adminHelper.getItemMeta();
            adminMeta.displayName(MiniMessage.miniMessage().deserialize("<red><bold>ADMIN MODE</bold></red>"));
            adminMeta.lore(Collections.singletonList(MiniMessage.miniMessage().deserialize("<yellow>Shift-click items to delete them.</yellow>")));
            adminHelper.setItemMeta(adminMeta);
            inventory.setItem(26, adminHelper);
        }
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Pet getPet() {
        return pet;
    }

    public boolean isAdmin() {
        return isAdmin;
    }
}