package me.login.ordersystem.system;

import me.login.Login;
import me.login.ordersystem.data.OrdersDatabase; // --- FIX: Correct import ---
import me.login.ordersystem.util.OrderLogger;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Manages core system logic, primarily the list of orderable items.
 * (Refactored for Point 13)
 */
public class OrderSystem {

    private final Login plugin;
    private final OrdersDatabase ordersDatabase;
    private final OrderLogger logger;
    private final List<ItemStack> orderableItems = new ArrayList<>();

    public OrderSystem(Login plugin, OrdersDatabase ordersDatabase, OrderLogger logger) {
        this.plugin = plugin;
        this.ordersDatabase = ordersDatabase;
        this.logger = logger;
        loadOrderableItems();
    }

    // --- Getters ---
    public Login getPlugin() { return plugin; }
    public OrdersDatabase getOrdersDatabase() { return ordersDatabase; }
    public List<ItemStack> getOrderableItems() { return new ArrayList<>(orderableItems); }
    public OrderLogger getLogger() { return logger; }


    private void loadOrderableItems() {
        List<Material> allowedMaterials = Arrays.stream(Material.values())
                .filter(m -> m.isItem() && !m.isLegacy())
                .collect(Collectors.toList());

        for (Material mat : allowedMaterials) {
            // Exclude non-orderable items
            if (mat == Material.AIR ||
                    mat == Material.COMMAND_BLOCK || mat == Material.CHAIN_COMMAND_BLOCK || mat == Material.REPEATING_COMMAND_BLOCK ||
                    mat == Material.COMMAND_BLOCK_MINECART || mat == Material.STRUCTURE_BLOCK || mat == Material.STRUCTURE_VOID ||
                    mat == Material.JIGSAW || mat == Material.BARRIER || mat == Material.LIGHT ||
                    mat == Material.BEDROCK || mat == Material.END_PORTAL_FRAME || mat == Material.SPAWNER ||
                    mat.name().contains("LEGACY") || mat.name().endsWith("_SPAWN_EGG")) {
                continue;
            }
            orderableItems.add(new ItemStack(mat));
        }
        plugin.getLogger().info("[OrderSystem] Loaded " + orderableItems.size() + " orderable items.");
    }
}