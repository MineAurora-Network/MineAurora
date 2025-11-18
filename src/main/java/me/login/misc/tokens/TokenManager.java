package me.login.misc.tokens;
import me.login.Login;
import me.login.misc.dailyreward.DailyRewardDatabase;
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
    private final DailyRewardDatabase database;
    private final TokenLogger logger;
    private final LuckPerms luckPerms;
    private final ItemManager itemManager;
    private final MiniMessage mm;
    private final Component prefix;
    public TokenManager(Login plugin, DailyRewardDatabase database, TokenLogger logger, LuckPerms luckPerms, ItemManager itemManager) {
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

    // --- THIS IS THE FIX ---
    /**
     * Adds tokens to a player's balance. (Non-admin version for system rewards)
     * @param uuid The player's UUID.
     * @param amount The positive amount to add.
     */
    public void addTokens(UUID uuid, int amount) {
        if (amount <= 0) return;
        database.addTokens(uuid, amount);

        // Optional: Send message to player if they are online
        Player targetPlayer = Bukkit.getPlayer(uuid);
        if (targetPlayer != null && targetPlayer.isOnline()) {
            sendMsg(targetPlayer, "<gray>You received <gold>" + amount + " ☆</gold>.</gray>");
        }
    }
    // --- END NEW METHOD ---

    public void purchaseItem(Player player, String itemKey, long cost) {
        database.removeTokens(player.getUniqueId(), cost).thenAccept(success -> {
            if (success) {
                // Get item from ItemManager
                ItemStack item = itemManager.getItem(itemKey);
                if (item == null) {
                    sendMsg(player, "<red>Error: The item '"+itemKey+"' is not configured in items.yml! Refunding tokens.</red>");
                    database.addTokens(player.getUniqueId(), (int) cost); // Refund
                    logger.logAdmin("`" + player.getName() + "` failed to buy item `"+itemKey+"` (missing in items.yml). Refunded `" + cost + "` tokens.");
                    return;
                }
                // Give item to player
                player.getInventory().addItem(item);
                // Feedback
                Component displayName = item.displayName().decoration(TextDecoration.ITALIC, false);
                sendMsg(player, Component.text("You purchased ", NamedTextColor.GREEN)
                        .append(displayName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(mm.deserialize(" for <gold>" + cost + " ☆</gold>!</green>")));
                player.closeInventory();
                // Log purchase
                logger.logShop("`" + player.getName() + "` purchased `" + itemKey + "` for `" + cost + "` tokens.");
            } else {
                // Insufficient funds
                sendMsg(player, "<red>You do not have enough tokens to purchase this!</red>");
            }
        });
    }
    // --- Admin Commands ---
    public void addTokens(CommandSender sender, String targetName, long amount) {
        getTargetUUID(targetName).thenAccept(uuid -> {
            if (uuid == null) {
                sendMsg(sender, "<red>Player '" + targetName + "' not found.</red>");
                return;
            }
            database.addTokens(uuid, (int) amount); // DB method expects int, but logic is fine
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
            // Perform rank hierarchy check
            canModify(sender, targetUUID).thenAccept(canModify -> {
                if (!canModify) {
                    sendMsg(sender, "<red>You cannot modify the token balance of a player with an equal or higher rank.</red>");
                    return;
                }
                // Proceed with setting tokens
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
    /**
     * Checks if a command sender can modify a target's balance based on LuckPerms rank weight.
     */
    private CompletableFuture<Boolean> canModify(CommandSender sender, UUID targetUUID) {
        if (luckPerms == null) {
            return CompletableFuture.completedFuture(false); // Fail safe if LP not found
        }
        // Console can always modify
        if (!(sender instanceof Player)) {
            return CompletableFuture.completedFuture(true);
        }
        Player senderPlayer = (Player) sender;
        if (senderPlayer.getUniqueId().equals(targetUUID) && !senderPlayer.isOp()) {
            // Allow setting self
            return CompletableFuture.completedFuture(true);
        }
        // Load both users from LuckPerms
        CompletableFuture<User> senderUserFuture = luckPerms.getUserManager().loadUser(senderPlayer.getUniqueId());
        CompletableFuture<User> targetUserFuture = luckPerms.getUserManager().loadUser(targetUUID);
        return senderUserFuture.thenCombine(targetUserFuture, (senderUser, targetUser) -> {
            // Get primary group weights
            String senderGroup = senderUser.getPrimaryGroup();
            String targetGroup = targetUser.getPrimaryGroup();
            int senderWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(senderGroup))
                    .map(group -> group.getWeight().orElse(0)) // Get OptionalInt, unwrap to 0 if empty
                    .orElse(0); // If group was null, default to 0
            int targetWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(targetGroup))
                    .map(group -> group.getWeight().orElse(0))
                    .orElse(0);
            // Sender must have a strictly higher weight
            return senderWeight > targetWeight;
        }).exceptionally(ex -> {
            plugin.getLogger().warning("Failed to perform LuckPerms rank check: " + ex.getMessage());
            return false; // Fail safe
        });
    }
    /**
     * Gets a player's UUID from their name. Supports offline players.
     */
    private CompletableFuture<UUID> getTargetUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null) {
                return target.getUniqueId();
            }
            // Player is offline, look them up
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            if (offlinePlayer.hasPlayedBefore()) {
                return offlinePlayer.getUniqueId();
            }
            return null; // Player not found
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }
    private void sendMsg(CommandSender sender, String message) {
        sender.sendMessage(prefix.append(mm.deserialize(message)));
    }
    private void sendMsg(CommandSender sender, Component message) {
        sender.sendMessage(prefix.append(message));
    }
}