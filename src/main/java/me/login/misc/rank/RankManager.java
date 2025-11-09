package me.login.misc.rank;

import me.login.Login;
import me.login.misc.rank.util.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RankManager {

    private final Login plugin;
    private final RankDatabase database;
    private final RankLogger logger;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private LuckPerms luckPerms;

    // Map to track active temporary rank tasks
    private final Map<UUID, BukkitTask> tempRankTasks = new ConcurrentHashMap<>();

    public RankManager(Login plugin, RankDatabase database, RankLogger logger, Component serverPrefix) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.serverPrefix = serverPrefix;
    }

    /**
     * Initializes the manager with the LuckPerms API.
     */
    public void init(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    /**
     * Sets a player's rank, potentially temporarily.
     */
    public void setRank(CommandSender sender, User targetUser, Group rank, long durationMillis) {
        String targetName = targetUser.getUsername() != null ? targetUser.getUsername() : "Unknown";
        String rankName = rank.getName();
        String setterName = sender.getName();
        UUID setterUuid = (sender instanceof Player) ? ((Player) sender).getUniqueId() : UUID.fromString("00000000-0000-0000-0000-000000000000"); // Console UUID

        // 1. Get previous rank
        String previousRank = targetUser.getPrimaryGroup();
        if (previousRank == null || previousRank.isEmpty()) {
            previousRank = "default";
        }

        // 2. Apply the new rank using LuckPerms
        applyLuckPermsRank(targetUser, rankName);

        // 3. Cancel any existing temp rank task
        cancelTask(targetUser.getUniqueId());

        long expiryTime = (durationMillis == -1) ? -1 : System.currentTimeMillis() + durationMillis;

        // 4. Save to database
        RankData rankData = new RankData(
                targetUser.getUniqueId(),
                targetName,
                rankName,
                setterUuid,
                setterName,
                previousRank,
                expiryTime
        );
        database.saveRankData(rankData);
        database.addRankHistory(rankData, durationMillis); // Log to history

        // 5. Schedule removal if temporary
        if (durationMillis != -1) {
            scheduleRankRemoval(targetUser.getUniqueId(), previousRank, durationMillis);
        }

        // 6. Send confirmations and log
        String timeString = (durationMillis == -1) ? "permanent" : TimeUtil.formatDuration(durationMillis);
        sender.sendMessage(serverPrefix.append(mm.deserialize(
                "<green>You set <yellow>" + targetName + "</yellow>'s rank to <aqua>" + rankName + "</aqua> for <white>" + timeString + "</white>."
        )));

        Player targetPlayer = Bukkit.getPlayer(targetUser.getUniqueId());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(serverPrefix.append(mm.deserialize(
                    "<green>Your rank has been set to <aqua>" + rankName + "</aqua> for <white>" + timeString + "</white> by <yellow>" + setterName + "</yellow>."
            )));
        }

        logger.logRankSet(setterName, targetName, rankName, timeString);
    }

    /**
     * Removes a player's rank and sets them to default.
     */
    public void removeRank(CommandSender sender, User targetUser) {
        String targetName = targetUser.getUsername();
        String setterName = sender.getName();

        // 1. Cancel any active task
        cancelTask(targetUser.getUniqueId());

        // 2. Remove from database
        database.removeRankData(targetUser.getUniqueId());

        // 3. Set to default in LuckPerms
        applyLuckPermsRank(targetUser, "default");

        // 4. Send confirmations and log
        sender.sendMessage(serverPrefix.append(mm.deserialize(
                "<green>You removed <yellow>" + targetName + "</yellow>'s rank, setting them to default."
        )));

        Player targetPlayer = Bukkit.getPlayer(targetUser.getUniqueId());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(serverPrefix.append(mm.deserialize(
                    "<red>Your rank has been removed by <yellow>" + setterName + "</yellow>."
            )));
        }
        logger.logRankRemove(setterName, targetName, "default");
    }

    /**
     * Force-removes a rank (used by scheduled tasks).
     * @param targetUuid The player to demote.
     * @param rankToRestore The rank to give back (usually 'default').
     */
    public void expireRank(UUID targetUuid, String rankToRestore) {
        database.removeRankData(targetUuid);
        luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(user -> {
            if (user == null) return;

            applyLuckPermsRank(user, rankToRestore);
            String targetName = user.getUsername() != null ? user.getUsername() : targetUuid.toString();

            Player targetPlayer = Bukkit.getPlayer(targetUuid);
            if (targetPlayer != null) {
                targetPlayer.sendMessage(serverPrefix.append(mm.deserialize(
                        "<red>Your temporary rank has expired. Your rank has been set to <aqua>" + rankToRestore + "</aqua>."
                )));
            }
            logger.logRankRemove("Console (Expiry)", targetName, rankToRestore);
        });
    }


    /**
     * Applies a rank to a user via LuckPerms, clearing all other ranks.
     */
    private void applyLuckPermsRank(User user, String rankName) {
        // Clear all existing group nodes
        user.data().clear(node -> node instanceof InheritanceNode);
        // Add the new group node
        Node newNode = InheritanceNode.builder(rankName).build();
        user.data().add(newNode);
        // Save changes
        luckPerms.getUserManager().saveUser(user);
    }

    /**
     * Schedules a task to remove a player's rank after a duration.
     */
    private void scheduleRankRemoval(UUID targetUuid, String rankToRestore, long durationMillis) {
        long durationTicks = durationMillis / 50; // Convert ms to ticks
        if (durationTicks <= 0) return;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                expireRank(targetUuid, rankToRestore);
                tempRankTasks.remove(targetUuid);
            }
        }.runTaskLater(plugin, durationTicks);

        tempRankTasks.put(targetUuid, task);
    }

    /**
     * Loads all active temporary ranks from the DB on startup and schedules them.
     */
    public void loadScheduledTasks() {
        plugin.getLogger().info("Loading and scheduling active temporary ranks...");
        int count = 0;
        for (RankData data : database.getActiveTempRanks()) {
            long remainingMillis = data.expiryTime() - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                // Rank expired while server was off
                expireRank(data.playerUuid(), data.previousRank());
            } else {
                // Reschedule for the remaining time
                scheduleRankRemoval(data.playerUuid(), data.previousRank(), remainingMillis);
                count++;
            }
        }
        plugin.getLogger().info("Rescheduled " + count + " temporary ranks.");
    }

    /**
     * Cancels and removes an active temp rank task for a player.
     */
    private void cancelTask(UUID uuid) {
        BukkitTask existingTask = tempRankTasks.remove(uuid);
        if (existingTask != null) {
            try {
                existingTask.cancel();
            } catch (Exception e) {
                // Task might already be cancelled or finished, ignore.
            }
        }
    }

    public void shutdown() {
        for (BukkitTask task : tempRankTasks.values()) {
            task.cancel();
        }
        tempRankTasks.clear();
    }

    /**
     * Checks if a command sender has the authority to modify a target user's rank.
     * @param sender The admin/console performing the action.
     * @param targetUser The user being modified.
     * @return true if the sender can modify the target.
     */
    public boolean canModify(CommandSender sender, User targetUser) {
        if (sender instanceof Player) {
            Player senderPlayer = (Player) sender;
            // Rule 1: Can't target self
            if (senderPlayer.getUniqueId().equals(targetUser.getUniqueId())) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You cannot manage your own rank.</red>")));
                return false;
            }

            // Rule 2: Check hierarchy
            User senderUser = luckPerms.getUserManager().getUser(senderPlayer.getUniqueId());
            if (senderUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not load your permission data.</red>")));
                return false; // Safety check
            }

            int senderWeight = getWeight(senderUser.getPrimaryGroup());
            int targetWeight = getWeight(targetUser.getPrimaryGroup());

            if (senderWeight <= targetWeight) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You cannot modify the rank of a player with an equal or higher rank.</red>")));
                return false;
            }
        }
        // Console (sender is not Player) can modify anyone
        return true;
    }

    /**
     * Gets the weight of a LuckPerms group.
     * @param groupName The name of the group.
     * @return The weight, or 0 if not found.
     */
    public int getWeight(String groupName) {
        if (groupName == null) return 0;
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group != null) {
            return group.getWeight().orElse(0);
        }
        return 0;
    }

    /**
     * Gets formatted rank info for the /rank info command.
     */
    public Component getRankInfo(User targetUser) {
        String targetName = targetUser.getUsername();
        RankData data = database.getRankData(targetUser.getUniqueId());
        String currentRank = targetUser.getPrimaryGroup();

        if (data == null) {
            return serverPrefix.append(mm.deserialize(
                    "<white>" + targetName + "</white> has the rank <aqua>" + currentRank + "</aqua>." +
                            "%nl%<gray>This is not a temporary rank managed by this system.</gray>"
            ));
        }

        String setter = data.setterName();
        String expiryString;
        if (data.expiryTime() == -1) {
            expiryString = "<white>Permanent</white>";
        } else {
            long remaining = data.expiryTime() - System.currentTimeMillis();
            expiryString = (remaining > 0) ?
                    "<white>" + TimeUtil.formatDuration(remaining) + "</white> <gray>(Expires: " + Instant.ofEpochMilli(data.expiryTime()) + ")</gray>" :
                    "<red>Expired</red>";
        }

        return serverPrefix.append(mm.deserialize(
                "<gold>Rank Info for <white>" + targetName + "</white>:" +
                        "%nl%  <gray>Rank: <aqua>" + data.rankName() + "</aqua>" +
                        "%nl%  <gray>Set By: <yellow>" + setter + "</yellow>" +
                        "%nl%  <gray>Previous Rank: <aqua>" + data.previousRank() + "</aqua>" +
                        "%nl%  <gray>Expires In: " + expiryString
        ));
    }
}