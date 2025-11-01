//
// File: me/login/moderation/ModerationListener.java
// (Updated with new mute message format)
//
package me.login.moderation;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;

public class ModerationListener implements Listener {

    private final Login plugin;
    private final ModerationDatabase database;

    public ModerationListener(Login plugin, ModerationDatabase database) {
        this.plugin = plugin;
        this.database = database;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        Map<String, Object> muteInfo = database.getActiveMuteInfo(player.getUniqueId());

        if (muteInfo != null) {
            event.setCancelled(true);

            // --- UPDATED MESSAGE FORMAT ---
            String reason = (String) muteInfo.get("reason");
            long endTime = (long) muteInfo.get("end_time");
            String timeLeft = Utils.formatTimeLeftShort(endTime); // Use new method
            String discordLink = plugin.getConfig().getString("discord-server-link", "your-discord.gg"); // Get link from config

            String message = "&c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬\n" +
                    "&cYou are currently muted for &f" + reason + "&c on the server.\n" +
                    "&7Your mute will expire in &f" + timeLeft + "\n" +
                    "&7Appeal at: &f" + discordLink + "\n" +
                    " &c▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬";

            // Send the new message format (does not use server_prefix)
            player.sendMessage(Utils.color(message));
            // --- END UPDATE ---
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Component prefix = Utils.getServerPrefix(plugin);
        UUID playerUUID = event.getPlayer().getUniqueId();
        String ipAddress = event.getAddress().getHostAddress();

        // 1. Check for UUID Ban
        Map<String, Object> banInfo = database.getActiveBanInfo(playerUUID);
        if (banInfo != null) {
            String reason = (String) banInfo.get("reason");
            long endTime = (long) banInfo.get("end_time");

            Component kickMessage = prefix.append(Component.text("\nYou are banned from this server.", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)))
                    .append(Component.newline())
                    .append(Component.text("Expires: ", NamedTextColor.RED).append(Component.text(Utils.formatTimeLeft(endTime), NamedTextColor.WHITE)));

            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
            return;
        }

        // 2. Check for IP Ban
        Map<String, Object> ipBanInfo = database.getActiveIpBanInfo(ipAddress);
        if (ipBanInfo != null) {
            String reason = (String) ipBanInfo.get("reason");
            long endTime = (long) ipBanInfo.get("end_time");

            Component kickMessage = prefix.append(Component.text("\nYour IP address is banned from this server.", NamedTextColor.RED))
                    .append(Component.newline())
                    .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)))
                    .append(Component.newline())
                    .append(Component.text("Expires: ", NamedTextColor.RED).append(Component.text(Utils.formatTimeLeft(endTime), NamedTextColor.WHITE)));

            event.disallow(PlayerLoginEvent.Result.KICK_BANNED, kickMessage);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player staff = (Player) event.getWhoClicked();
        UUID staffUUID = staff.getUniqueId();

        if (!plugin.getViewingInventories().containsKey(staffUUID)) {
            return; // Not in a special inventory
        }

        String title = event.getView().getTitle();
        if (!title.startsWith(Utils.color("&8Inventory: "))) {
            return;
        }

        boolean isAdmin = plugin.isAdminChecking(staffUUID);
        int rawSlot = event.getRawSlot();
        Inventory clickedInv = event.getClickedInventory();
        Inventory topInv = event.getView().getTopInventory();

        if (clickedInv != null && clickedInv.equals(topInv)) {
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && (clickedItem.getType() == Material.BARRIER || clickedItem.getType() == Material.GRAY_STAINED_GLASS_PANE)) {
                event.setCancelled(true);
                if (clickedItem.getType() == Material.BARRIER) {
                    staff.closeInventory();
                }
                return;
            }

            if (!isAdmin) {
                event.setCancelled(true);
                return;
            }

            if (isAdmin) {
                if (event.getAction().name().contains("PLACE") || event.getAction().name().contains("SWAP")) {
                    event.setCancelled(true);
                    return;
                }

                UUID targetUUID = plugin.getViewingInventories().get(staffUUID);
                Player target = plugin.getServer().getPlayer(targetUUID);
                if (target == null || !target.isOnline()) {
                    staff.sendMessage(Utils.color("&cTarget player is no longer online."));
                    event.setCancelled(true);
                    staff.closeInventory();
                    return;
                }

                ItemStack itemToRemove = null;
                int playerInvSlot = -1;

                if (rawSlot >= 0 && rawSlot <= 3) { // Armor
                    int armorSlot = 3 - rawSlot;
                    ItemStack[] armor = target.getInventory().getArmorContents();
                    itemToRemove = armor[armorSlot] != null ? armor[armorSlot].clone() : null;
                    armor[armorSlot] = null;
                    target.getInventory().setArmorContents(armor);
                } else if (rawSlot == 7) { // Main hand
                    playerInvSlot = target.getInventory().getHeldItemSlot();
                    itemToRemove = target.getInventory().getItemInMainHand() != null ? target.getInventory().getItemInMainHand().clone() : null;
                    if (playerInvSlot != -1 && itemToRemove != null && itemToRemove.equals(target.getInventory().getItem(playerInvSlot))) {
                        target.getInventory().setItemInMainHand(null);
                    } else {
                        target.getInventory().setItemInMainHand(null);
                    }
                } else if (rawSlot == 8) { // Off hand
                    itemToRemove = target.getInventory().getItemInOffHand() != null ? target.getInventory().getItemInOffHand().clone() : null;
                    target.getInventory().setItemInOffHand(null);
                } else if (rawSlot >= 18 && rawSlot <= 26) { // Row 3: Hotbar (Player 0-8)
                    playerInvSlot = rawSlot - 18;
                } else if (rawSlot >= 27 && rawSlot <= 53) { // Rows 4-6: Main Inv (Player 9-35)
                    playerInvSlot = rawSlot - 18;
                }

                if (playerInvSlot != -1) {
                    itemToRemove = target.getInventory().getItem(playerInvSlot) != null ? target.getInventory().getItem(playerInvSlot).clone() : null;
                    target.getInventory().setItem(playerInvSlot, null);
                }

                if (itemToRemove != null && itemToRemove.getType() != Material.AIR) {
                    staff.sendMessage(Utils.color(Utils.STAFF_BROADCAST_PREFIX_LEGACY + "&aRemoved item &f" + itemToRemove.getType().name() + " &afrom target."));
                } else {
                    event.setCancelled(true);
                }
            }

        } else if (clickedInv != null && clickedInv.equals(staff.getInventory())) {
            if (isAdmin && event.getAction().name().contains("MOVE_TO_OTHER_INVENTORY")) {
                event.setCancelled(true);
            }
        }
    }


    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player staff = (Player) event.getPlayer();
        plugin.getViewingInventories().remove(staff.getUniqueId());
        plugin.getAdminCheckMap().remove(staff.getUniqueId());
    }
}