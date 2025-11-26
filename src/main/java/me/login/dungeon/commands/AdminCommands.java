package me.login.dungeon.commands;

import me.login.dungeon.game.GameManager;
import me.login.dungeon.game.GameSession;
import me.login.dungeon.game.MobManager;
import me.login.dungeon.gui.DungeonGUI;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.manager.DungeonRewardManager;
import me.login.dungeon.model.Cuboid;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonUtils;
import me.login.Login;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AdminCommands implements CommandExecutor {

    private final Login plugin;
    private final DungeonManager dungeonManager;
    private final GameManager gameManager;
    private final DungeonRewardManager rewardManager;

    public AdminCommands(Login plugin, DungeonManager dungeonManager, GameManager gameManager, DungeonRewardManager rewardManager) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.gameManager = gameManager;
        this.rewardManager = rewardManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        if (!player.hasPermission("dungeon.admin")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("rngmeter")) {
                DungeonGUI.openRNGMeter(player, rewardManager);
            } else {
                DungeonUtils.error(player, "No permission.");
            }
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("info")) {
            if (args.length < 2) { DungeonUtils.error(player, "Usage: /dungeon info <id>"); return true; }
            try {
                int id = Integer.parseInt(args[1]);
                Dungeon d = dungeonManager.getDungeon(id);
                if (d == null) { DungeonUtils.error(player, "Dungeon not found."); return true; }

                player.sendMessage(dungeonManager.getDungeonInfo(id));
            } catch(NumberFormatException e) { DungeonUtils.error(player, "Invalid ID."); }
            return true;
        }

        if (sub.equals("showremaining")) {
            GameSession session = gameManager.getSession(player);
            if (session == null) {
                DungeonUtils.error(player, "You are not in a dungeon session.");
                return true;
            }
            session.highlightRemainingMobs();
            return true;
        }

        if (sub.equals("rngmeter")) {
            DungeonGUI.openRNGMeter(player, rewardManager);
            return true;
        }

        if (sub.equals("removemobspawn")) {
            double closestDist = 4.0;
            Entity target = null;
            for (Entity e : player.getNearbyEntities(2, 2, 2)) {
                if (e instanceof TextDisplay && e.getPersistentDataContainer().has(MobManager.MARKER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                    double dist = e.getLocation().distanceSquared(player.getLocation());
                    if (dist < closestDist) {
                        closestDist = dist;
                        target = e;
                    }
                }
            }

            if (target != null) {
                int rId = target.getPersistentDataContainer().get(MobManager.MARKER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
                target.remove();
                DungeonUtils.msg(player, "<green>Removed mob spawn marker for Room " + rId + ".</green>");
            } else {
                DungeonUtils.error(player, "No mob spawn marker found nearby (2 block radius).");
            }
            return true;
        }

        if (sub.equals("give")) {
            if (args.length < 2) {
                DungeonUtils.error(player, "Usage: /dungeon give <item_id/all>");
                return true;
            }
            String target = args[1].toLowerCase();
            List<DungeonRewardManager.RewardItem> allRewards = rewardManager.getAllRewards();

            if (target.equals("all")) {
                for (DungeonRewardManager.RewardItem item : allRewards) {
                    player.getInventory().addItem(item.stack.clone());
                }
                DungeonUtils.msg(player, "<green>Gave all dungeon items!");
            } else {
                boolean found = false;
                for (DungeonRewardManager.RewardItem item : allRewards) {
                    if (item.id.equalsIgnoreCase(target)) {
                        player.getInventory().addItem(item.stack.clone());
                        DungeonUtils.msg(player, "<green>Gave item: " + item.id);
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    DungeonUtils.error(player, "Item '" + target + "' not found in items.yml");
                }
            }
            return true;
        }

        if (sub.equals("create")) {
            if (args.length < 2) { DungeonUtils.error(player, "Usage: /dungeon create <id>"); return true; }
            try {
                int id = Integer.parseInt(args[1]);
                if (dungeonManager.getDungeon(id) != null) {
                    DungeonUtils.error(player, "Dungeon " + id + " already exists!");
                    return true;
                }
                Location loc = player.getLocation();
                dungeonManager.createDungeon(id, loc);
                DungeonUtils.msg(player, "Created Dungeon " + id + " at your location.");
                plugin.getLogger().info("[Dungeon Setup] " + player.getName() + " created Dungeon " + id +
                        " Spawn at: " + loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
            } catch(NumberFormatException e) { DungeonUtils.error(player, "Invalid ID."); }
            return true;
        }

        if (sub.equals("delete")) {
            if (args.length < 2) { DungeonUtils.error(player, "Usage: /dungeon delete <id>"); return true; }
            try {
                int id = Integer.parseInt(args[1]);
                dungeonManager.deleteDungeon(id);
                DungeonUtils.msg(player, "Deleted Dungeon " + id + ".");
                plugin.getLogger().info("[Dungeon Setup] " + player.getName() + " deleted Dungeon " + id);
            } catch(NumberFormatException e) { DungeonUtils.error(player, "Invalid ID."); }
            return true;
        }

        if (sub.equals("redo")) {
            if (args.length < 3) { DungeonUtils.error(player, "Usage: /dungeon redo <id> <amount>"); return true; }
            try {
                int id = Integer.parseInt(args[1]);
                int amount = Integer.parseInt(args[2]);
                List<Integer> removed = dungeonManager.removeLastRooms(id, amount);
                if (removed.isEmpty()) {
                    DungeonUtils.error(player, "No rooms found to remove for Dungeon " + id);
                } else {
                    DungeonUtils.msg(player, "Removed " + removed.size() + " rooms from Dungeon " + id + ": <yellow>" + removed.toString() + "</yellow>");
                }
            } catch(NumberFormatException e) { DungeonUtils.error(player, "Invalid numbers."); }
            return true;
        }

        if (sub.equals("start")) {
            if (args.length < 2) { DungeonUtils.error(player, "Usage: /dungeon start <id>"); return true; }
            try {
                int id = Integer.parseInt(args[1]);
                gameManager.startDungeon(player, id);
            } catch(NumberFormatException e) { DungeonUtils.error(player, "Invalid ID."); }
            return true;
        }

        if (sub.equals("stop")) {
            gameManager.endSession(player);
            DungeonUtils.msg(player, "Session ended (if any).");
            return true;
        }

        if (sub.equals("check")) {
            if (args.length < 5 || !args[2].equalsIgnoreCase("room") || !args[4].equalsIgnoreCase("mobspawn")) {
                DungeonUtils.error(player, "Usage: /dungeon check <id> room <roomID> mobspawn");
                return true;
            }
            try {
                int id = Integer.parseInt(args[1]);
                int roomId = Integer.parseInt(args[3]);
                Dungeon d = dungeonManager.getDungeon(id);
                if (d != null) {
                    int count = 0;
                    for (Entity e : player.getNearbyEntities(250, 250, 250)) {
                        if (e instanceof TextDisplay && e.getPersistentDataContainer().has(MobManager.MARKER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER)) {
                            int rId = e.getPersistentDataContainer().get(MobManager.MARKER_KEY, org.bukkit.persistence.PersistentDataType.INTEGER);
                            if (rId == roomId) {
                                Location loc = e.getLocation();
                                ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                                as.setGravity(false);
                                as.setCanPickupItems(false);
                                as.setCustomNameVisible(true);
                                as.setCustomName("§cSpawn Room " + rId);
                                as.setGlowing(true);
                                as.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET));
                                new BukkitRunnable() { @Override public void run() { as.remove(); } }.runTaskLater(plugin, 200L);
                                count++;
                            }
                        }
                    }
                    DungeonUtils.msg(player, "Showing " + count + " markers for Room " + roomId + " (Lasts 10s)");
                } else {
                    DungeonUtils.error(player, "Dungeon not found.");
                }
            } catch (Exception e) { DungeonUtils.error(player, "Invalid arguments."); }
            return true;
        }

        if (sub.equals("setup")) {
            if (args.length < 3) { sendHelp(player); return true; }
            int id;
            try { id = Integer.parseInt(args[1]); } catch(Exception e) { DungeonUtils.error(player, "Invalid ID."); return true; }

            Dungeon dungeon = dungeonManager.getDungeon(id);
            if (dungeon == null) { DungeonUtils.error(player, "Dungeon " + id + " not found. Create it first."); return true; }

            String type = args[2].toLowerCase();

            if (type.equals("chest")) {
                dungeonManager.startSetup(player, id, 0, "chest", "chest");
                return true;
            }

            if (type.equals("entrydoor")) {
                if (args.length < 4) { DungeonUtils.error(player, "Usage: ... entrydoor <pos1/pos2>"); return true; }
                dungeonManager.startSetup(player, id, 0, "entrydoor", args[3].toLowerCase());
                return true;
            }

            if (type.equals("lastroom")) {
                if (args.length < 4) { DungeonUtils.error(player, "Usage: ... lastroom <bossspawn/rewardloc/bossdoor/treasuredoor/minirewardchest>"); return true; }
                String subType = args[3].toLowerCase();
                Location loc = player.getLocation();
                String locStr = loc.getWorld().getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

                if (subType.equals("bossspawn")) {
                    dungeon.setBossSpawnLocation(loc);
                    dungeonManager.saveDungeon(dungeon);
                    DungeonUtils.msg(player, "Set Boss Spawn to your location.");
                }
                else if (subType.equals("rewardloc")) {
                    dungeon.setRewardChestLocation(loc);
                    dungeonManager.saveDungeon(dungeon);
                    DungeonUtils.msg(player, "Set Reward Chest Location to your location.");
                }
                else if (subType.equals("minirewardchest")) { // NEW MINI CHEST SETUP
                    dungeon.setMiniRewardChestLocation(loc);
                    dungeonManager.saveDungeon(dungeon);
                    DungeonUtils.msg(player, "Set Mini Reward Chest Location to your location.");
                    plugin.getLogger().info("[Dungeon Setup] " + player.getName() + " set Mini Chest for Dungeon " + id + " at " + locStr);
                }
                else if (subType.equals("bossdoor")) {
                    if (args.length < 5) { DungeonUtils.error(player, "Usage: ... bossdoor <pos1/pos2>"); return true; }
                    dungeonManager.startSetup(player, id, 0, "bossdoor", args[4].toLowerCase());
                }
                else if (subType.equals("treasuredoor")) {
                    if (args.length < 5) { DungeonUtils.error(player, "Usage: ... treasuredoor <pos1/pos2>"); return true; }
                    dungeonManager.startSetup(player, id, 0, "treasuredoor", args[4].toLowerCase());
                }
                return true;
            }

            if (type.equals("room")) {
                if (args.length < 5) { DungeonUtils.error(player, "Usage: ... room <id> <mobspawn/door>"); return true; }
                try {
                    int roomId = Integer.parseInt(args[3]);
                    DungeonRoom room = dungeon.getRoom(roomId);
                    String roomAction = args[4].toLowerCase();

                    if (roomAction.equals("mobspawn")) {
                        Location loc = player.getLocation();
                        MobManager.spawnMarker(loc, roomId);
                        DungeonUtils.msg(player, "Placed Spawn Marker for Room " + roomId + " at your location.");
                        plugin.getLogger().info("[Dungeon Setup] " + player.getName() + " placed marker for Room " + roomId + " at " + loc);
                    }
                    else if (roomAction.equals("door")) {
                        if (args.length < 6) {
                            DungeonUtils.error(player, "Usage: ... room <room> door <pos1/pos2>");
                            return true;
                        }
                        dungeonManager.startSetup(player, id, roomId, "door", args[5].toLowerCase());
                    }
                } catch (NumberFormatException e) {
                    DungeonUtils.error(player, "Invalid Room ID.");
                }
                return true;
            }
        }

        if (sub.equals("tempopen")) {
            if (args.length < 4 || !args[2].equalsIgnoreCase("door")) {
                DungeonUtils.error(player, "Usage: /dungeon tempopen <id> door <roomID>");
                return true;
            }
            try {
                int dId = Integer.parseInt(args[1]);
                int rId = Integer.parseInt(args[3]);
                GameSession session = gameManager.getSession(player);

                if (session == null) { DungeonUtils.error(player, "Not in a session!"); return true; }
                if (session.getDungeon().getId() != dId) { DungeonUtils.error(player, "Wrong Dungeon ID."); return true; }

                Cuboid region = null;
                if (rId == 0) region = session.getDungeon().getEntryDoor();
                else if (rId == 6) region = session.getDungeon().getBossRoomDoor();
                else {
                    DungeonRoom room = session.getDungeon().getRoom(rId);
                    if (room != null) region = room.getDoorRegion();
                }

                if (region != null) {
                    session.openDoor(region);
                    DungeonUtils.msg(player, "<green>Force opened Room " + rId + " door.");
                } else {
                    DungeonUtils.error(player, "Room " + rId + " has no door region defined!");
                }
            } catch (Exception e) { DungeonUtils.error(player, "Invalid ID."); }
            return true;
        }

        sendHelp(player);
        return true;
    }

    private void sendHelp(Player p) {
        DungeonUtils.msg(p, "<b>Admin Commands:</b>");
        DungeonUtils.msg(p, "/dungeon info <id>");
        DungeonUtils.msg(p, "/dungeon setup <id> chest - Right click a chest");
        DungeonUtils.msg(p, "/dungeon showremaining - Highlights mobs");
        DungeonUtils.msg(p, "/dungeon create <id>");
        DungeonUtils.msg(p, "/dungeon delete <id>");
        DungeonUtils.msg(p, "/dungeon redo <id> <amount>");
        DungeonUtils.msg(p, "/dungeon setup <id> entrydoor <pos1/pos2>");
        DungeonUtils.msg(p, "/dungeon setup <id> room <room> mobspawn");
        DungeonUtils.msg(p, "/dungeon removemobspawn");
        DungeonUtils.msg(p, "/dungeon setup <id> room <room> door <pos1/pos2>");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom <bossspawn/rewardloc/minirewardchest>");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom <bossdoor/treasuredoor> <pos1/pos2>");
        DungeonUtils.msg(p, "/dungeon check <id> room <room> mobspawn");
        DungeonUtils.msg(p, "/dungeon start <id>");
        DungeonUtils.msg(p, "/dungeon stop");
    }
}