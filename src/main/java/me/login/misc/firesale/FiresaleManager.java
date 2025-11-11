package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.database.FiresaleDatabase;
import me.login.misc.firesale.item.FiresaleItemManager;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import me.login.scoreboard.SkriptUtils;
import me.login.scoreboard.SkriptVarParse;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FiresaleManager {

    private final Login plugin;
    private final FiresaleDatabase database;
    private final FiresaleLogger logger;
    private final FiresaleItemManager itemManager;
    private final MiniMessage miniMessage;
    private final Component serverPrefix;

    private final ConcurrentHashMap<Integer, Firesale> activeSales = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, BukkitTask> saleTasks = new ConcurrentHashMap<>();
    private BukkitTask globalUpdateTask;

    private static final int MAX_ACTIVE_SALES = 4;
    private static final Pattern TIME_PATTERN = Pattern.compile("(\\d+)([smhd])");
    private static final String SKRIPT_CURRENCY_VAR = "credits.%player's uuid%";

    public FiresaleManager(Login plugin, FiresaleDatabase database, FiresaleLogger logger, FiresaleItemManager itemManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.itemManager = itemManager;
        this.miniMessage = plugin.getComponentSerializer();
        this.serverPrefix = miniMessage.deserialize(plugin.getServerPrefix());
    }

    public void loadSales() {
        activeSales.clear();
        for (Firesale sale : database.loadActiveSales()) {
            activeSales.put(sale.getSaleId(), sale);
            scheduleSaleTasks(sale); // Schedule start/end tasks for this sale
        }
    }

    /**
     * Starts the main 1-second update scheduler for GUI timers and player join alerts.
     */
    public void startScheduler() {
        this.globalUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                for (Firesale sale : getActiveSales()) {
                    if (sale.getStatus() == SaleStatus.ACTIVE && sale.getEndTime().isBefore(now)) {
                        // Handle expiration
                        endSale(sale, SaleStatus.EXPIRED, "Time expired");
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L); // Run every second
    }

    /**
     * Schedules the tasks for a sale's start and end.
     */
    private void scheduleSaleTasks(Firesale sale) {
        Instant now = Instant.now();
        // Cancel any existing tasks for this sale ID first
        cancelSaleTask(sale.getSaleId());

        if (sale.getStatus() == SaleStatus.PENDING && sale.getStartTime().isAfter(now)) {
            // Sale hasn't started yet, schedule start task
            long delayTicks = (sale.getStartTime().toEpochMilli() - now.toEpochMilli()) / 50;
            BukkitTask startTask = new BukkitRunnable() {
                @Override
                public void run() {
                    startSale(sale);
                }
            }.runTaskLater(plugin, Math.max(0, delayTicks));
            saleTasks.put(sale.getSaleId(), startTask);

        } else if (sale.getStatus() == SaleStatus.ACTIVE && sale.getEndTime().isAfter(now)) {
            // Sale is already active, schedule end task
            long delayTicks = (sale.getEndTime().toEpochMilli() - now.toEpochMilli()) / 50;
            BukkitTask endTask = new BukkitRunnable() {
                @Override
                public void run() {
                    endSale(sale, SaleStatus.EXPIRED, "Time expired");
                }
            }.runTaskLater(plugin, Math.max(0, delayTicks));
            saleTasks.put(sale.getSaleId(), endTask);
        } else if (sale.getStatus() == SaleStatus.PENDING && sale.getStartTime().isBefore(now)) {
            // Sale was pending but start time is in the past (e.g., server restart), start it now.
            startSale(sale);
        }
    }

    private void cancelSaleTask(int saleId) {
        if (saleTasks.containsKey(saleId)) {
            saleTasks.get(saleId).cancel();
            saleTasks.remove(saleId);
        }
    }

    /**
     * Creates a new firesale.
     *
     * @return A success or error message component.
     */
    public Component createSale(Player creator, ItemStack item, double price, String startIn, int quantity, String duration) {
        if (getActiveSales().size() >= MAX_ACTIVE_SALES) {
            return serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Maximum number of active sales (4) reached."));
        }

        if (item == null || item.getType() == Material.AIR) {
            return serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Invalid item."));
        }

        // Prevent stacking custom heads from hand
        if (item.getType() == Material.PLAYER_HEAD && item.getItemMeta().hasCustomModelData()) { // A basic check
            return serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Custom player heads from hand are not supported. Please use an item from items.yml."));
        }

        long startMillis = parseTime(startIn);
        long durationMillis = parseTime(duration);

        if (startMillis == -1 || durationMillis == -1) {
            return serverPrefix.append(miniMessage.deserialize("<red>Invalid time format. Use s, m, h, or d (e.g., 10m, 1h)."));
        }

        Instant now = Instant.now();
        Instant startTime = now.plusMillis(startMillis);
        Instant endTime = startTime.plusMillis(durationMillis);

        Firesale sale = new Firesale(
                0, // ID will be set by DB
                item.clone(),
                price,
                quantity,
                quantity,
                startTime,
                endTime,
                creator.getUniqueId(),
                creator.getName(),
                SaleStatus.PENDING,
                0
        );

        // Save to DB (which returns the new ID) and add to active map
        Firesale savedSale = database.saveSale(sale);
        activeSales.put(savedSale.getSaleId(), savedSale);

        // Schedule its tasks
        scheduleSaleTasks(savedSale);

        logger.logSaleCreated(creator, savedSale);
        return serverPrefix.append(miniMessage.deserialize(
                "<green>Firesale created! ID: <yellow><sale_id></yellow>. Item: <aqua><item_name></aqua>. Starts in: <white><starts></white>.",
                Placeholder.component("sale_id", Component.text(savedSale.getSaleId())),
                Placeholder.component("item_name", Component.text(itemManager.getItemName(item))),
                Placeholder.component("starts", Component.text(formatDuration(startMillis)))
        ));
    }

    /**
     * Removes a firesale.
     *
     * @return A success or error message component.
     */
    public Component removeSale(Player admin, int saleId) {
        Firesale sale = activeSales.get(saleId);
        if (sale == null) {
            return serverPrefix.append(miniMessage.deserialize("<red>No active or pending sale found with ID: <yellow>" + saleId));
        }

        // End the sale, marking it as cancelled
        endSale(sale, SaleStatus.CANCELLED, "Manually removed by " + admin.getName());

        logger.logSaleRemoved(admin, sale);
        return serverPrefix.append(miniMessage.deserialize("<green>Firesale <yellow>" + saleId + "</yellow> has been removed."));
    }

    /**
     * Called when a PENDING sale becomes ACTIVE.
     */
    public void startSale(Firesale sale) {
        sale.setStatus(SaleStatus.ACTIVE);
        database.updateSaleStatus(sale.getSaleId(), SaleStatus.ACTIVE); // Update status in DB

        // Reschedule the end task
        scheduleSaleTasks(sale);

        logger.logSaleStart(sale);

        // Announce to players in the "lifesteal" world
        String messageTemplate = plugin.getConfig().getString("firesale.join-message",
                "<green>A firesale is now active!%nl%<aqua><item_name></aqua> (<white>x<quantity></white>)%nl%Price: <gold><price> Credits</gold>%nl%Time Left: <yellow><time></yellow>");

        String formattedMsg = messageTemplate
                .replace("<item_name>", itemManager.getItemName(sale.getItem()))
                .replace("<quantity>", String.valueOf(sale.getRemainingQuantity()))
                .replace("<price>", String.valueOf(sale.getPrice()))
                .replace("<time>", formatDuration(sale.getTimeRemainingMillis()))
                .replace("%nl%", "\n");

        Component announcement = serverPrefix.append(miniMessage.deserialize(formattedMsg));

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().getName().equalsIgnoreCase("lifesteal")) {
                p.sendMessage(announcement);
            }
        }
    }

    /**
     * Called when an ACTIVE sale ends for any reason (expired, completed, cancelled).
     */
    private void endSale(Firesale sale, SaleStatus endStatus, String reason) {
        // Remove from active list
        activeSales.remove(sale.getSaleId());

        // Cancel any tasks
        cancelSaleTask(sale.getSaleId());

        // Update object and archive it
        sale.setStatus(endStatus);
        if (endStatus == SaleStatus.EXPIRED) {
            sale.setEndTime(Instant.now()); // Ensure end time is accurate if it's manually ended
        }
        database.archiveSale(sale);

        logger.logSaleEnd(sale, reason);
    }

    /**
     * Thread-safe method to attempt purchasing an item.
     */
    public synchronized void attemptPurchase(Player player, Firesale sale) {
        if (sale.getStatus() != SaleStatus.ACTIVE) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>This sale is no longer active.")));
            player.closeInventory();
            return;
        }

        if (sale.getRemainingQuantity() <= 0) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>This item is sold out!")));
            // End the sale since it's sold out
            endSale(sale, SaleStatus.COMPLETED, "Sold out");
            player.closeInventory();
            return;
        }

        double playerCredits = getCredits(player);
        if (playerCredits < sale.getPrice()) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You do not have enough credits to buy this item.")));
            return;
        }

        // Check for inventory space
        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Your inventory is full!")));
            return;
        }

        // --- Transaction ---
        // 1. Take credits
        setCredits(player, playerCredits - sale.getPrice());

        // 2. Decrement stock
        sale.setRemainingQuantity(sale.getRemainingQuantity() - 1);
        sale.setTotalSold(sale.getTotalSold() + 1);
        database.updateSaleQuantity(sale.getSaleId(), sale.getRemainingQuantity(), sale.getTotalSold());

        // 3. Give item
        player.getInventory().addItem(sale.getItem().clone());

        // 4. Log and notify
        logger.logPurchase(player, sale, 1);
        player.sendMessage(serverPrefix.append(miniMessage.deserialize(
                "<green>You purchased <aqua><item_name></aqua> for <gold><price> credits</gold>!",
                Placeholder.component("item_name", Component.text(itemManager.getItemName(sale.getItem()))),
                Placeholder.component("price", Component.text(sale.getPrice()))
        )));

        // 5. Check if sold out
        if (sale.getRemainingQuantity() <= 0) {
            endSale(sale, SaleStatus.COMPLETED, "Sold out");
            player.closeInventory(); // Close inventory for all viewers as sale is gone
        } else {
            // Just refresh this player's view
            plugin.getServer().getScheduler().runTask(plugin, () ->
                    ((Player)player).updateInventory()
            );
        }
    }

    /**
     * Checks if a player should receive the firesale message on join/world change.
     */
    public void checkPlayerJoin(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase("lifesteal")) {
            return;
        }

        Firesale sale = getActiveSales().stream().findFirst().orElse(null);
        if (sale != null) {
            String messageTemplate = plugin.getConfig().getString("firesale.join-message",
                    "<green>A firesale is currently active!%nl%<aqua><item_name></aqua> (<white>x<quantity></white>)%nl%Price: <gold><price> Credits</gold>%nl%Time Left: <yellow><time></yellow>");

            String formattedMsg = messageTemplate
                    .replace("<item_name>", itemManager.getItemName(sale.getItem()))
                    .replace("<quantity>", String.valueOf(sale.getRemainingQuantity()))
                    .replace("<price>", String.valueOf(sale.getPrice()))
                    .replace("<time>", formatDuration(sale.getTimeRemainingMillis()))
                    .replace("%nl%", "\n");

            Component announcement = serverPrefix.append(miniMessage.deserialize(formattedMsg));
            player.sendMessage(announcement);
        }
    }

    public List<Firesale> getActiveSales() {
        return new ArrayList<>(activeSales.values());
    }

    public Optional<Firesale> getSaleById(int saleId) {
        return Optional.ofNullable(activeSales.get(saleId));
    }

    // --- FIX: Added getter for the logger ---
    public FiresaleLogger getLogger() {
        return this.logger;
    }

    // --- Currency Helpers ---

    private double getCredits(Player player) {
        String varName = SkriptVarParse.parse(player, SKRIPT_CURRENCY_VAR);
        Object value = SkriptUtils.getVar(varName);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private void setCredits(Player player, double amount) {
        String varName = SkriptVarParse.parse(player, SKRIPT_CURRENCY_VAR);
        SkriptUtils.setVar(varName, amount);
    }

    // --- Time Helpers ---

    /**
     * Parses a time string like "10m", "1h" into milliseconds.
     *
     * @return Milliseconds, or -1 if invalid format.
     */
    public long parseTime(String timeString) {
        try {
            Matcher matcher = TIME_PATTERN.matcher(timeString.toLowerCase());
            if (matcher.matches()) {
                long value = Long.parseLong(matcher.group(1));
                String unit = matcher.group(2);
                switch (unit) {
                    case "s": return TimeUnit.SECONDS.toMillis(value);
                    case "m": return TimeUnit.MINUTES.toMillis(value);
                    case "h": return TimeUnit.HOURS.toMillis(value);
                    case "d": return TimeUnit.DAYS.toMillis(value);
                }
            }
        } catch (NumberFormatException e) {
            return -1;
        }
        return -1;
    }

    /**
     * Formats a duration in milliseconds into a readable string "HH:MM:SS" or "DDd HHh".
     */
    public String formatDuration(long millis) {
        if (millis < 0) return "00:00";

        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    public void shutdown() {
        if (globalUpdateTask != null) {
            globalUpdateTask.cancel();
        }
        saleTasks.values().forEach(BukkitTask::cancel);
        saleTasks.clear();
    }
}