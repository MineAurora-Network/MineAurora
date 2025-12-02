package me.login.moderation;

import me.login.Login;
import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.logging.Level;

public class ModerationDatabase {

    private final Login plugin;
    private Connection connection;
    private final String dbPath;

    public ModerationDatabase(Login plugin) {
        this.plugin = plugin;
        this.dbPath = new File(plugin.getDataFolder(), "database").getAbsolutePath() + File.separator + "moderation.db";
        initializeDatabase();
    }

    private Connection getSQLConnection() {
        File dbFile = new File(dbPath);
        if (!dbFile.getParentFile().exists()) {
            dbFile.getParentFile().mkdirs();
        }

        if (!dbFile.exists()) {
            try {
                if (!dbFile.createNewFile()) {
                    plugin.getLogger().severe("Could not create moderation database file!");
                    return null;
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "IO Exception while creating database file.", e);
                return null;
            }
        }
        try {
            if (connection != null && !connection.isClosed()) {
                return connection;
            }
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
            return connection;
        } catch (SQLException | ClassNotFoundException ex) {
            plugin.getLogger().log(Level.SEVERE, "SQLite exception on initialize", ex);
        }
        return null;
    }

    public void initializeDatabase() {
        connection = getSQLConnection();
        try {
            Statement s = connection.createStatement();
            // Mutes table
            s.executeUpdate("CREATE TABLE IF NOT EXISTS mutes (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`player_uuid` VARCHAR(36) NOT NULL," +
                    "`player_name` VARCHAR(16) NOT NULL," +
                    "`staff_uuid` VARCHAR(36) NOT NULL," +
                    "`staff_name` VARCHAR(16) NOT NULL," +
                    "`reason` TEXT NOT NULL," +
                    "`start_time` BIGINT NOT NULL," +
                    "`end_time` BIGINT NOT NULL," +
                    "`active` BOOLEAN DEFAULT 1" +
                    ");");

            // Bans table
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bans (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`player_uuid` VARCHAR(36)," +
                    "`player_name` VARCHAR(16)," +
                    "`ip_address` VARCHAR(45)," +
                    "`staff_uuid` VARCHAR(36) NOT NULL," +
                    "`staff_name` VARCHAR(16) NOT NULL," +
                    "`reason` TEXT NOT NULL," +
                    "`start_time` BIGINT NOT NULL," +
                    "`end_time` BIGINT NOT NULL," +
                    "`type` VARCHAR(10) NOT NULL," +
                    "`active` BOOLEAN DEFAULT 1" +
                    ");");

            // Reports Table (THIS IS WHAT WAS MISSING)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS reports (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`reporter_uuid` VARCHAR(36) NOT NULL," +
                    "`reporter_name` VARCHAR(16) NOT NULL," +
                    "`reported_uuid` VARCHAR(36) NOT NULL," +
                    "`reported_name` VARCHAR(16) NOT NULL," +
                    "`reason` TEXT NOT NULL," +
                    "`timestamp` BIGINT NOT NULL" +
                    ");");

            s.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating database tables", e);
        }
    }

    // --- Mute/Ban Methods (Required for other commands) ---

    public List<Map<String, Object>> getMuteHistory(UUID playerUUID) {
        return getHistory("mutes", playerUUID);
    }

    public List<Map<String, Object>> getBanHistory(UUID playerUUID) {
        return getHistory("bans", playerUUID); // Fixed: Bans table uses 'type' filter usually, simplified here
    }

    private List<Map<String, Object>> getHistory(String table, UUID uuid) {
        List<Map<String, Object>> history = new ArrayList<>();
        String sql = "SELECT * FROM " + table + " WHERE player_uuid = ? ORDER BY start_time DESC LIMIT 10";
        if (table.equals("bans")) {
            sql = "SELECT * FROM bans WHERE player_uuid = ? AND type = 'BAN' ORDER BY start_time DESC LIMIT 10";
        }

        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Map<String, Object> entry = new HashMap<>();
                entry.put("staff_name", rs.getString("staff_name"));
                entry.put("reason", rs.getString("reason"));
                entry.put("start_time", rs.getLong("start_time"));
                entry.put("end_time", rs.getLong("end_time"));
                entry.put("active", rs.getBoolean("active"));
                history.add(entry);
            }
        } catch (SQLException ex) {
            ex.printStackTrace();
        }
        return history;
    }

    public boolean mutePlayer(UUID playerUUID, String playerName, UUID staffUUID, String staffName, String reason, long duration) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;
        deactivatePastMutes(playerUUID);

        String sql = "INSERT INTO mutes(player_uuid, player_name, staff_uuid, staff_name, reason, start_time, end_time, active) VALUES(?,?,?,?,?,?,?,1)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString()); ps.setString(2, playerName); ps.setString(3, staffUUID.toString());
            ps.setString(4, staffName); ps.setString(5, reason); ps.setLong(6, startTime); ps.setLong(7, endTime);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public boolean banPlayer(UUID playerUUID, String playerName, UUID staffUUID, String staffName, String reason, long duration) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;
        deactivatePastBans(playerUUID);

        String sql = "INSERT INTO bans(player_uuid, player_name, staff_uuid, staff_name, reason, start_time, end_time, type, active) VALUES(?,?,?,?,?,?,?,'BAN',1)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString()); ps.setString(2, playerName); ps.setString(3, staffUUID.toString());
            ps.setString(4, staffName); ps.setString(5, reason); ps.setLong(6, startTime); ps.setLong(7, endTime);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public boolean ipBanPlayer(String ip, UUID staffUUID, String staffName, String reason, long duration, UUID playerUUID, String playerName) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;
        deactivatePastIpBans(ip);
        String sql = "INSERT INTO bans(ip_address, staff_uuid, staff_name, reason, start_time, end_time, type, active, player_uuid, player_name) VALUES(?,?,?,?,?,?,'IPBAN',1,?,?)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ip); ps.setString(2, staffUUID.toString()); ps.setString(3, staffName);
            ps.setString(4, reason); ps.setLong(5, startTime); ps.setLong(6, endTime);
            ps.setString(7, playerUUID != null ? playerUUID.toString() : null); ps.setString(8, playerName);
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public Map<String, Object> getActiveMuteInfo(UUID uuid) { return getActiveInfo("mutes", uuid, null); }
    public Map<String, Object> getActiveBanInfo(UUID uuid) { return getActiveInfo("bans", uuid, "BAN"); }
    public Map<String, Object> getActiveIpBanInfo(String ip) {
        try(PreparedStatement ps=getSQLConnection().prepareStatement("SELECT * FROM bans WHERE ip_address=? AND type='IPBAN' AND active=1")){
            ps.setString(1,ip); return processResultSet(ps);
        }catch(SQLException e){return null;}
    }

    private Map<String, Object> getActiveInfo(String table, UUID uuid, String type) {
        String sql = "SELECT * FROM " + table + " WHERE player_uuid = ? AND active = 1" + (type != null ? " AND type = ?" : "");
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            if (type != null) ps.setString(2, type);
            return processResultSet(ps);
        } catch (SQLException ex) { return null; }
    }

    private Map<String, Object> processResultSet(PreparedStatement ps) throws SQLException {
        ResultSet rs = ps.executeQuery();
        if (rs.next()) {
            long endTime = rs.getLong("end_time");
            if (endTime != -1 && System.currentTimeMillis() > endTime) {
                try(PreparedStatement up = getSQLConnection().prepareStatement("UPDATE " + rs.getMetaData().getTableName(1) + " SET active=0 WHERE id=?")) {
                    up.setInt(1, rs.getInt("id")); up.executeUpdate();
                }
                return null;
            }
            Map<String, Object> info = new HashMap<>();
            info.put("staff_name", rs.getString("staff_name"));
            info.put("reason", rs.getString("reason"));
            info.put("end_time", endTime);
            info.put("start_time", rs.getLong("start_time"));
            return info;
        }
        return null;
    }

    public boolean unmutePlayer(UUID uuid) { return deactivatePastMutes(uuid); }
    public boolean unbanPlayer(UUID uuid) { return deactivatePastBans(uuid); }
    public boolean unbanIp(String ip) { return deactivatePastIpBans(ip); }

    private boolean deactivatePastMutes(UUID uuid) { return executeUpdate("UPDATE mutes SET active=0 WHERE player_uuid=?", uuid.toString()); }
    private boolean deactivatePastBans(UUID uuid) { return executeUpdate("UPDATE bans SET active=0 WHERE player_uuid=? AND type='BAN'", uuid.toString()); }
    private boolean deactivatePastIpBans(String ip) { return executeUpdate("UPDATE bans SET active=0 WHERE ip_address=? AND type='IPBAN'", ip); }
    private boolean executeUpdate(String sql, String arg) {
        try(PreparedStatement ps = getSQLConnection().prepareStatement(sql)){ ps.setString(1,arg); return ps.executeUpdate()>0; }catch(SQLException e){return false;}
    }

    // --- REPORT SYSTEM METHODS (THESE WERE MISSING) ---

    public void addReport(UUID reporterId, String reporterName, UUID reportedId, String reportedName, String reason) {
        String sql = "INSERT INTO reports(reporter_uuid, reporter_name, reported_uuid, reported_name, reason, timestamp) VALUES(?,?,?,?,?,?)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, reporterId.toString());
            ps.setString(2, reporterName);
            ps.setString(3, reportedId.toString());
            ps.setString(4, reportedName);
            ps.setString(5, reason);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<Map<String, Object>> getAllReports() {
        List<Map<String, Object>> list = new ArrayList<>();
        try (Statement s = getSQLConnection().createStatement();
             ResultSet rs = s.executeQuery("SELECT * FROM reports ORDER BY timestamp DESC")) {
            while (rs.next()) {
                list.add(mapReport(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public List<Map<String, Object>> getReportsForPlayer(UUID reportedUuid) {
        List<Map<String, Object>> list = new ArrayList<>();
        try (PreparedStatement ps = getSQLConnection().prepareStatement("SELECT * FROM reports WHERE reported_uuid = ? ORDER BY timestamp DESC")) {
            ps.setString(1, reportedUuid.toString());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                list.add(mapReport(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return list;
    }

    public boolean deleteReport(UUID reportedUuid, int id) {
        try (PreparedStatement ps = getSQLConnection().prepareStatement("DELETE FROM reports WHERE id = ? AND reported_uuid = ?")) {
            ps.setInt(1, id);
            ps.setString(2, reportedUuid.toString());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, Object> mapReport(ResultSet rs) throws SQLException {
        Map<String, Object> map = new HashMap<>();
        map.put("id", rs.getInt("id"));
        map.put("reporter_name", rs.getString("reporter_name"));
        map.put("reported_name", rs.getString("reported_name"));
        map.put("reason", rs.getString("reason"));
        map.put("timestamp", rs.getLong("timestamp"));
        return map;
    }

    public void closeConnection() {
        try { if (connection != null && !connection.isClosed()) connection.close(); } catch (SQLException e) {}
    }
}