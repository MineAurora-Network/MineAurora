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
    private final UUID CONSOLE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final Map<UUID, BukkitTask> tempRankTasks = new ConcurrentHashMap<>();

    public RankManager(Login plugin, RankDatabase database, RankLogger logger, Component serverPrefix) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.serverPrefix = serverPrefix;
    }

    public void init(LuckPerms luckPerms) {
        this.luckPerms = luckPerms;
    }

    public void setRank(CommandSender sender, User targetUser, Group rank, long durationMillis) {
        String setterName = sender.getName();
        UUID setterUuid = (sender instanceof Player) ? ((Player) sender).getUniqueId() : CONSOLE_UUID;

        if (!canModify(sender, targetUser)) {
            return;
        }

        setRank(setterName, setterUuid, targetUser, rank, durationMillis);

        sender.sendMessage(serverPrefix.append(mm.deserialize(
                "<green>You set <yellow>" + targetUser.getUsername() + "</yellow>'s rank to <aqua>" + rank.getName() + "</aqua> for <white>" + TimeUtil.formatDuration(durationMillis) + "</white>."
        )));
    }

    public void setRank(String setterName, UUID setterUuid, User targetUser, Group rank, long durationMillis) {
        String targetName = targetUser.getUsername() != null ? targetUser.getUsername() : "Unknown";
        String rankName = rank.getName();

        String previousRank = targetUser.getPrimaryGroup();
        if (previousRank == null || previousRank.isEmpty()) {
            previousRank = "default";
        }

        applyLuckPermsRank(targetUser, rankName);
        cancelTask(targetUser.getUniqueId());

        long expiryTime = (durationMillis == -1) ? -1 : System.currentTimeMillis() + durationMillis;

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
        database.addRankHistory(rankData, durationMillis);

        if (durationMillis != -1) {
            scheduleRankRemoval(targetUser.getUniqueId(), previousRank, durationMillis);
        }

        String timeString = TimeUtil.formatDuration(durationMillis);
        Player targetPlayer = Bukkit.getPlayer(targetUser.getUniqueId());
        if (targetPlayer != null) {
            targetPlayer.sendMessage(serverPrefix.append(mm.deserialize(
                    "<green>Your rank has been set to <aqua>" + rankName + "</aqua> for <white>" + timeString + "</white> by <yellow>" + setterName + "</yellow>."
            )));
        }

        logger.logRankSet(setterName, targetName, rankName, timeString);
    }

    public void removeRank(CommandSender sender, User targetUser) {
        String targetName = targetUser.getUsername();
        String setterName = sender.getName();

        if (!canModify(sender, targetUser)) {
            return;
        }

        cancelTask(targetUser.getUniqueId());
        database.removeRankData(targetUser.getUniqueId());
        applyLuckPermsRank(targetUser, "default");

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


    private void applyLuckPermsRank(User user, String rankName) {
        user.data().clear(node -> node instanceof InheritanceNode);
        Node newNode = InheritanceNode.builder(rankName).build();
        user.data().add(newNode);
        luckPerms.getUserManager().saveUser(user);
    }

    private void scheduleRankRemoval(UUID targetUuid, String rankToRestore, long durationMillis) {
        long durationTicks = durationMillis / 50;
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

    public void loadScheduledTasks() {
        plugin.getLogger().info("Loading and scheduling active temporary ranks...");
        int count = 0;
        for (RankData data : database.getActiveTempRanks()) {
            long remainingMillis = data.expiryTime() - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                expireRank(data.playerUuid(), data.previousRank());
            } else {
                scheduleRankRemoval(data.playerUuid(), data.previousRank(), remainingMillis);
                count++;
            }
        }
        plugin.getLogger().info("Rescheduled " + count + " temporary ranks.");
    }

    private void cancelTask(UUID uuid) {
        BukkitTask existingTask = tempRankTasks.remove(uuid);
        if (existingTask != null) {
            try {
                existingTask.cancel();
            } catch (Exception e) {
            }
        }
    }

    public void shutdown() {
        for (BukkitTask task : tempRankTasks.values()) {
            task.cancel();
        }
        tempRankTasks.clear();
    }

    public boolean canModify(CommandSender sender, User targetUser) {
        if (sender instanceof Player) {
            Player senderPlayer = (Player) sender;
            if (senderPlayer.getUniqueId().equals(targetUser.getUniqueId())) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You cannot manage your own rank.</red>")));
                return false;
            }

            User senderUser = luckPerms.getUserManager().getUser(senderPlayer.getUniqueId());
            if (senderUser == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not load your permission data.</red>")));
                return false;
            }

            int senderWeight = getWeight(senderUser.getPrimaryGroup());
            int targetWeight = getWeight(targetUser.getPrimaryGroup());

            if (senderWeight <= targetWeight) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You cannot modify the rank of a player with an equal or higher rank.</red>")));
                return false;
            }
        }
        return true;
    }

    public int getWeight(String groupName) {
        if (groupName == null) return 0;
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group != null) {
            return group.getWeight().orElse(0);
        }
        return 0;
    }

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