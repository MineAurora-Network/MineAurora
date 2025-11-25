package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;

public class TokenManager {
    private final Login plugin;
    private final TokenDatabase database; // CHANGED
    private final TokenLogger logger;
    private final LuckPerms luckPerms;
    private final ItemManager itemManager;
    private final MiniMessage mm;
    private final Component prefix;

    // CHANGED: Constructor now accepts TokenDatabase
    public TokenManager(Login plugin, TokenDatabase database, TokenLogger logger, LuckPerms luckPerms, ItemManager itemManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.luckPerms = luckPerms;
        this.itemManager = itemManager;
        this.mm = MiniMessage.miniMessage();
        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        this.prefix = mm.deserialize(prefixString + " ");
    }

    public MiniMessage getMiniMessage() {
        return mm;
    }

    public Component getPrefix() {
        return prefix;
    }

    public ItemManager getItemManager() {
        return itemManager;
    }

    public TokenLogger getLogger() {
        return logger;
    }

    public CompletableFuture<Long> getTokenBalance(UUID uuid) {
        return database.getTokenBalance(uuid);
    }

    public void addTokens(UUID uuid, int amount) {
        if (amount <= 0) return;
        database.addTokens(uuid, amount);

        Player targetPlayer = Bukkit.getPlayer(uuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            sendMsg(targetPlayer, "<gray>You received <gold>" + amount + " ☆</gold>.</gray>");
        }
    }

    public void purchaseItem(Player player, String itemKey, long cost) {
        database.removeTokens(player.getUniqueId(), cost).thenAccept(success -> {
            if (success) {
                ItemStack item = itemManager.getItem(itemKey);
                if (item == null) {
                    sendMsg(player, "<red>Error: The item '"+itemKey+"' is not configured in items.yml! Refunding tokens.</red>");
                    database.addTokens(player.getUniqueId(), (int) cost);
                    logger.logAdmin("`" + player.getName() + "` failed to buy item `"+itemKey+"` (missing in items.yml). Refunded `" + cost + "` tokens.");
                    return;
                }
                player.getInventory().addItem(item);
                Component displayName = item.displayName().decoration(TextDecoration.ITALIC, false);
                sendMsg(player, Component.text("You purchased ", NamedTextColor.GREEN)
                        .append(displayName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(mm.deserialize(" for <gold>" + cost + " ☆</gold>!</green>")));
                player.closeInventory();
                logger.logShop("`" + player.getName() + "` purchased `" + itemKey + "` for `" + cost + "` tokens.");
            } else {
                sendMsg(player, "<red>You do not have enough tokens to purchase this!</red>");
            }
        });
    }

    public void addTokens(CommandSender sender, String targetName, long amount) {
        getTargetUUID(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                sendMsg(sender, "<red>Player '" + targetName + "' not found.</red>");
                return;
            }
            database.addTokens(uuid, (int) amount);
            sendMsg(sender, "<green>Added <white>" + amount + "</white> tokens to <white>" + targetName + "</white>.</green>");
            logger.logAdmin("`" + sender.getName() + "` added `" + amount + "` tokens to `" + targetName + "`.");
            Player targetPlayer = Bukkit.getPlayer(uuid);
            if (targetPlayer != null && targetPlayer.isOnline()) {
                sendMsg(targetPlayer, "<gray>An admin added <gold>" + amount + " ☆</gold> to your balance.</gray>");
            }
        });
    }

    public void removeTokens(CommandSender sender, String targetName, long amount) {
        getTargetUUID(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                sendMsg(sender, "<red>Player '" + targetName + "' not found.</red>");
                return;
            }
            database.removeTokens(uuid, amount).thenAccept(success -> {
                if (success) {
                    sendMsg(sender, "<green>Removed <white>" + amount + "</white> tokens from <white>" + targetName + "</white>.</green>");
                    logger.logAdmin("`" + sender.getName() + "` removed `" + amount + "` tokens from `" + targetName + "`.");
                    Player targetPlayer = Bukkit.getPlayer(uuid);
                    if (targetPlayer != null && targetPlayer.isOnline()) {
                        sendMsg(targetPlayer, "<red>An admin removed <white>" + amount + "</white> tokens from your balance.</red>");
                    }
                } else {
                    sendMsg(sender, "<red>'" + targetName + "' does not have enough tokens to remove.</red>");
                }
            });
        });
    }

    public void setTokens(CommandSender sender, String targetName, long amount) {
        if (luckPerms == null) {
            sendMsg(sender, "<red>LuckPerms is not installed. Cannot perform rank hierarchy check.</red>");
            return;
        }
        getTargetUUID(targetName).thenAccept(targetUUID -> {
            if (targetUUID == null) {
                sendMsg(sender, "<red>Player '" + targetName + "' not found.</red>");
                return;
            }
            canModify(sender, targetUUID).thenAccept(canModify -> {
                if (!canModify) {
                    sendMsg(sender, "<red>You cannot modify the token balance of a player with an equal or higher rank.</red>");
                    return;
                }
                database.setTokens(targetUUID, amount);
                sendMsg(sender, "<green>Set <white>" + targetName + "</white>'s token balance to <white>" + amount + "</white>.</green>");
                logger.logAdmin("`" + sender.getName() + "` set `" + targetName + "`'s token balance to `" + amount + "`.");
                Player targetPlayer = Bukkit.getPlayer(targetUUID);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    sendMsg(targetPlayer, "<gray>An admin set your token balance to <gold>" + amount + " ☆</gold>.</gray>");
                }
            });
        });
    }

    private CompletableFuture<Boolean> canModify(CommandSender sender, UUID targetUUID) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(false);
        }
        if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture(true);
        }
        Player senderPlayer = (Player) sender;
        if (senderPlayer.getUniqueId().equals(targetUUID) && !senderPlayer.isOp()) {
            return CompletableFuture.completedFuture(true);
        }
        CompletableFuture<User> senderUserFuture = luckPerms.getUserManager().loadUser(senderPlayer.getUniqueId());
        CompletableFuture<User> targetUserFuture = luckPerms.getUserManager().loadUser(targetUUID);
        return senderUserFuture.thenCombine(targetUserFuture, (senderUser, targetUser) -> {
            String senderGroup = senderUser.getPrimaryGroup();
            String targetGroup = targetUser.getPrimaryGroup();
            int senderWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(senderGroup))
                    .map(group -> group.getWeight().orElse(0))
                    .orElse(0);
            int targetWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(targetGroup))
                    .map(group -> group.getWeight().orElse(0))
                    .orElse(0);
            return senderWeight > targetWeight;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to perform LuckPerms rank check: " + ex.getMessage());
            return false;
        });
    }

    private CompletableFuture<UUID> getTargetUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null) {
                return target.getUniqueId();
            }
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                return offlinePlayer.getUniqueId();
            }
            return null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    private void sendMsg(CommandSender sender, String message) {
        sender.sendMessage(prefix.append(mm.deserialize(message)));
    }

    private void sendMsg(CommandSender sender, Component message) {
        sender.sendMessage(prefix.append(message));
    }
}