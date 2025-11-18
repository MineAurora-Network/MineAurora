package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.database.FiresaleDatabase;
import me.login.misc.firesale.item.FiresaleItemManager;
import me.login.misc.firesale.model.Firesale;
import me.login.misc.firesale.model.SaleStatus;
import me.login.scoreboard.SkriptUtils;
import me.login.scoreboard.SkriptVarParse;
import net.citizensnpcs.api.npc.NPC;
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
    private NPC npc;

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
        String prefixRaw = plugin.getConfig().getString("server_prefix", "<red>[Server] ");
        this.serverPrefix = miniMessage.deserialize(prefixRaw);
    }

    public void loadSales() {
        activeSales.clear();
        for (Firesale sale : database.loadActiveSales()) {
            activeSales.put(sale.getSaleId(), sale);
            scheduleSaleTasks(sale);
        }
    }

    public void setNpc(NPC npc) {
        this.npc = npc;
    }

    public NPC getNpc() {
        return npc;
    }

    public void startScheduler() {
        this.globalUpdateTask = new BukkitRunnable() {
            @Override
            public void run() {
                Instant now = Instant.now();
                for (Firesale sale : getActiveSales()) {
                    if (sale.getStatus() == SaleStatus.ACTIVE && sale.getEndTime().isBefore(now)) {
                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                endSale(sale, SaleStatus.EXPIRED, "Time expired");
                            }
                        }.runTask(plugin);
                    }
                }
            }
        }.runTaskTimerAsynchronously(plugin, 20L, 20L);
    }

    private void scheduleSaleTasks(Firesale sale) {
        Instant now = Instant.now();
        cancelSaleTask(sale.getSaleId());

        if (sale.getStatus() == SaleStatus.PENDING && sale.getStartTime().isAfter(now)) {
            long delayTicks = (sale.getStartTime().toEpochMilli() - now.toEpochMilli()) / 50;
            BukkitTask startTask = new BukkitRunnable() {
                @Override
                public void run() {
                    startSale(sale);
                }
            }.runTaskLater(plugin, Math.max(0, delayTicks));
            saleTasks.put(sale.getSaleId(), startTask);

        } else if (sale.getStatus() == SaleStatus.ACTIVE && sale.getEndTime().isAfter(now)) {
            long delayTicks = (sale.getEndTime().toEpochMilli() - now.toEpochMilli()) / 50;
            BukkitTask endTask = new BukkitRunnable() {
                @Override
                public void run() {
                    endSale(sale, SaleStatus.EXPIRED, "Time expired");
                }
            }.runTaskLater(plugin, Math.max(0, delayTicks));
            saleTasks.put(sale.getSaleId(), endTask);
        } else if (sale.getStatus() == SaleStatus.PENDING && sale.getStartTime().isBefore(now)) {
            startSale(sale);
        }
    }

    private void cancelSaleTask(int saleId) {
        if (saleTasks.containsKey(saleId)) {
            saleTasks.get(saleId).cancel();
            saleTasks.remove(saleId);
        }
    }

    public Component createSale(Player creator, ItemStack item, double price, String startIn, int quantity, String duration) {
        if (getActiveSales().size() >= MAX_ACTIVE_SALES) {
            return serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Maximum number of active sales (4) reached."));
        }

        if (item == null || item.getType() == Material.AIR) {
            return serverPrefix.append(miniMessage.deserialize("<red>Cannot create sale: Invalid item."));
        }

        if (item.getType() == Material.PLAYER_HEAD && item.getItemMeta().hasCustomModelData()) {
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
                0,
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

        Firesale savedSale = database.saveSale(sale);
        activeSales.put(savedSale.getSaleId(), savedSale);
        scheduleSaleTasks(savedSale);

        logger.logSaleCreated(creator, savedSale);

        // --- FIX: Added line breaks (%nl%) as requested ---
        return serverPrefix.append(miniMessage.deserialize(
                "<green>Firesale created!%nl%  ID: <yellow><sale_id></yellow>%nl%  Item: <aqua><item_name></aqua>%nl%  Starts in: <white><starts></white>.",
                Placeholder.component("sale_id", Component.text(savedSale.getSaleId())),
                Placeholder.component("item_name", Component.text(itemManager.getItemName(item))),
                Placeholder.component("starts", Component.text(formatDuration(startMillis)))
        ).replaceText(config -> config.match("%nl%").replacement(Component.newline())));
    }

    public Component removeSale(Player admin, int saleId) {
        Firesale sale = activeSales.get(saleId);
        if (sale == null) {
            return serverPrefix.append(miniMessage.deserialize("<red>No active or pending sale found with ID: <yellow>" + saleId));
        }
        endSale(sale, SaleStatus.CANCELLED, "Manually removed by " + admin.getName());
        logger.logSaleRemoved(admin, sale);
        return serverPrefix.append(miniMessage.deserialize("<green>Firesale <yellow>" + saleId + "</yellow> has been removed."));
    }

    public void startSale(Firesale sale) {
        sale.setStatus(SaleStatus.ACTIVE);
        database.updateSaleStatus(sale.getSaleId(), SaleStatus.ACTIVE);
        scheduleSaleTasks(sale);
        logger.logSaleStart(sale);

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

    private void endSale(Firesale sale, SaleStatus endStatus, String reason) {
        activeSales.remove(sale.getSaleId());
        cancelSaleTask(sale.getSaleId());
        sale.setStatus(endStatus);
        if (endStatus == SaleStatus.EXPIRED) {
            sale.setEndTime(Instant.now());
        }
        database.archiveSale(sale);
        logger.logSaleEnd(sale, reason);
    }

    public synchronized void attemptPurchase(Player player, Firesale sale) {
        if (sale.getStatus() != SaleStatus.ACTIVE) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>This sale is no longer active.")));
            player.closeInventory();
            return;
        }

        if (sale.getRemainingQuantity() <= 0) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>This item is sold out!")));
            endSale(sale, SaleStatus.COMPLETED, "Sold out");
            player.closeInventory();
            return;
        }

        double playerCredits = getCredits(player);
        if (playerCredits < sale.getPrice()) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>You do not have enough credits.")));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>Your inventory is full!")));
            return;
        }

        setCredits(player, playerCredits - sale.getPrice());
        sale.setRemainingQuantity(sale.getRemainingQuantity() - 1);
        sale.setTotalSold(sale.getTotalSold() + 1);

        // Sync update to database to prevent race conditions
        database.updateSaleQuantity(sale.getSaleId(), sale.getRemainingQuantity(), sale.getTotalSold());

        player.getInventory().addItem(sale.getItem().clone());
        logger.logPurchase(player, sale, 1);

        player.sendMessage(serverPrefix.append(miniMessage.deserialize(
                "<green>You purchased <aqua><item_name></aqua> for <gold><price> credits</gold>!",
                Placeholder.component("item_name", Component.text(itemManager.getItemName(sale.getItem()))),
                Placeholder.component("price", Component.text(sale.getPrice()))
        )));

        if (sale.getRemainingQuantity() <= 0) {
            // FIXED: Added message informing player that sale ended
            player.sendMessage(serverPrefix.append(miniMessage.deserialize("<red>The firesale has just ended (Sold Out)!")));
            endSale(sale, SaleStatus.COMPLETED, "Sold out");
            player.closeInventory();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() == null) {
                    player.updateInventory();
                }
            });
        }
    }

    public void checkPlayerJoin(Player player) {
        if (!player.getWorld().getName().equalsIgnoreCase("lifesteal")) return;
        Firesale sale = getActiveSales().stream().findFirst().orElse(null);
        if (sale != null) {
            // Join message logic...
        }
    }

    public List<Firesale> getActiveSales() {
        return new ArrayList<>(activeSales.values());
    }

    public Optional<Firesale> getSaleById(int saleId) {
        return Optional.ofNullable(activeSales.get(saleId));
    }

    public FiresaleLogger getLogger() {
        return this.logger;
    }

    private double getCredits(Player player) {
        String varName = SkriptVarParse.parse(player, SKRIPT_CURRENCY_VAR);
        Object value = SkriptUtils.getVar(varName);
        return value instanceof Number ? ((Number) value).doubleValue() : 0.0;
    }

    private void setCredits(Player player, double amount) {
        String varName = SkriptVarParse.parse(player, SKRIPT_CURRENCY_VAR);
        SkriptUtils.setVar(varName, amount);
    }

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
        } catch (NumberFormatException e) { return -1; }
        return -1;
    }

    public String formatDuration(long millis) {
        if (millis < 0) return "00:00";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
        millis -= TimeUnit.MINUTES.toMillis(minutes);
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);

        if (days > 0) return String.format("%dd %dh %dm", days, hours, minutes);
        else if (hours > 0) return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        else return String.format("%02d:%02d", minutes, seconds);
    }

    public void shutdown() {
        if (globalUpdateTask != null) globalUpdateTask.cancel();
        saleTasks.values().forEach(BukkitTask::cancel);
        saleTasks.clear();
    }
}