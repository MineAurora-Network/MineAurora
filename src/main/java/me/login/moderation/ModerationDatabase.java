package me.login.moderation;

import me.login.Login;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class ModerationDatabase {

    private final Login plugin;
    private Connection connection;
    private final String dbPath;

    public ModerationDatabase(Login plugin) {
        this.plugin = plugin;
        this.dbPath = plugin.getDataFolder().getAbsolutePath() + File.separator + "moderation.db";
        initializeDatabase();
    }

    private Connection getSQLConnection() {
        File dataFolder = new File(dbPath);
        if (!dataFolder.exists()) {
            try {
                // Create the file if it doesn't exist
                if (!dataFolder.createNewFile()) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create database file!");
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

            // Bans table (for both player and IP bans)
            s.executeUpdate("CREATE TABLE IF NOT EXISTS bans (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "`player_uuid` VARCHAR(36)," + // Nullable for IP bans
                    "`player_name` VARCHAR(16)," + // Nullable for IP bans
                    "`ip_address` VARCHAR(45)," + // Nullable for player bans
                    "`staff_uuid` VARCHAR(36) NOT NULL," +
                    "`staff_name` VARCHAR(16) NOT NULL," +
                    "`reason` TEXT NOT NULL," +
                    "`start_time` BIGINT NOT NULL," +
                    "`end_time` BIGINT NOT NULL," +
                    "`type` VARCHAR(10) NOT NULL," + // 'BAN' or 'IPBAN'
                    "`active` BOOLEAN DEFAULT 1" +
                    ");");
            s.close();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error creating database tables", e);
        }
    }

    /**
     * Mute a player.
     *
     * @param playerUUID  UUID of the muted player
     * @param playerName  Name of the muted player
     * @param staffUUID   UUID of the staff member
     * @param staffName   Name of the staff member
     * @param reason      Reason for the mute
     * @param duration    Duration of the mute in milliseconds (-1 for permanent)
     * @return true if successful, false otherwise
     */
    public boolean mutePlayer(UUID playerUUID, String playerName, UUID staffUUID, String staffName, String reason, long duration) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;

        // Deactivate old active mutes for this player
        deactivatePastMutes(playerUUID);

        String sql = "INSERT INTO mutes(player_uuid, player_name, staff_uuid, staff_name, reason, start_time, end_time, active) VALUES(?,?,?,?,?,?,?,1)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, playerName);
            ps.setString(3, staffUUID.toString());
            ps.setString(4, staffName);
            ps.setString(5, reason);
            ps.setLong(6, startTime);
            ps.setLong(7, endTime);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error muting player", ex);
            return false;
        }
    }

    /**
     * Ban a player by UUID.
     *
     * @return true if successful, false otherwise
     */
    public boolean banPlayer(UUID playerUUID, String playerName, UUID staffUUID, String staffName, String reason, long duration) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;

        deactivatePastBans(playerUUID);

        String sql = "INSERT INTO bans(player_uuid, player_name, staff_uuid, staff_name, reason, start_time, end_time, type, active) VALUES(?,?,?,?,?,?,?,'BAN',1)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ps.setString(2, playerName);
            ps.setString(3, staffUUID.toString());
            ps.setString(4, staffName);
            ps.setString(5, reason);
            ps.setLong(6, startTime);
            ps.setLong(7, endTime);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error banning player", ex);
            return false;
        }
    }

    /**
     * Ban a player by IP.
     *
     * @return true if successful, false otherwise
     */
    public boolean ipBanPlayer(String ipAddress, UUID staffUUID, String staffName, String reason, long duration, UUID playerUUID, String playerName) {
        long startTime = System.currentTimeMillis();
        long endTime = (duration == -1) ? -1 : startTime + duration;

        deactivatePastIpBans(ipAddress);

        String sql = "INSERT INTO bans(ip_address, staff_uuid, staff_name, reason, start_time, end_time, type, active, player_uuid, player_name) VALUES(?,?,?,?,?,?,'IPBAN',1,?,?)";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            ps.setString(2, staffUUID.toString());
            ps.setString(3, staffName);
            ps.setString(4, reason);
            ps.setLong(5, startTime);
            ps.setLong(6, endTime);
            ps.setString(7, playerUUID != null ? playerUUID.toString() : null);
            ps.setString(8, playerName);
            ps.executeUpdate();
            return true;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error IP banning player", ex);
            return false;
        }
    }

    /**
     * Gets active mute info for a player.
     *
     * @param playerUUID UUID of the player
     * @return Map containing mute details, or null if not actively muted.
     */
    public Map<String, Object> getActiveMuteInfo(UUID playerUUID) {
        String sql = "SELECT * FROM mutes WHERE player_uuid = ? AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long endTime = rs.getLong("end_time");
                if (endTime != -1 && System.currentTimeMillis() > endTime) {
                    // Mute expired, deactivate it
                    deactivateMute(rs.getInt("id"));
                    return null;
                }

                Map<String, Object> info = new HashMap<>();
                info.put("staff_name", rs.getString("staff_name"));
                info.put("reason", rs.getString("reason"));
                info.put("end_time", endTime);
                info.put("start_time", rs.getLong("start_time"));
                return info;
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching mute info", ex);
        }
        return null;
    }

    /**
     * Gets active ban info for a player (UUID based).
     *
     * @param playerUUID UUID of the player
     * @return Map containing ban details, or null if not actively banned.
     */
    public Map<String, Object> getActiveBanInfo(UUID playerUUID) {
        String sql = "SELECT * FROM bans WHERE player_uuid = ? AND type = 'BAN' AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long endTime = rs.getLong("end_time");
                if (endTime != -1 && System.currentTimeMillis() > endTime) {
                    // Ban expired
                    deactivateBan(rs.getInt("id"));
                    return null;
                }
                return createBanInfoMap(rs);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching ban info", ex);
        }
        return null;
    }

    /**
     * Gets active IP ban info for an IP address.
     *
     * @param ipAddress IP address
     * @return Map containing ban details, or null if not actively IP banned.
     */
    public Map<String, Object> getActiveIpBanInfo(String ipAddress) {
        String sql = "SELECT * FROM bans WHERE ip_address = ? AND type = 'IPBAN' AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long endTime = rs.getLong("end_time");
                if (endTime != -1 && System.currentTimeMillis() > endTime) {
                    // Ban expired
                    deactivateBan(rs.getInt("id"));
                    return null;
                }
                return createBanInfoMap(rs);
            }
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error fetching IP ban info", ex);
        }
        return null;
    }

    private Map<String, Object> createBanInfoMap(ResultSet rs) throws SQLException {
        Map<String, Object> info = new HashMap<>();
        info.put("staff_name", rs.getString("staff_name"));
        info.put("reason", rs.getString("reason"));
        info.put("end_time", rs.getLong("end_time"));
        info.put("start_time", rs.getLong("start_time"));
        info.put("type", rs.getString("type"));
        info.put("player_name", rs.getString("player_name")); // Name associated with ban
        return info;
    }

    /**
     * Unmutes a player by deactivating all their active mutes.
     *
     * @param playerUUID UUID of the player
     * @return true if an active mute was found and deactivated, false otherwise
     */
    public boolean unmutePlayer(UUID playerUUID) {
        return deactivatePastMutes(playerUUID);
    }

    /**
     * Unbans a player by deactivating all their active UUID-based bans.
     *
     * @param playerUUID UUID of the player
     * @return true if an active ban was found and deactivated, false otherwise
     */
    public boolean unbanPlayer(UUID playerUUID) {
        return deactivatePastBans(playerUUID);
    }

    /**
     * Unbans an IP by deactivating all active bans for that IP.
     *
     * @param ipAddress IP address
     * @return true if an active IP ban was found and deactivated, false otherwise
     */
    public boolean unbanIp(String ipAddress) {
        return deactivatePastIpBans(ipAddress);
    }

    // --- Helper methods to deactivate punishments ---

    private void deactivateMute(int muteId) {
        String sql = "UPDATE mutes SET active = 0 WHERE id = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setInt(1, muteId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deactivating mute", ex);
        }
    }

    private boolean deactivatePastMutes(UUID playerUUID) {
        String sql = "UPDATE mutes SET active = 0 WHERE player_uuid = ? AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deactivating past mutes", ex);
            return false;
        }
    }

    private void deactivateBan(int banId) {
        String sql = "UPDATE bans SET active = 0 WHERE id = ?";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setInt(1, banId);
            ps.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deactivating ban", ex);
        }
    }

    private boolean deactivatePastBans(UUID playerUUID) {
        String sql = "UPDATE bans SET active = 0 WHERE player_uuid = ? AND type = 'BAN' AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, playerUUID.toString());
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deactivating past bans", ex);
            return false;
        }
    }

    private boolean deactivatePastIpBans(String ipAddress) {
        String sql = "UPDATE bans SET active = 0 WHERE ip_address = ? AND type = 'IPBAN' AND active = 1";
        try (PreparedStatement ps = getSQLConnection().prepareStatement(sql)) {
            ps.setString(1, ipAddress);
            int rowsAffected = ps.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException ex) {
            plugin.getLogger().log(Level.SEVERE, "Error deactivating past IP bans", ex);
            return false;
        }
    }

    /**
     * Closes the database connection.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Error closing database connection", e);
        }
    }
}
