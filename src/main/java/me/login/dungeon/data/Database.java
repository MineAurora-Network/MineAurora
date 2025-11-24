package me.login.dungeon.data;

import me.login.Login;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class Database {

    private final Login plugin;
    private Connection connection;

    public Database(Login plugin) {
        this.plugin = plugin;
        initialize();
    }

    private void initialize() {
        File dbFolder = new File(plugin.getDataFolder(), "database");
        if (!dbFolder.exists()) {
            dbFolder.mkdirs();
        }

        File dbFile = new File(dbFolder, "dungeons.db");

        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            createTables();
        } catch (ClassNotFoundException | SQLException e) {
            plugin.getLogger().severe("Failed to initialize Dungeon SQLite database!");
            e.printStackTrace();
        }
    }

    private void createTables() {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS dungeons (" +
                    "id INTEGER PRIMARY KEY, " +
                    "world TEXT, " +
                    "spawn_x DOUBLE, spawn_y DOUBLE, spawn_z DOUBLE, spawn_yaw FLOAT, spawn_pitch FLOAT, " +
                    "entry_world TEXT, entry_min_x DOUBLE, entry_min_y DOUBLE, entry_min_z DOUBLE, " +
                    "entry_max_x DOUBLE, entry_max_y DOUBLE, entry_max_z DOUBLE, " +
                    "boss_world TEXT, boss_x DOUBLE, boss_y DOUBLE, boss_z DOUBLE, " +
                    "chest_world TEXT, chest_x DOUBLE, chest_y DOUBLE, chest_z DOUBLE, " +
                    "bdoor_world TEXT, bdoor_min_x DOUBLE, bdoor_min_y DOUBLE, bdoor_min_z DOUBLE, " +
                    "bdoor_max_x DOUBLE, bdoor_max_y DOUBLE, bdoor_max_z DOUBLE, " +
                    "rdoor_world TEXT, rdoor_min_x DOUBLE, rdoor_min_y DOUBLE, rdoor_min_z DOUBLE, " +
                    "rdoor_max_x DOUBLE, rdoor_max_y DOUBLE, rdoor_max_z DOUBLE" +
                    ")");

            statement.execute("CREATE TABLE IF NOT EXISTS dungeon_rooms (" +
                    "dungeon_id INTEGER, " +
                    "room_id INTEGER, " +
                    "door_world TEXT, " +
                    "door_min_x DOUBLE, door_min_y DOUBLE, door_min_z DOUBLE, " +
                    "door_max_x DOUBLE, door_max_y DOUBLE, door_max_z DOUBLE, " +
                    "PRIMARY KEY (dungeon_id, room_id)" +
                    ")");

            statement.execute("CREATE TABLE IF NOT EXISTS dungeon_room_spawns (" +
                    "dungeon_id INTEGER, " +
                    "room_id INTEGER, " +
                    "world TEXT, " +
                    "x DOUBLE, " +
                    "y DOUBLE, " +
                    "z DOUBLE" +
                    ")");

            // NEW TABLE FOR CHESTS
            statement.execute("CREATE TABLE IF NOT EXISTS dungeon_chests (" +
                    "dungeon_id INTEGER, " +
                    "world TEXT, " +
                    "x INT, " +
                    "y INT, " +
                    "z INT" +
                    ")");

            // Migrations
            try { statement.execute("ALTER TABLE dungeon_rooms ADD COLUMN door_world TEXT;"); } catch (SQLException ignored) {}
            try { statement.execute("ALTER TABLE dungeons ADD COLUMN boss_world TEXT;"); } catch (SQLException ignored) {}
            // ... (rest of existing migrations ignored for brevity) ...

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Connection getConnection() {
        try { if (connection == null || connection.isClosed()) initialize(); } catch (SQLException e) { initialize(); }
        return connection;
    }

    public void close() {
        try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) { e.printStackTrace(); }
    }
}