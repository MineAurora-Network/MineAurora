package me.login.dungeon.commands;

import me.login.dungeon.game.GameManager;
import me.login.dungeon.gui.DungeonGUI;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.manager.DungeonRewardManager;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonUtils;
import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
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

        if (args.length == 0) {
            if (player.hasPermission("dungeon.admin")) sendHelp(player);
            else DungeonUtils.msg(player, "/dungeon rngmeter");
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("rngmeter")) {
            DungeonGUI.openRNGMeter(player, rewardManager);
            return true;
        }

        if (!player.hasPermission("dungeon.admin")) {
            DungeonUtils.error(player, "No permission.");
            return true;
        }

        // Basic Commands
        if (sub.equals("create") && args.length >= 2) {
            try { dungeonManager.createDungeon(Integer.parseInt(args[1]), player.getLocation()); DungeonUtils.msg(player, "Created."); } catch(Exception e) {}
            return true;
        }
        if (sub.equals("delete") && args.length >= 2) {
            try { dungeonManager.deleteDungeon(Integer.parseInt(args[1])); DungeonUtils.msg(player, "Deleted."); } catch(Exception e) {}
            return true;
        }
        if (sub.equals("redo") && args.length >= 3) {
            try { dungeonManager.removeLastRooms(Integer.parseInt(args[1]), Integer.parseInt(args[2])); DungeonUtils.msg(player, "Redone."); } catch(Exception e) {}
            return true;
        }
        if (sub.equals("start") && args.length >= 2) {
            try { gameManager.startDungeon(player, Integer.parseInt(args[1])); } catch(Exception e) {}
            return true;
        }
        if (sub.equals("stop")) {
            gameManager.endSession(player);
            return true;
        }
        if (sub.equals("check") && args.length >= 5) {
            try {
                int id = Integer.parseInt(args[1]); int roomId = Integer.parseInt(args[3]);
                Dungeon d = dungeonManager.getDungeon(id);
                if (d != null) {
                    List<Location> locs = d.getRoom(roomId).getMobSpawnLocations();
                    for (Location loc : locs) {
                        ArmorStand as = (ArmorStand) loc.getWorld().spawnEntity(loc, EntityType.ARMOR_STAND);
                        as.setGlowing(true); as.getEquipment().setHelmet(new ItemStack(Material.GOLDEN_HELMET)); as.setGravity(false);
                        new BukkitRunnable() { @Override public void run() { as.remove(); } }.runTaskLater(plugin, 200L);
                    }
                    DungeonUtils.msg(player, "Showing " + locs.size() + " spawns.");
                }
            } catch (Exception e) {}
            return true;
        }

        // SETUP
        if (sub.equals("setup")) {
            if (args.length < 3) return true;
            int id; try { id = Integer.parseInt(args[1]); } catch(Exception e) { return true; }
            Dungeon dungeon = dungeonManager.getDungeon(id);
            if (dungeon == null) return true;

            String type = args[2].toLowerCase();

            // LASTROOM
            if (type.equals("lastroom")) {
                if (args.length < 4) return true;
                String subType = args[3].toLowerCase();

                if (subType.equals("bossspawn")) {
                    dungeon.setBossSpawnLocation(player.getLocation());
                    dungeonManager.saveDungeon(dungeon);
                    DungeonUtils.msg(player, "Set Boss Spawn.");
                }
                else if (subType.equals("rewardloc")) {
                    dungeon.setRewardChestLocation(player.getLocation());
                    dungeonManager.saveDungeon(dungeon);
                    DungeonUtils.msg(player, "Set Reward Chest Location.");
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

            if (type.equals("entrydoor")) {
                dungeonManager.startSetup(player, id, 0, "entrydoor", args[3]);
                return true;
            }

            if (type.equals("room")) {
                try {
                    int roomId = Integer.parseInt(args[3]);
                    DungeonRoom room = dungeon.getRoom(roomId);
                    String roomAction = args[4].toLowerCase();

                    if (roomAction.equals("mobspawn")) {
                        room.addMobSpawnLocation(player.getLocation());
                        dungeonManager.saveDungeon(dungeon);
                        DungeonUtils.msg(player, "Added mob spawn.");
                    }
                    else if (roomAction.equals("resetspawns")) {
                        room.clearMobSpawnLocations();
                        dungeonManager.saveDungeon(dungeon);
                        DungeonUtils.msg(player, "Cleared spawns.");
                    }
                    else if (roomAction.equals("door")) {
                        dungeonManager.startSetup(player, id, roomId, "door", args[5]);
                    }
                } catch (Exception e) {}
                return true;
            }
        }
        return true;
    }

    private void sendHelp(Player p) {
        DungeonUtils.msg(p, "/dungeon create/delete/start/stop");
        DungeonUtils.msg(p, "/dungeon setup <id> room <room> mobspawn/door");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom bossspawn");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom rewardloc");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom bossdoor <pos1/pos2>");
        DungeonUtils.msg(p, "/dungeon setup <id> lastroom treasuredoor <pos1/pos2>");
    }
}