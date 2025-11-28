package me.login.misc.tokens;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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
    private final TokenDatabase database;
    private final TokenLogger logger;
    private final LuckPerms luckPerms;
    private final ItemManager itemManager;
    private final MiniMessage mm;
    private final Component prefix;

    public TokenManager(Login plugin, TokenDatabase database, TokenLogger logger, LuckPerms luckPerms, ItemManager itemManager) {
        this.plugin = plugin;
        this.database = database;
        this.logger = logger;
        this.luckPerms = luckPerms;
        this.itemManager = itemManager;
        this.mm = MiniMessage.miniMessage();

        // Only load 'server_prefix' to prevent double prefixes.
        // Ignored 'server_prefix_2' intentionally to fix duplication issues.
        String p1Raw = plugin.getConfig().getString("server_prefix");
        if (p1Raw == null) {
            p1Raw = "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b> <dark_gray>•</dark_gray>";
        }

        // Append a single space after the prefix
        this.prefix = parseMixedContent(p1Raw).append(Component.text(" "));
    }

    private Component parseMixedContent(String input) {
        if (input == null || input.isEmpty()) return Component.empty();
        if (input.contains("&") || input.contains("§")) {
            return LegacyComponentSerializer.legacyAmpersand().deserialize(input);
        }
        try {
            return mm.deserialize(input);
        } catch (Exception e) {
            return Component.text(input);
        }
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

    public TokenDatabase getDatabase() { return database; }

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

    public void purchaseItem(Player player, String itemKey, long cost, TokenShopGUI guiToUpdate) {
        database.removeTokens(player.getUniqueId(), cost).thenAccept(success -> {
            if (success) {
                ItemStack item = itemManager.getItem(itemKey);
                if (item == null) {
                    sendMsg(player, "<red>Error: The item '"+itemKey+"' is not configured in items.yml! Refunding tokens.</red>");
                    database.addTokens(player.getUniqueId(), (int) cost);

                    // FIX: Updated to match the 4-argument signature of logAdmin
                    logger.logAdmin("System", "Refund (Item Missing: " + itemKey + ")", player.getName(), cost);
                    return;
                }

                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    player.getInventory().addItem(item);
                    if (guiToUpdate != null) {
                        guiToUpdate.updateBalanceItem(player);
                    }
                });

                Component displayName = item.displayName().decoration(TextDecoration.ITALIC, false);
                sendMsg(player, Component.text("You purchased ", NamedTextColor.GREEN)
                        .append(displayName.colorIfAbsent(NamedTextColor.WHITE))
                        .append(mm.deserialize(" for <gold>" + cost + " ☆</gold>!</green>")));

                String logMsg = "`" + player.getName() + "` purchased `" + itemKey + "` for `" + cost + "` tokens.";
                logger.logShop(logMsg);

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

            logger.logAdmin(sender.getName(), "Added", targetName, amount);

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

                    logger.logAdmin(sender.getName(), "Removed", targetName, amount);

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
                    sendMsg(sender, "<red>You cannot modify the token balance of a player with a higher rank.</red>");
                    return;
                }
                database.setTokens(targetUUID, amount);
                sendMsg(sender, "<green>Set <white>" + targetName + "</white>'s token balance to <white>" + amount + "</white>.</green>");

                logger.logAdmin(sender.getName(), "Set", targetName, amount);

                Player targetPlayer = Bukkit.getPlayer(targetUUID);
                if (targetPlayer != null && targetPlayer.isOnline()) {
                    sendMsg(targetPlayer, "<gray>An admin set your token balance to <gold>" + amount + " ☆</gold>.</gray>");
                }
            });
        });
    }

    private CompletableFuture<Boolean> canModify(CommandSender sender, UUID targetUUID) {
        if (luckPerms == null) return CompletableFuture.completedFuture(false);
        if (!(sender instanceof Player)) return CompletableFuture.completedFuture(true);
        Player senderPlayer = (Player) sender;
        return luckPerms.getUserManager().loadUser(senderPlayer.getUniqueId())
                .thenCombine(luckPerms.getUserManager().loadUser(targetUUID), (senderUser, targetUser) -> {
                    int senderWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(senderUser.getPrimaryGroup())).map(g -> g.getWeight().orElse(0)).orElse(0);
                    int targetWeight = Optional.ofNullable(luckPerms.getGroupManager().getGroup(targetUser.getPrimaryGroup())).map(g -> g.getWeight().orElse(0)).orElse(0);
                    return senderWeight >= targetWeight;
                }).exceptionally(e -> false);
    }

    private CompletableFuture<UUID> getTargetUUID(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target != null) return target.getUniqueId();
            OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
            return off.hasPlayedBefore() ? off.getUniqueId() : null;
        }, runnable -> plugin.getServer().getScheduler().runTaskAsynchronously(plugin, runnable));
    }

    private void sendMsg(CommandSender sender, String message) {
        sender.sendMessage(prefix.append(mm.deserialize(message)));
    }

    private void sendMsg(CommandSender sender, Component message) {
        sender.sendMessage(prefix.append(message));
    }
}