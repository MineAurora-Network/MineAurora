//
// File: me/login/moderation/commands/CheckInvCommand.java
// (Updated with new layout)
//
package me.login.moderation;

import me.login.Login;
import me.login.moderation.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

public class CheckInvCommand implements CommandExecutor {

    private final Login plugin;

    public CheckInvCommand(Login plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be used by players.");
            return true;
        }

        Component prefix = Utils.getServerPrefix(plugin);
        boolean isAdmin = label.equalsIgnoreCase("admincheckinv");

        if (isAdmin && !sender.hasPermission("staff.admincheckinv")) {
            Utils.sendComponent(sender, prefix.append(Component.text("You do not have permission to use this command.", NamedTextColor.RED)));
            return true;
        }
        if (!isAdmin && !sender.hasPermission("staff.checkinv")) {
            Utils.sendComponent(sender, prefix.append(Component.text("You do not have permission to use this command.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 1) {
            Utils.sendComponent(sender, prefix.append(Component.text("Usage: /" + label + " <player>", NamedTextColor.RED)));
            return true;
        }

        Player target = Bukkit.getPlayer(args[0]);
        if (target == null || !target.isOnline()) {
            Utils.sendComponent(sender, prefix.append(Component.text("Player is not online.", NamedTextColor.RED)));
            return true;
        }

        Player staff = (Player) sender;
        openInventory(staff, target, isAdmin);
        return true;
    }

    private void openInventory(Player staff, Player target, boolean isAdmin) {
        // GUI titles still use legacy color codes, which is standard
        String title = Utils.color("&8Inventory: " + target.getName() + (isAdmin ? " (Admin)" : ""));
        Inventory inv = Bukkit.createInventory(null, 54, title); // 6 rows

        PlayerInventory targetInv = target.getInventory();

        // Row 1: Armor and Hands
        inv.setItem(0, targetInv.getHelmet());
        inv.setItem(1, targetInv.getChestplate());
        inv.setItem(2, targetInv.getLeggings());
        inv.setItem(3, targetInv.getBoots());
        // Slots 4, 5, 6 empty
        inv.setItem(7, targetInv.getItemInMainHand());
        inv.setItem(8, targetInv.getItemInOffHand());

        // Row 2: Glass Panes
        ItemStack filler = Utils.getFillerGlass();
        for (int i = 9; i <= 17; i++) {
            inv.setItem(i, filler);
        }

        // Row 3: Player Hotbar (slots 0-8)
        for (int i = 0; i <= 8; i++) {
            int guiSlot = i + 18; // Player slot 0 -> GUI slot 18
            inv.setItem(guiSlot, targetInv.getItem(i));
        }

        // Rows 4-6: Main Inventory (Player slots 9-35)
        for (int i = 9; i <= 35; i++) {
            // Player slot 9 -> GUI slot 27
            // Player slot 35 -> GUI slot 53
            int guiSlot = i + 18;
            inv.setItem(guiSlot, targetInv.getItem(i));
        }

        // Row 6, Middle: Barrier
        ItemStack barrier = new ItemStack(Material.BARRIER);
        ItemMeta meta = barrier.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Utils.color("&cClose"));
            barrier.setItemMeta(meta);
        }
        inv.setItem(49, barrier); // Middle of 6th row (overwrites player slot 31)

        // Add to tracking maps
        plugin.getViewingInventories().put(staff.getUniqueId(), target.getUniqueId());
        if (isAdmin) {
            plugin.getAdminCheckMap().put(staff.getUniqueId(), true);
        }

        staff.openInventory(inv);
    }
}