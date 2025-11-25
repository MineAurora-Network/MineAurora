package me.login.loginsystem;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourManager implements Listener {

    private final Login plugin;
    private final LoginDatabase database;
    private final LoginSystemLogger logger; // Added Logger
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final Map<UUID, Integer> activeSessions = new ConcurrentHashMap<>();

    public ParkourManager(Login plugin, LoginDatabase database, LoginSystemLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger; // Inject Logger
        Bukkit.getPluginManager().registerEvents(this, plugin);
        startIntegrityTask();
    }

    // ... (Rest of the class logic remains the same, only logToDiscord changes) ...

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals("login")) return;

        if (event.getAction() == Action.PHYSICAL && event.getClickedBlock() != null) {
            Block b = event.getClickedBlock();
            if (b.getType() == Material.HEAVY_WEIGHTED_PRESSURE_PLATE || b.getType() == Material.LIGHT_WEIGHTED_PRESSURE_PLATE) {
                LoginDatabase.ParkourPoint point = database.getParkourPoint(b.getLocation());
                if (point != null) {
                    handlePointTouch(player, point);
                }
            }
        }

        if (event.getAction().name().contains("RIGHT_CLICK")) {
            ItemStack item = event.getItem();
            if (isCancelItem(item)) {
                cancelParkour(player, true);
                event.setCancelled(true);
            }
        }
    }

    private void handlePointTouch(Player player, LoginDatabase.ParkourPoint point) {
        UUID uuid = player.getUniqueId();

        if (point.type().equals("START")) {
            if (activeSessions.containsKey(uuid)) {
                player.sendMessage(mm.deserialize("<red>You have already started parkour! Use the <bold>Red Concrete</bold> to cancel first if you want to restart.</red>"));
                return;
            }
            startParkour(player);

        } else if (point.type().equals("CHECKPOINT")) {
            if (!activeSessions.containsKey(uuid)) {
                player.sendMessage(mm.deserialize("<red>You must start the parkour first at the Start Point!</red>"));
                return;
            }

            int current = activeSessions.get(uuid);
            if (point.index() == current + 1) {
                activeSessions.put(uuid, point.index());
                player.sendMessage(mm.deserialize("<yellow>Checkpoint <gold>#" + point.index() + "</gold> reached!</yellow>"));
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            } else if (point.index() <= current) {
                // Silent
            } else {
                player.sendMessage(mm.deserialize("<red>You skipped a checkpoint! Go back to <gold>#" + (current + 1) + "</gold>.</red>"));
            }

        } else if (point.type().equals("FINAL")) {
            if (!activeSessions.containsKey(uuid)) {
                player.sendMessage(mm.deserialize("<red>You must start the parkour first!</red>"));
                return;
            }

            int current = activeSessions.get(uuid);
            int total = database.getParkourPointCount("CHECKPOINT");

            if (current == total) {
                finishParkour(player);
            } else {
                player.sendMessage(mm.deserialize("<red>You missed checkpoints! Current: <yellow>" + current + "/" + total + "</yellow></red>"));
            }
        }
    }

    private void startParkour(Player player) {
        activeSessions.put(player.getUniqueId(), 0);
        player.sendMessage(mm.deserialize("<green><bold>Parkour Started! <gray>Reach the end!</gray></bold></green>"));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 2f);

        ItemStack cancel = new ItemStack(Material.RED_CONCRETE);
        ItemMeta meta = cancel.getItemMeta();
        meta.displayName(mm.deserialize("<red><bold>Cancel Parkour</bold> <gray>(Right Click)</gray>"));
        cancel.setItemMeta(meta);
        player.getInventory().setItem(8, cancel);
    }

    public void cancelParkour(Player player, boolean message) {
        if (activeSessions.remove(player.getUniqueId()) != null) {
            player.getInventory().setItem(8, null);
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack item = player.getInventory().getItem(i);
                if (isCancelItem(item)) {
                    player.getInventory().setItem(i, null);
                }
            }
            if (message) {
                player.sendMessage(mm.deserialize("<red>Parkour Cancelled.</red>"));
            }
        }
    }

    private void finishParkour(Player player) {
        long lastReward = database.getLastParkourReward(player.getUniqueId());
        long now = System.currentTimeMillis();
        long cooldown = 24 * 60 * 60 * 1000;

        database.incrementParkourCompletions(player.getUniqueId());

        plugin.getTokenManager().getTokenBalance(player.getUniqueId()).thenAccept(oldTokens -> {
            if (now - lastReward >= cooldown) {
                plugin.getTokenManager().addTokens(player.getUniqueId(), 5);
                double newTokens = oldTokens + 5;
                database.setLastParkourReward(player.getUniqueId(), now);

                player.sendMessage(mm.deserialize("<gold><bold>PARKOUR COMPLETE!</bold> <green>+5 Tokens</green></gold>"));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                logToDiscord(player.getName(), oldTokens.doubleValue(), newTokens);
            } else {
                long remaining = cooldown - (now - lastReward);
                long hours = remaining / 3600000;
                long minutes = (remaining % 3600000) / 60000;
                player.sendMessage(mm.deserialize("<green>Parkour Finished! <red>(Reward available in " + hours + "h " + minutes + "m)</red></green>"));
            }
        });

        cancelParkour(player, false);
        player.teleport(new Location(Bukkit.getWorld("login"), 0.5, 100, 0.5));
    }

    public void handleSetupCommand(Player player, String type) {
        if (type.equalsIgnoreCase("checkpoint")) {
            giveSetupItem(player, Material.HEAVY_WEIGHTED_PRESSURE_PLATE, "<yellow>Parkour Checkpoint</yellow>");
        } else if (type.equalsIgnoreCase("finalpoint")) {
            giveSetupItem(player, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "<gold>Parkour Final Point</gold>");
        } else if (type.equalsIgnoreCase("startingpoint")) {
            giveSetupItem(player, Material.LIGHT_WEIGHTED_PRESSURE_PLATE, "<green>Parkour Start Point</green>");
        } else {
            player.sendMessage(Component.text("Usage: /loginparkour <checkpoint|finalpoint|startingpoint>", NamedTextColor.RED));
        }
    }

    private void giveSetupItem(Player p, Material mat, String name) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name));
        item.setItemMeta(meta);
        p.getInventory().addItem(item);
        p.sendMessage(mm.deserialize("<green>Received " + name + " item.</green>"));
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!event.getPlayer().getWorld().getName().equals("login")) return;
        if (isCancelItem(event.getItemInHand())) {
            event.setCancelled(true);
            return;
        }
        if (!event.getPlayer().isOp()) return;

        ItemStack item = event.getItemInHand();
        if (item == null || !item.hasItemMeta()) return;

        Component displayName = item.getItemMeta().displayName();
        if (displayName == null) return;
        String name = mm.serialize(displayName);
        Location loc = event.getBlock().getLocation();

        if (name.contains("Parkour Checkpoint")) {
            int index = database.getParkourPointCount("CHECKPOINT") + 1;
            database.addParkourPoint(loc, "CHECKPOINT", index);
            spawnHologram(loc, "<yellow>Checkpoint " + index + "</yellow>");
            event.getPlayer().sendMessage(mm.deserialize("<green>Checkpoint #" + index + " placed!</green>"));

        } else if (name.contains("Parkour Final Point")) {
            database.addParkourPoint(loc, "FINAL", 0);
            spawnHologram(loc, "<gold>Final Point</gold>");
            event.getPlayer().sendMessage(mm.deserialize("<green>Final Point placed!</green>"));

        } else if (name.contains("Parkour Start Point")) {
            database.addParkourPoint(loc, "START", 0);
            spawnHologram(loc, "<green>Start Parkour</green>");
            event.getPlayer().sendMessage(mm.deserialize("<green>Start Point placed!</green>"));
        }
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        if (!event.getPlayer().getWorld().getName().equals("login")) return;
        if (!event.getPlayer().isOp()) {
            if (database.getParkourPoint(event.getBlock().getLocation()) != null) {
                event.setCancelled(true);
            }
            return;
        }

        LoginDatabase.ParkourPoint point = database.getParkourPoint(event.getBlock().getLocation());
        if (point != null) {
            database.removeParkourPoint(event.getBlock().getLocation());
            removeHologram(event.getBlock().getLocation());

            if (point.type().equals("CHECKPOINT")) {
                reorderCheckpoints(point.index());
            }
            event.getPlayer().sendMessage(mm.deserialize("<red>Parkour point removed.</red>"));
        }
    }

    private void reorderCheckpoints(int deletedIndex) {
        List<LoginDatabase.ParkourPointData> points = database.getAllParkourPoints();
        for (LoginDatabase.ParkourPointData p : points) {
            if (p.type().equals("CHECKPOINT") && p.index() > deletedIndex) {
                Location loc = new Location(Bukkit.getWorld(p.world()), p.x(), p.y(), p.z());
                database.removeParkourPoint(loc);
                removeHologram(loc);
                int newIndex = p.index() - 1;
                database.addParkourPoint(loc, "CHECKPOINT", newIndex);
                spawnHologram(loc, "<yellow>Checkpoint " + newIndex + "</yellow>");
            }
        }
    }

    private boolean isCancelItem(ItemStack item) {
        if (item == null || item.getType() != Material.RED_CONCRETE) return false;
        if (!item.hasItemMeta()) return false;
        Component display = item.getItemMeta().displayName();
        return display != null && mm.serialize(display).contains("Cancel Parkour");
    }

    private void spawnHologram(Location plateLoc, String text) {
        Location holoLoc = plateLoc.clone().add(0.5, 1.5, 0.5);
        TextDisplay display = (TextDisplay) holoLoc.getWorld().spawnEntity(holoLoc, EntityType.TEXT_DISPLAY);
        display.text(mm.deserialize(text));
        display.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.addScoreboardTag("login_parkour");
        display.setPersistent(true);
    }

    private void removeHologram(Location plateLoc) {
        Location holoLoc = plateLoc.clone().add(0.5, 1.5, 0.5);
        holoLoc.getWorld().getNearbyEntities(holoLoc, 0.5, 0.5, 0.5).forEach(e -> {
            if (e instanceof TextDisplay && e.getScoreboardTags().contains("login_parkour")) {
                e.remove();
            }
        });
    }

    public void killAllDisplays(Player admin) {
        int count = 0;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e instanceof TextDisplay && e.getScoreboardTags().contains("login_parkour")) {
                    e.remove();
                    count++;
                }
            }
        }
        admin.sendMessage(mm.deserialize("<green>Removed " + count + " parkour displays.</green>"));
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity().getScoreboardTags().contains("login_parkour")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelParkour(event.getPlayer(), false);
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cancelParkour(event.getEntity(), true);
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (!event.getTo().getWorld().getName().equals("login")) {
            cancelParkour(event.getPlayer(), false);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (isCancelItem(event.getCurrentItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (isCancelItem(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
            event.setCancelled(true);
        }
    }

    private void logToDiscord(String playerName, double oldBal, double newBal) {
        String message = String.format("Player: **%s** finished parkour. Tokens: %.2f -> %.2f", playerName, oldBal, newBal);
        logger.logParkour(message);
    }

    private void startIntegrityTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                World loginWorld = Bukkit.getWorld("login");
                if (loginWorld == null) return;

                List<LoginDatabase.ParkourPointData> points = database.getAllParkourPoints();
                for (LoginDatabase.ParkourPointData p : points) {
                    Location displayLoc = new Location(Bukkit.getWorld(p.world()), p.x() + 0.5, p.y() + 1.5, p.z() + 0.5);
                    boolean exists = false;
                    if (displayLoc.getWorld() != null) {
                        for (Entity e : displayLoc.getWorld().getNearbyEntities(displayLoc, 0.2, 0.2, 0.2)) {
                            if (e instanceof TextDisplay && e.getScoreboardTags().contains("login_parkour")) {
                                exists = true;
                                break;
                            }
                        }
                        if (!exists) {
                            String text;
                            if (p.type().equals("CHECKPOINT")) text = "<yellow>Checkpoint " + p.index() + "</yellow>";
                            else if (p.type().equals("START")) text = "<green>Start Parkour</green>";
                            else text = "<gold>Final Point</gold>";
                            spawnHologram(new Location(Bukkit.getWorld(p.world()), p.x(), p.y(), p.z()), text);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 100L, 600L);
    }
}