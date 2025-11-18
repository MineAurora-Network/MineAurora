package me.login.misc.dailyquests;

import me.login.Login;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.EnumSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.Arrays;

public class QuestsDatabase {

    private final QuestsModule module;
    private final Login plugin;
    private Connection connection;

    public QuestsDatabase(QuestsModule module) {
        this.module = module;
        this.plugin = module.getPlugin();
    }

    public void connect() {
        try {
            File dbFile = new File(plugin.getDataFolder(), "database/Quests.db");
            if (!dbFile.getParentFile().exists()) {
                dbFile.getParentFile().mkdirs();
            }
            String url = "jdbc:sqlite:" + dbFile.getAbsolutePath();
            connection = DriverManager.getConnection(url);
            plugin.getLogger().info("Connected to Quests SQLite database.");
            createTables();
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not connect to Quests SQLite database: " + e.getMessage());
        }
    }

    public void disconnect() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                plugin.getLogger().info("Disconnected from Quests SQLite database.");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error disconnecting from Quests SQLite database: " + e.getMessage());
        }
    }

    private void createTables() {
        String sql = "CREATE TABLE IF NOT EXISTS player_quests ("
                + "uuid TEXT PRIMARY KEY NOT NULL,"
                + "last_reset INTEGER DEFAULT 0,"
                + "daily_easy_id TEXT,"
                + "daily_hard_id TEXT,"
                + "daily_extreme_id TEXT,"
                + "active_quest_id TEXT,"
                + "active_quest_progress INTEGER DEFAULT 0,"
                + "completed_types TEXT"
                + ");";
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().severe("Could not create Quests database table: " + e.getMessage());
        }
    }

    public PlayerQuestData loadPlayerQuests(UUID uuid) {
        PlayerQuestData data = new PlayerQuestData(uuid);
        String sql = "SELECT * FROM player_quests WHERE uuid = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                data.setLastQuestResetTimestamp(rs.getLong("last_reset"));
                data.setDailyEasyQuest(module.getQuestManager().getQuestById(rs.getString("daily_easy_id")));
                data.setDailyHardQuest(module.getQuestManager().getQuestById(rs.getString("daily_hard_id")));
                data.setDailyExtremeQuest(module.getQuestManager().getQuestById(rs.getString("daily_extreme_id")));
                data.setActiveQuest(module.getQuestManager().getQuestById(rs.getString("active_quest_id")));
                data.setActiveQuestProgress(rs.getInt("active_quest_progress"));

                String completed = rs.getString("completed_types");
                if (completed != null && !completed.isEmpty()) {
                    EnumSet<QuestType> completedSet = EnumSet.noneOf(QuestType.class);
                    Arrays.stream(completed.split(","))
                            .map(QuestType::valueOf)
                            .forEach(completedSet::add);
                    data.setCompletedQuestTypes(completedSet);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("Error loading player quest data: " + e.getMessage());
        }
        return data;
    }

    public void savePlayerQuests(PlayerQuestData data) {
        String sql = "INSERT OR REPLACE INTO player_quests (uuid, last_reset, daily_easy_id, daily_hard_id, daily_extreme_id, active_quest_id, active_quest_progress, completed_types) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, data.getPlayerUUID().toString());
            pstmt.setLong(2, data.getLastQuestResetTimestamp());
            pstmt.setString(3, data.getDailyEasyQuest() != null ? data.getDailyEasyQuest().getId() : null);
            pstmt.setString(4, data.getDailyHardQuest() != null ? data.getDailyHardQuest().getId() : null);
            pstmt.setString(5, data.getDailyExtremeQuest() != null ? data.getDailyExtremeQuest().getId() : null);
            pstmt.setString(6, data.getActiveQuest() != null ? data.getActiveQuest().getId() : null);
            pstmt.setInt(7, data.getActiveQuestProgress());

            String completed = data.getCompletedQuestTypes().stream()
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
            pstmt.setString(8, completed);

            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error saving player quest data: " + e.getMessage());
        }
    }

    public void updateActiveQuestProgress(UUID uuid, int progress) {
        String sql = "UPDATE player_quests SET active_quest_progress = ? WHERE uuid = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, progress);
            pstmt.setString(2, uuid.toString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().severe("Error updating active quest progress: " + e.getMessage());
        }
    }
}