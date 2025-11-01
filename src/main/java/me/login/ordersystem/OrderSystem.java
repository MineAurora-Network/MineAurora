package me.login.ordersystem;

import club.minnced.discord.webhook.WebhookClient;
import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


public class OrderSystem implements Listener {

    private final Login plugin;
    private final OrdersDatabase ordersDatabase;
    private final WebhookClient logWebhook;
    private final List<ItemStack> orderableItems = new ArrayList<>();

    // GUI identification constants
    public static final String GUI_CREATE_METADATA = "OrderCreateGUI";
    public static final String GUI_MENU_METADATA = "OrderMenuGUI";
    public static final String GUI_MANAGE_METADATA = "OrderManageGUI";
    public static final String GUI_SEARCH_METADATA = "OrderSearchGUI";
    public static final String GUI_MENU_SEARCH_METADATA = "OrderMenuSearchGUI";
    // --- ADDED ---
    public static final String GUI_ADMIN_METADATA = "OrderAdminMenuGUI";


    public OrderSystem(Login plugin, OrdersDatabase ordersDatabase, WebhookClient logWebhook) {
        this.plugin = plugin;
        this.ordersDatabase = ordersDatabase;
        this.logWebhook = logWebhook;
        loadOrderableItems();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    // --- Getters ---
    public Login getPlugin() { return plugin; }
    public OrdersDatabase getOrdersDatabase() { return ordersDatabase; }
    public List<ItemStack> getOrderableItems() { return new ArrayList<>(orderableItems); }


    // --- Logging (Unchanged) ---
    public void sendLog(String message) {
        plugin.getLogger().info("[OrderSystem Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (logWebhook != null) { logWebhook.send("[OrderSystem] " + message).exceptionally(error -> { plugin.getLogger().warning("Failed send order webhook: " + error.getMessage()); return null; }); }
    }

    // --- loadOrderableItems (Unchanged) ---
    private void loadOrderableItems() { /* ... */ List<Material> allowedMaterials=Arrays.stream(Material.values()).filter(m->m.isItem()&&!m.isLegacy()).collect(Collectors.toList()); for(Material mat:allowedMaterials){if(mat==Material.AIR||mat==Material.COMMAND_BLOCK||mat==Material.CHAIN_COMMAND_BLOCK||mat==Material.REPEATING_COMMAND_BLOCK||mat==Material.COMMAND_BLOCK_MINECART||mat==Material.STRUCTURE_BLOCK||mat==Material.STRUCTURE_VOID||mat==Material.JIGSAW||mat==Material.BARRIER||mat==Material.LIGHT||mat==Material.BEDROCK||mat==Material.END_PORTAL_FRAME||mat==Material.SPAWNER||mat.name().contains("LEGACY")||mat.name().endsWith("_SPAWN_EGG")){continue;}orderableItems.add(new ItemStack(mat));} plugin.getLogger().info("Loaded " + orderableItems.size() + " orderable items."); }

    // --- Central Event Handlers (MODIFIED) ---
    // This handler is just a backup check. The main handlers are in the specific classes.
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        String title = event.getView().getTitle();

        // Check if the click is in any of our GUIs.
        if (player.hasMetadata(GUI_MENU_METADATA) && title.startsWith(ChatColor.GRAY + "Active Orders")) {
            // Let OrderMenu handle
        } else if (player.hasMetadata(GUI_MANAGE_METADATA) && title.startsWith(ChatColor.GRAY + "Manage Orders")) {
            // Let OrderManage handle
        } else if (player.hasMetadata(GUI_CREATE_METADATA) && title.startsWith(ChatColor.GRAY + "Create Order")) {
            // Let OrderCreate handle
        } else if (player.hasMetadata(GUI_SEARCH_METADATA) && title.startsWith(ChatColor.GRAY + "Search:")) {
            // Let OrderCreate handle
        } else if (player.hasMetadata(GUI_MENU_SEARCH_METADATA) && title.startsWith(ChatColor.GRAY + "Search:")) {
            // Let OrderMenu handle its search results
        }
        // --- ADDED ---
        else if (player.hasMetadata(GUI_ADMIN_METADATA) && title.startsWith(ChatColor.DARK_RED + "Admin: Active Orders")) {
            // Let OrderAdminMenu handle
        }
    }

    // --- REMOVED: onInventoryClose handler ---
    /*
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        // ... (Handler removed) ...
    }
    */
    // --- END REMOVAL ---


    // Static Helper Method (Unchanged)
    public static ItemStack createGuiItem(Material material, String name, List<String> lore) { /* ... */ ItemStack i=new ItemStack(material,1);ItemMeta m=i.getItemMeta();if(m!=null){m.setDisplayName(name);if(lore!=null)m.setLore(lore);i.setItemMeta(m);}return i; }
}