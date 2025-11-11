package me.login.misc.firesale.model;

import org.bukkit.inventory.ItemStack;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a single firesale instance (active, pending, or historical).
 */
public class Firesale {

    private int saleId;
    private final ItemStack item;
    private final double price;
    private final int initialQuantity;
    private int remainingQuantity;
    private final Instant startTime;
    private Instant endTime;
    private final UUID creatorUuid;
    private final String creatorName;
    private SaleStatus status;
    private int totalSold;

    public Firesale(int saleId, ItemStack item, double price, int initialQuantity, int remainingQuantity,
                    Instant startTime, Instant endTime, UUID creatorUuid, String creatorName,
                    SaleStatus status, int totalSold) {
        this.saleId = saleId;
        this.item = item;
        this.price = price;
        this.initialQuantity = initialQuantity;
        this.remainingQuantity = remainingQuantity;
        this.startTime = startTime;
        this.endTime = endTime;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.status = status;
        this.totalSold = totalSold;
    }

    // Getters
    public int getSaleId() { return saleId; }
    public ItemStack getItem() { return item; }
    public double getPrice() { return price; }
    public int getInitialQuantity() { return initialQuantity; }
    public int getRemainingQuantity() { return remainingQuantity; }
    public Instant getStartTime() { return startTime; }
    public Instant getEndTime() { return endTime; }
    public UUID getCreatorUuid() { return creatorUuid; }
    public String getCreatorName() { return creatorName; }
    public SaleStatus getStatus() { return status; }
    public int getTotalSold() { return totalSold; }

    public long getTimeRemainingMillis() {
        if (status != SaleStatus.ACTIVE) return 0;
        long remaining = endTime.toEpochMilli() - Instant.now().toEpochMilli();
        return Math.max(0, remaining);
    }

    // Setters
    public void setSaleId(int saleId) { this.saleId = saleId; }
    public void setRemainingQuantity(int remainingQuantity) { this.remainingQuantity = remainingQuantity; }
    public void setEndTime(Instant endTime) { this.endTime = endTime; }
    public void setStatus(SaleStatus status) { this.status = status; }
    public void setTotalSold(int totalSold) { this.totalSold = totalSold; }
}