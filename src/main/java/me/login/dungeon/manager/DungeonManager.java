package me.login.dungeon.manager;

import me.login.Login;
import me.login.dungeon.data.Database;
import me.login.dungeon.model.Cuboid;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import me.login.dungeon.game.MobManager;
import org.bukkit.persistence.PersistentDataType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

public class DungeonManager {

    private final Login plugin;
    private final Database database;
    private final Map<Integer, Dungeon> dungeons = new HashMap<>();
    private final Map<UUID, SetupContext> pendingSetups = new HashMap<>();
    private final Map<UUID, Location[]> selectionCache = new HashMap<>();

    public DungeonManager(Login plugin, Database database) {
        this.plugin = plugin;
        this.database = database;
        loadDungeons();
    }

    public String getDungeonInfo(int id) {
        Dungeon d = dungeons.get(id);
        if (d == null) return "§cDungeon " + id + " not found.";

        StringBuilder sb = new StringBuilder();
        sb.append("§6--- Dungeon ").append(id).append(" Info ---").append("\n");
        sb.append("§eSpawn: ").append(locToStr(d.getSpawnLocation())).append("\n");
        sb.append("§eRooms: ").append(d.getRooms().size()).append("\n");
        sb.append("§eEntry Door: ").append(d.getEntryDoor() != null ? "Set" : "§cNot Set").append("\n");
        sb.append("§eBoss Spawn: ").append(d.getBossSpawnLocation() != null ? "Set" : "§cNot Set").append("\n");
        sb.append("§eReward Chest: ").append(d.getRewardChestLocation() != null ? "Set" : "§cNot Set").append("\n");
        sb.append("§eMini Reward Chest: ").append(d.getMiniRewardChestLocation() != null ? "Set" : "§cNot Set").append("\n");

        // Count total mob spawns via markers nearby
        if (d.getSpawnLocation() != null) {
            int count = 0;
            for (Entity e : d.getSpawnLocation().getWorld().getNearbyEntities(d.getSpawnLocation(), 250, 50, 250)) {
                if (e instanceof TextDisplay && e.getPersistentDataContainer().has(MobManager.MARKER_KEY, PersistentDataType.INTEGER)) {
                    count++;
                }
            }
            sb.append("§eTotal Mob Markers Found: ").append(count);
        }
        return sb.toString();
    }

    private String locToStr(Location loc) {
        if (loc == null) return "§cNone";
        return loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
    }

    // --- Setup Mode Logic ---
    public void startSetup(Player player, int dungeonId, int roomId, String type, String point) {
        pendingSetups.put(player.getUniqueId(), new SetupContext(dungeonId, roomId, type, point));

        if (type.equalsIgnoreCase("chest")) {
            DungeonUtils.msg(player, "<green>Setup Mode:</green> Right-Click a Chest to save it.");
        } else {
            DungeonUtils.msg(player, "<green>Setup Mode:</green> Break block for <yellow>" + point + "</yellow> (" + type + ").");
        }
    }

    public boolean isSettingUp(Player player) {
        return pendingSetups.containsKey(player.getUniqueId());
    }

    public void handleBlockBreak(Player player, Location location) {
        if (!pendingSetups.containsKey(player.getUniqueId())) return;
        SetupContext ctx = pendingSetups.get(player.getUniqueId());
        if (ctx.type.equalsIgnoreCase("chest")) return;

        pendingSetups.remove(player.getUniqueId());

        Location[] selection = selectionCache.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);
        if (ctx.point.equalsIgnoreCase("pos1")) selection[0] = location; else selection[1] = location;
        DungeonUtils.msg(player, "<green>Set " + ctx.point + "</green> for " + ctx.type);

        if (selection[0] != null && selection[1] != null) {
            if (!selection[0].getWorld().getName().equals(selection[1].getWorld().getName())) {
                DungeonUtils.error(player, "Worlds differ! Resetting."); selectionCache.remove(player.getUniqueId()); return;
            }
            Dungeon dungeon = getDungeon(ctx.dungeonId);
            if (dungeon == null) return;
            Cuboid region = new Cuboid(selection[0], selection[1]);

            if (ctx.type.equalsIgnoreCase("entrydoor")) dungeon.setEntryDoor(region);
            else if (ctx.type.equalsIgnoreCase("bossdoor")) dungeon.setBossRoomDoor(region);
            else if (ctx.type.equalsIgnoreCase("treasuredoor")) dungeon.setRewardDoor(region);
            else if (ctx.type.equalsIgnoreCase("door")) dungeon.getRoom(ctx.roomId).setDoorRegion(region);

            saveDungeon(dungeon);
            selectionCache.remove(player.getUniqueId());
            DungeonUtils.msg(player, "<gold>Region saved!</gold>");
        }
    }

    public void handleInteract(Player player, Block block) {
        if (!pendingSetups.containsKey(player.getUniqueId())) return;
        SetupContext ctx = pendingSetups.get(player.getUniqueId());

        if (ctx.type.equalsIgnoreCase("chest")) {
            pendingSetups.remove(player.getUniqueId());
            Dungeon dungeon = getDungeon(ctx.dungeonId);
            if (dungeon != null) {
                dungeon.addChestLocation(block.getLocation());
                saveDungeon(dungeon);
                DungeonUtils.msg(player, "<gold>Chest added to Dungeon " + ctx.dungeonId + "!</gold>");
            }
        }
    }

    // --- Core Logic ---
    public Dungeon getDungeon(int id) { return dungeons.get(id); }

    public Dungeon findEmptyDungeon() {
        return dungeons.values().stream().filter(d -> !d.isOccupied() && d.isSetupComplete()).findFirst().orElse(null);
    }

    public Dungeon createDungeon(int id, Location spawn) {
        Dungeon dungeon = new Dungeon(id);
        dungeon.setSpawnLocation(spawn);
        dungeons.put(id, dungeon);
        saveDungeon(dungeon);
        return dungeon;
    }

    public void deleteDungeon(int id) {
        dungeons.remove(id);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String[] qs = { "DELETE FROM dungeons WHERE id = ?", "DELETE FROM dungeon_rooms WHERE dungeon_id = ?", "DELETE FROM dungeon_chests WHERE dungeon_id = ?" };
                for (String q : qs) {
                    try (PreparedStatement ps = database.getConnection().prepareStatement(q)) { ps.setInt(1, id); ps.executeUpdate(); }
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public List<Integer> removeLastRooms(int dungeonId, int amount) {
        Dungeon dungeon = getDungeon(dungeonId);
        List<Integer> removedIds = new ArrayList<>();
        if (dungeon == null) return removedIds;
        List<Integer> roomIds = new ArrayList<>(dungeon.getRooms().keySet());
        Collections.sort(roomIds, Collections.reverseOrder());
        int removed = 0;
        for (int roomId : roomIds) {
            if (removed >= amount) break;
            dungeon.getRooms().remove(roomId);
            deleteRoomFromDB(dungeonId, roomId);
            removedIds.add(roomId);
            removed++;
        }
        return removedIds;
    }

    private void deleteRoomFromDB(int dungeonId, int roomId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM dungeon_rooms WHERE dungeon_id = ? AND room_id = ?")) {
                    ps.setInt(1, dungeonId); ps.setInt(2, roomId); ps.executeUpdate();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void saveAll() { for (Dungeon d : dungeons.values()) saveDungeon(d); }

    public void saveDungeon(Dungeon dungeon) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Updated SQL to include Mini Chest
                String sql = "INSERT OR REPLACE INTO dungeons (id, world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, entry_world, entry_min_x, entry_min_y, entry_min_z, entry_max_x, entry_max_y, entry_max_z, boss_world, boss_x, boss_y, boss_z, chest_world, chest_x, chest_y, chest_z, bdoor_world, bdoor_min_x, bdoor_min_y, bdoor_min_z, bdoor_max_x, bdoor_max_y, bdoor_max_z, rdoor_world, rdoor_min_x, rdoor_min_y, rdoor_min_z, rdoor_max_x, rdoor_max_y, rdoor_max_z, minic_world, minic_x, minic_y, minic_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                    ps.setInt(1, dungeon.getId());
                    Location spawn = dungeon.getSpawnLocation();
                    ps.setString(2, spawn.getWorld().getName());
                    ps.setDouble(3, spawn.getX()); ps.setDouble(4, spawn.getY()); ps.setDouble(5, spawn.getZ()); ps.setFloat(6, spawn.getYaw()); ps.setFloat(7, spawn.getPitch());
                    saveCuboid(ps, 8, dungeon.getEntryDoor());
                    if (dungeon.getBossSpawnLocation() != null) {
                        ps.setString(15, dungeon.getBossSpawnLocation().getWorld().getName()); ps.setDouble(16, dungeon.getBossSpawnLocation().getX()); ps.setDouble(17, dungeon.getBossSpawnLocation().getY()); ps.setDouble(18, dungeon.getBossSpawnLocation().getZ());
                    } else { ps.setString(15, null); ps.setDouble(16,0); ps.setDouble(17,0); ps.setDouble(18,0); }
                    if (dungeon.getRewardChestLocation() != null) {
                        ps.setString(19, dungeon.getRewardChestLocation().getWorld().getName()); ps.setDouble(20, dungeon.getRewardChestLocation().getX()); ps.setDouble(21, dungeon.getRewardChestLocation().getY()); ps.setDouble(22, dungeon.getRewardChestLocation().getZ());
                    } else { ps.setString(19, null); ps.setDouble(20,0); ps.setDouble(21,0); ps.setDouble(22,0); }
                    saveCuboid(ps, 23, dungeon.getBossRoomDoor()); saveCuboid(ps, 30, dungeon.getRewardDoor());

                    // Mini Chest
                    if (dungeon.getMiniRewardChestLocation() != null) {
                        ps.setString(37, dungeon.getMiniRewardChestLocation().getWorld().getName());
                        ps.setDouble(38, dungeon.getMiniRewardChestLocation().getX());
                        ps.setDouble(39, dungeon.getMiniRewardChestLocation().getY());
                        ps.setDouble(40, dungeon.getMiniRewardChestLocation().getZ());
                    } else {
                        ps.setString(37, null); ps.setDouble(38, 0); ps.setDouble(39, 0); ps.setDouble(40, 0);
                    }

                    ps.executeUpdate();
                }

                String roomSql = "INSERT OR REPLACE INTO dungeon_rooms (dungeon_id, room_id, door_min_x, door_min_y, door_min_z, door_max_x, door_max_y, door_max_z, door_world) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = database.getConnection().prepareStatement(roomSql)) {
                    for (DungeonRoom room : dungeon.getRooms().values()) {
                        ps.setInt(1, dungeon.getId()); ps.setInt(2, room.getRoomId());
                        Cuboid d = room.getDoorRegion();
                        if (d!=null) { ps.setDouble(3, d.getMinX()); ps.setDouble(4, d.getMinY()); ps.setDouble(5, d.getMinZ()); ps.setDouble(6, d.getMaxX()); ps.setDouble(7, d.getMaxY()); ps.setDouble(8, d.getMaxZ()); ps.setString(9, d.getWorld().getName()); }
                        else { ps.setDouble(3,0); ps.setDouble(4,0); ps.setDouble(5,0); ps.setDouble(6,0); ps.setDouble(7,0); ps.setDouble(8,0); ps.setString(9, null); }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

                try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM dungeon_chests WHERE dungeon_id = ?")) { ps.setInt(1, dungeon.getId()); ps.executeUpdate(); }
                try (PreparedStatement ps = database.getConnection().prepareStatement("INSERT INTO dungeon_chests (dungeon_id, world, x, y, z) VALUES (?, ?, ?, ?, ?)")) {
                    for (Location loc : dungeon.getChestLocations()) {
                        ps.setInt(1, dungeon.getId());
                        ps.setString(2, loc.getWorld().getName());
                        ps.setInt(3, loc.getBlockX());
                        ps.setInt(4, loc.getBlockY());
                        ps.setInt(5, loc.getBlockZ());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }

            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void saveCuboid(PreparedStatement ps, int startIdx, Cuboid c) throws SQLException {
        if (c != null) {
            ps.setString(startIdx, c.getWorld().getName());
            ps.setDouble(startIdx+1, c.getMinX()); ps.setDouble(startIdx+2, c.getMinY()); ps.setDouble(startIdx+3, c.getMinZ()); ps.setDouble(startIdx+4, c.getMaxX()); ps.setDouble(startIdx+5, c.getMaxY()); ps.setDouble(startIdx+6, c.getMaxZ());
        } else {
            ps.setString(startIdx, null); for(int i=1;i<=6;i++) ps.setDouble(startIdx+i, 0);
        }
    }

    private void loadDungeons() {
        try {
            Statement st = database.getConnection().createStatement();
            ResultSet rs = st.executeQuery("SELECT * FROM dungeons");
            while (rs.next()) {
                int id = rs.getInt("id");
                Dungeon dungeon = new Dungeon(id);
                World w = Bukkit.getWorld(rs.getString("world"));
                if (w != null) dungeon.setSpawnLocation(new Location(w, rs.getDouble("spawn_x"), rs.getDouble("spawn_y"), rs.getDouble("spawn_z"), rs.getFloat("spawn_yaw"), rs.getFloat("spawn_pitch")));
                String entryW = rs.getString("entry_world");
                if (entryW != null) dungeon.setEntryDoor(new Cuboid(entryW, rs.getDouble("entry_min_x"), rs.getDouble("entry_min_y"), rs.getDouble("entry_min_z"), rs.getDouble("entry_max_x"), rs.getDouble("entry_max_y"), rs.getDouble("entry_max_z")));
                String bossW = rs.getString("boss_world");
                if (bossW != null && Bukkit.getWorld(bossW) != null) dungeon.setBossSpawnLocation(new Location(Bukkit.getWorld(bossW), rs.getDouble("boss_x"), rs.getDouble("boss_y"), rs.getDouble("boss_z")));
                String chestW = rs.getString("chest_world");
                if (chestW != null && Bukkit.getWorld(chestW) != null) dungeon.setRewardChestLocation(new Location(Bukkit.getWorld(chestW), rs.getDouble("chest_x"), rs.getDouble("chest_y"), rs.getDouble("chest_z")));
                String bDoorW = rs.getString("bdoor_world");
                if (bDoorW != null) dungeon.setBossRoomDoor(new Cuboid(bDoorW, rs.getDouble("bdoor_min_x"), rs.getDouble("bdoor_min_y"), rs.getDouble("bdoor_min_z"), rs.getDouble("bdoor_max_x"), rs.getDouble("bdoor_max_y"), rs.getDouble("bdoor_max_z")));
                String rDoorW = rs.getString("rdoor_world");
                if (rDoorW != null) dungeon.setRewardDoor(new Cuboid(rDoorW, rs.getDouble("rdoor_min_x"), rs.getDouble("rdoor_min_y"), rs.getDouble("rdoor_min_z"), rs.getDouble("rdoor_max_x"), rs.getDouble("rdoor_max_y"), rs.getDouble("rdoor_max_z")));

                // Load Mini Chest
                String minicW = rs.getString("minic_world");
                if (minicW != null && Bukkit.getWorld(minicW) != null) {
                    dungeon.setMiniRewardChestLocation(new Location(Bukkit.getWorld(minicW), rs.getDouble("minic_x"), rs.getDouble("minic_y"), rs.getDouble("minic_z")));
                }

                dungeons.put(id, dungeon);
            }
            rs.close();

            ResultSet rsRooms = st.executeQuery("SELECT * FROM dungeon_rooms");
            while (rsRooms.next()) {
                int dId = rsRooms.getInt("dungeon_id");
                Dungeon d = dungeons.get(dId);
                if (d != null) {
                    int rId = rsRooms.getInt("room_id");
                    DungeonRoom room = d.getRoom(rId);
                    if (rsRooms.getDouble("door_min_x") != 0) {
                        String worldName = rsRooms.getString("door_world");
                        if (worldName == null) { if (d.getEntryDoor() != null) worldName = d.getEntryDoor().getWorld().getName(); else if (d.getSpawnLocation() != null) worldName = d.getSpawnLocation().getWorld().getName(); }
                        if (worldName != null) { room.setDoorRegion(new Cuboid(worldName, rsRooms.getDouble("door_min_x"), rsRooms.getDouble("door_min_y"), rsRooms.getDouble("door_min_z"), rsRooms.getDouble("door_max_x"), rsRooms.getDouble("door_max_y"), rsRooms.getDouble("door_max_z"))); }
                    }
                }
            }
            rsRooms.close();

            ResultSet rsChests = st.executeQuery("SELECT * FROM dungeon_chests");
            while (rsChests.next()) {
                int dId = rsChests.getInt("dungeon_id");
                Dungeon d = dungeons.get(dId);
                if (d != null) {
                    String wName = rsChests.getString("world");
                    if (wName != null && Bukkit.getWorld(wName) != null) {
                        d.addChestLocation(new Location(Bukkit.getWorld(wName), rsChests.getInt("x"), rsChests.getInt("y"), rsChests.getInt("z")));
                    }
                }
            }
            rsChests.close();

            st.close();
            plugin.getLogger().info("Loaded " + dungeons.size() + " dungeons.");
        } catch (SQLException e) { e.printStackTrace(); }
    }

    private static class SetupContext {
        int dungeonId; int roomId; String type; String point;
        public SetupContext(int dungeonId, int roomId, String type, String point) {
            this.dungeonId = dungeonId; this.roomId = roomId; this.type = type; this.point = point;
        }
    }
}