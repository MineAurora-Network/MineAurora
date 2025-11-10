package me.login.ordersystem.data;

import org.bukkit.inventory.ItemStack;
import java.util.List;

/**
 * A simple data object to hold items and money
 * to be delivered to a player when they log in.
 * (Points 4, 5)
 */
public class OfflineDelivery {
    private final double refundAmount;
    private final List<ItemStack> items;

    public OfflineDelivery(double refundAmount, List<ItemStack> items) {
        this.refundAmount = refundAmount;
        this.items = items;
    }

    public double getRefundAmount() {
        return refundAmount;
    }

    public List<ItemStack> getItems() {
        return items;
    }
}