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
import org.bukkit.entity.Player;

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

    // --- Setup Mode Logic ---
    public void startSetup(Player player, int dungeonId, int roomId, String type, String point) {
        pendingSetups.put(player.getUniqueId(), new SetupContext(dungeonId, roomId, type, point));
        DungeonUtils.msg(player, "<green>Setup Mode:</green> Break block for <yellow>" + point + "</yellow> (" + type + ").");
    }

    public boolean isSettingUp(Player player) {
        return pendingSetups.containsKey(player.getUniqueId());
    }

    public void handleBlockBreak(Player player, Location location) {
        if (!pendingSetups.containsKey(player.getUniqueId())) return;

        SetupContext ctx = pendingSetups.remove(player.getUniqueId());
        Location[] selection = selectionCache.computeIfAbsent(player.getUniqueId(), k -> new Location[2]);

        if (ctx.point.equalsIgnoreCase("pos1")) selection[0] = location;
        else selection[1] = location;

        DungeonUtils.msg(player, "<green>Set " + ctx.point + "</green> for " + ctx.type);

        if (selection[0] != null && selection[1] != null) {
            if (!selection[0].getWorld().equals(selection[1].getWorld())) {
                DungeonUtils.error(player, "Worlds differ! Resetting.");
                selectionCache.remove(player.getUniqueId());
                return;
            }

            Dungeon dungeon = getDungeon(ctx.dungeonId);
            if (dungeon == null) return;

            Cuboid region = new Cuboid(selection[0], selection[1]);

            if (ctx.type.equalsIgnoreCase("entrydoor")) {
                dungeon.setEntryDoor(region);
                DungeonUtils.msg(player, "<gold>Entry Door saved!</gold>");
            }
            else if (ctx.type.equalsIgnoreCase("bossdoor")) {
                dungeon.setBossRoomDoor(region);
                DungeonUtils.msg(player, "<gold>Boss Room Door saved!</gold>");
            }
            else if (ctx.type.equalsIgnoreCase("treasuredoor")) {
                dungeon.setRewardDoor(region);
                DungeonUtils.msg(player, "<gold>Treasure Room Door saved!</gold>");
            }
            else if (ctx.type.equalsIgnoreCase("door")) {
                DungeonRoom room = dungeon.getRoom(ctx.roomId);
                room.setDoorRegion(region);
                DungeonUtils.msg(player, "<gold>Room " + ctx.roomId + " Door saved!</gold>");
            }

            saveDungeon(dungeon);
            selectionCache.remove(player.getUniqueId());
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
                String[] qs = { "DELETE FROM dungeons WHERE id = ?", "DELETE FROM dungeon_rooms WHERE dungeon_id = ?", "DELETE FROM dungeon_room_spawns WHERE dungeon_id = ?" };
                for (String q : qs) {
                    try (PreparedStatement ps = database.getConnection().prepareStatement(q)) { ps.setInt(1, id); ps.executeUpdate(); }
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void removeLastRooms(int dungeonId, int amount) {
        Dungeon dungeon = getDungeon(dungeonId);
        if (dungeon == null) return;
        List<Integer> roomIds = new ArrayList<>(dungeon.getRooms().keySet());
        Collections.sort(roomIds, Collections.reverseOrder());
        int removed = 0;
        for (int roomId : roomIds) {
            if (removed >= amount) break;
            dungeon.getRooms().remove(roomId);
            deleteRoomFromDB(dungeonId, roomId);
            removed++;
        }
    }

    private void deleteRoomFromDB(int dungeonId, int roomId) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM dungeon_rooms WHERE dungeon_id = ? AND room_id = ?")) {
                    ps.setInt(1, dungeonId); ps.setInt(2, roomId); ps.executeUpdate();
                }
                try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM dungeon_room_spawns WHERE dungeon_id = ? AND room_id = ?")) {
                    ps.setInt(1, dungeonId); ps.setInt(2, roomId); ps.executeUpdate();
                }
            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    public void saveAll() { for (Dungeon d : dungeons.values()) saveDungeon(d); }

    public void saveDungeon(Dungeon dungeon) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // Expanded SQL for new columns
                String sql = "INSERT OR REPLACE INTO dungeons (id, world, spawn_x, spawn_y, spawn_z, spawn_yaw, spawn_pitch, " +
                        "entry_world, entry_min_x, entry_min_y, entry_min_z, entry_max_x, entry_max_y, entry_max_z, " +
                        "boss_world, boss_x, boss_y, boss_z, chest_world, chest_x, chest_y, chest_z, " +
                        "bdoor_world, bdoor_min_x, bdoor_min_y, bdoor_min_z, bdoor_max_x, bdoor_max_y, bdoor_max_z, " +
                        "rdoor_world, rdoor_min_x, rdoor_min_y, rdoor_min_z, rdoor_max_x, rdoor_max_y, rdoor_max_z) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

                try (PreparedStatement ps = database.getConnection().prepareStatement(sql)) {
                    ps.setInt(1, dungeon.getId());
                    Location spawn = dungeon.getSpawnLocation();
                    ps.setString(2, spawn.getWorld().getName());
                    ps.setDouble(3, spawn.getX()); ps.setDouble(4, spawn.getY()); ps.setDouble(5, spawn.getZ());
                    ps.setFloat(6, spawn.getYaw()); ps.setFloat(7, spawn.getPitch());

                    saveCuboid(ps, 8, dungeon.getEntryDoor());

                    if (dungeon.getBossSpawnLocation() != null) {
                        ps.setString(15, dungeon.getBossSpawnLocation().getWorld().getName());
                        ps.setDouble(16, dungeon.getBossSpawnLocation().getX()); ps.setDouble(17, dungeon.getBossSpawnLocation().getY()); ps.setDouble(18, dungeon.getBossSpawnLocation().getZ());
                    } else { ps.setString(15, null); ps.setDouble(16,0); ps.setDouble(17,0); ps.setDouble(18,0); }

                    if (dungeon.getRewardChestLocation() != null) {
                        ps.setString(19, dungeon.getRewardChestLocation().getWorld().getName());
                        ps.setDouble(20, dungeon.getRewardChestLocation().getX()); ps.setDouble(21, dungeon.getRewardChestLocation().getY()); ps.setDouble(22, dungeon.getRewardChestLocation().getZ());
                    } else { ps.setString(19, null); ps.setDouble(20,0); ps.setDouble(21,0); ps.setDouble(22,0); }

                    saveCuboid(ps, 23, dungeon.getBossRoomDoor());
                    saveCuboid(ps, 30, dungeon.getRewardDoor());

                    ps.executeUpdate();
                }

                // ... Room/Spawn Saving (Logic same as Batch 3) ...
                String roomSql = "INSERT OR REPLACE INTO dungeon_rooms (dungeon_id, room_id, door_min_x, door_min_y, door_min_z, door_max_x, door_max_y, door_max_z) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = database.getConnection().prepareStatement(roomSql)) {
                    for (DungeonRoom room : dungeon.getRooms().values()) {
                        ps.setInt(1, dungeon.getId()); ps.setInt(2, room.getRoomId());
                        Cuboid d = room.getDoorRegion();
                        if (d!=null) { ps.setDouble(3, d.getMinX()); ps.setDouble(4, d.getMinY()); ps.setDouble(5, d.getMinZ()); ps.setDouble(6, d.getMaxX()); ps.setDouble(7, d.getMaxY()); ps.setDouble(8, d.getMaxZ()); }
                        else { ps.setDouble(3,0); ps.setDouble(4,0); ps.setDouble(5,0); ps.setDouble(6,0); ps.setDouble(7,0); ps.setDouble(8,0); }
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                try (PreparedStatement ps = database.getConnection().prepareStatement("DELETE FROM dungeon_room_spawns WHERE dungeon_id = ?")) { ps.setInt(1, dungeon.getId()); ps.executeUpdate(); }
                try (PreparedStatement ps = database.getConnection().prepareStatement("INSERT INTO dungeon_room_spawns (dungeon_id, room_id, world, x, y, z) VALUES (?, ?, ?, ?, ?, ?)")) {
                    for (DungeonRoom room : dungeon.getRooms().values()) {
                        for (Location loc : room.getMobSpawnLocations()) {
                            ps.setInt(1, dungeon.getId()); ps.setInt(2, room.getRoomId()); ps.setString(3, loc.getWorld().getName()); ps.setDouble(4, loc.getX()); ps.setDouble(5, loc.getY()); ps.setDouble(6, loc.getZ()); ps.addBatch();
                        }
                    }
                    ps.executeBatch();
                }

            } catch (SQLException e) { e.printStackTrace(); }
        });
    }

    private void saveCuboid(PreparedStatement ps, int startIdx, Cuboid c) throws SQLException {
        if (c != null) {
            ps.setString(startIdx, c.getWorld().getName());
            ps.setDouble(startIdx+1, c.getMinX()); ps.setDouble(startIdx+2, c.getMinY()); ps.setDouble(startIdx+3, c.getMinZ());
            ps.setDouble(startIdx+4, c.getMaxX()); ps.setDouble(startIdx+5, c.getMaxY()); ps.setDouble(startIdx+6, c.getMaxZ());
        } else {
            ps.setString(startIdx, null);
            for(int i=1;i<=6;i++) ps.setDouble(startIdx+i, 0);
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
                if (bossW != null) dungeon.setBossSpawnLocation(new Location(Bukkit.getWorld(bossW), rs.getDouble("boss_x"), rs.getDouble("boss_y"), rs.getDouble("boss_z")));

                String chestW = rs.getString("chest_world");
                if (chestW != null) dungeon.setRewardChestLocation(new Location(Bukkit.getWorld(chestW), rs.getDouble("chest_x"), rs.getDouble("chest_y"), rs.getDouble("chest_z")));

                String bDoorW = rs.getString("bdoor_world");
                if (bDoorW != null) dungeon.setBossRoomDoor(new Cuboid(bDoorW, rs.getDouble("bdoor_min_x"), rs.getDouble("bdoor_min_y"), rs.getDouble("bdoor_min_z"), rs.getDouble("bdoor_max_x"), rs.getDouble("bdoor_max_y"), rs.getDouble("bdoor_max_z")));

                String rDoorW = rs.getString("rdoor_world");
                if (rDoorW != null) dungeon.setRewardDoor(new Cuboid(rDoorW, rs.getDouble("rdoor_min_x"), rs.getDouble("rdoor_min_y"), rs.getDouble("rdoor_min_z"), rs.getDouble("rdoor_max_x"), rs.getDouble("rdoor_max_y"), rs.getDouble("rdoor_max_z")));

                dungeons.put(id, dungeon);
            }
            rs.close();

            // ... Load rooms/spawns (Logic same as Batch 3) ...
            ResultSet rsRooms = st.executeQuery("SELECT * FROM dungeon_rooms");
            while (rsRooms.next()) {
                int dId = rsRooms.getInt("dungeon_id");
                Dungeon d = dungeons.get(dId);
                if (d != null) {
                    int rId = rsRooms.getInt("room_id");
                    DungeonRoom room = d.getRoom(rId);
                    if (rsRooms.getDouble("door_min_x") != 0) {
                        room.setDoorRegion(new Cuboid(d.getSpawnLocation().getWorld().getName(),
                                rsRooms.getDouble("door_min_x"), rsRooms.getDouble("door_min_y"), rsRooms.getDouble("door_min_z"),
                                rsRooms.getDouble("door_max_x"), rsRooms.getDouble("door_max_y"), rsRooms.getDouble("door_max_z")));
                    }
                }
            }
            rsRooms.close();
            ResultSet rsSpawns = st.executeQuery("SELECT * FROM dungeon_room_spawns");
            while (rsSpawns.next()) {
                int dId = rsSpawns.getInt("dungeon_id");
                Dungeon d = dungeons.get(dId);
                if (d != null) {
                    int rId = rsSpawns.getInt("room_id");
                    DungeonRoom room = d.getRoom(rId);
                    String wName = rsSpawns.getString("world");
                    if (wName != null && Bukkit.getWorld(wName) != null) {
                        room.addMobSpawnLocation(new Location(Bukkit.getWorld(wName), rsSpawns.getDouble("x"), rsSpawns.getDouble("y"), rsSpawns.getDouble("z")));
                    }
                }
            }
            rsSpawns.close();
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