package me.login.discord.linking; // <-- CHANGED

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // <-- REMOVED
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// --- NEW KYORI IMPORTS ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
// --- END NEW IMPORTS ---

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscordLinkCmd implements CommandExecutor {

    private final Login plugin;
    private final DiscordLinking discordLinking;
    private final DiscordLinkDatabase database;

    // --- NEW KYORI FIELD ---
    private final Component prefix;

    // --- CONSTRUCTOR UPDATED ---
    public DiscordLinkCmd(Login plugin, DiscordLinkingModule module) {
        this.plugin = plugin;
        this.discordLinking = module.getDiscordLinking();
        this.database = module.getDiscordLinkDatabase();

        // --- ADDED PREFIX INITIALIZATION ---
        String prefixStr = plugin.getConfig().getString("server_prefix");
        if (prefixStr == null || prefixStr.isEmpty()) {
            prefixStr = plugin.getConfig().getString("server_prefix_2", "&cError: Prefix not found. ");
        }

        if (prefixStr.contains("<")) {
            this.prefix = MiniMessage.miniMessage().deserialize(prefixStr);
        } else {
            this.prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixStr);
        }
        // --- END PREFIX ---
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();
        switch (cmdName) {
            case "discord": return handleDiscordCommand(sender, args);
            case "unlink": return handleUnlinkCommand(sender);
            case "adminunlink": return handleAdminUnlinkCommand(sender, args);
            default: return false;
        }
    }

    private boolean handleDiscordCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cPlayer only command."));
            return true;
        }
        if (discordLinking.getJDA() == null) {
            player.sendMessage(prefix.append(Component.text("Discord bot offline.", NamedTextColor.RED)));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("link")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                if (database.isLinked(player.getUniqueId())) {
                    player.sendMessage(prefix.append(Component.text("You are already linked!", NamedTextColor.RED)));
                } else if (discordLinking.hasActiveCode(player.getUniqueId())) {
                    player.sendMessage(prefix.append(Component.text("Linking code already exists.", NamedTextColor.RED)));
                } else {
                    String code = discordLinking.generateCode(player.getUniqueId(), player.getName());
                    long channelId = plugin.getConfig().getLong("verification-channel-id");
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        player.sendMessage(prefix.append(Component.text("You linking code is ", NamedTextColor.GREEN))
                                .append(Component.text(code, NamedTextColor.YELLOW)));
                        player.sendMessage(prefix.append(Component.text("Enter this code in ", NamedTextColor.GRAY))
                                .append(Component.text("#Sync ", NamedTextColor.WHITE)) // You can change #Sync to your channel name
                                .append(Component.text("discord channel to verify your account.", NamedTextColor.GRAY)));
                    });
                }
            });
            return true;
        }
        sender.sendMessage(prefix.append(Component.text("Usage: /discord link", NamedTextColor.YELLOW)));
        return true;
    }

    private boolean handleUnlinkCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cPlayer only command."));
            return true;
        }
        UUID uuid = player.getUniqueId();
        Long discordId = discordLinking.getLinkedDiscordId(uuid);
        if (discordId == null) {
            player.sendMessage(prefix.append(Component.text("You are not linked.", NamedTextColor.RED)));
            return true;
        }
        performUnlink(discordId, player.getName(), sender, false);
        return true;
    }

    private boolean handleAdminUnlinkCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("discord.unlink.admin")) {
            sender.sendMessage(prefix.append(Component.text("You don't have permission to execute this command!", NamedTextColor.RED)));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(prefix.append(Component.text("Usage: /adminunlink <player>", NamedTextColor.RED)));
            return true;
        }
        String targetName = args[0];
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (targetPlayer == null || !targetPlayer.hasPlayedBefore()) {
                sender.sendMessage(prefix.append(Component.text("Player '" + targetName + "' not found.", NamedTextColor.RED)));
                return;
            }
            UUID uuid = targetPlayer.getUniqueId();
            Long discordId = discordLinking.getLinkedDiscordId(uuid);
            if (discordId == null) {
                sender.sendMessage(prefix.append(Component.text("Player is not linked.", NamedTextColor.RED)));
                return;
            }
            plugin.getServer().getScheduler().runTask(plugin, () -> performUnlink(discordId, targetPlayer.getName(), sender, true));
        });
        return true;
    }


    private void performUnlink(long discordId, String targetName, CommandSender sender, boolean isAdmin) {
        if (discordLinking.getJDA() == null) {
            sender.sendMessage(prefix.append(Component.text("Discord bot offline.", NamedTextColor.RED)));
            return;
        }

        discordLinking.unlinkUser(discordId); // Handles DB (async) and cache

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicBoolean dmAttempted = new AtomicBoolean(false);

        discordLinking.getJDA().retrieveUserById(discordId).queue(user -> { // Success user retrieval
            int guildCount = discordLinking.getJDA().getGuilds().size();
            AtomicInteger guildsProcessed = new AtomicInteger(0);

            if (guildCount == 0) { // Handle case where bot might be in 0 guilds somehow
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " from database. Failed to update in discord as Bot is not in server. Report this to any senior staff.", NamedTextColor.GREEN)));
                    discordLinking.getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Bot not in any guilds, Discord ID: `" + discordId + "`)");
                });
                return;
            }

            discordLinking.getJDA().getGuilds().forEach(guild -> {
                guild.retrieveMemberById(discordId).submit()
                        .whenComplete((member, memberError) -> {
                            if (member != null) { // Member found
                                plugin.getLogger().info("[Unlink] Found " + user.getName() + " in " + guild.getName() + ".");
                                try {
                                    long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
                                    long verifiedRoleId = plugin.getConfig().getLong("verified-role-id");
                                    Role unverifiedRole = guild.getRoleById(unverifiedRoleId);
                                    Role verifiedRole = guild.getRoleById(verifiedRoleId);

                                    if (verifiedRole != null) guild.removeRoleFromMember(member, verifiedRole).queue(s -> successCount.incrementAndGet(), e -> {});
                                    if (unverifiedRole != null) guild.addRoleToMember(member, unverifiedRole).queue(s -> successCount.incrementAndGet(), e -> {});

                                    member.modifyNickname(user.getName()).timeout(10, TimeUnit.SECONDS).queue(s -> successCount.incrementAndGet(), e -> plugin.getLogger().warning("[Unlink] Failed reset nick (" + guild.getName() + "): " + e.getMessage()));

                                    if (!isAdmin && dmAttempted.compareAndSet(false, true)) {
                                        EmbedBuilder eb = new EmbedBuilder().setColor(Color.ORANGE).setTitle("Account Unlinked")
                                                .setDescription("Unlinked from Minecraft account: **" + targetName + "**.")
                                                .addField("Status", "Roles/nickname reset.", false).setFooter("Use /discord link in-game to re-link.");
                                        user.openPrivateChannel().flatMap(channel -> channel.sendMessageEmbeds(eb.build()))
                                                .queue( s -> { successCount.incrementAndGet(); plugin.getLogger().info("[Unlink] Sent DM embed to " + user.getName()); },
                                                        e -> plugin.getLogger().warning("[Unlink] Failed send DM embed to " + user.getName() + ": " + e.getMessage()));
                                    }
                                } catch (Exception e) {
                                    plugin.getLogger().warning("[Unlink] Error during actions for " + user.getName() + " in " + guild.getName() + ": " + e.getMessage());
                                }
                            } else { // Member retrieval failed
                                Throwable cause = memberError.getCause();
                                if (!(cause instanceof ErrorResponseException ere && ere.getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER)) {
                                    plugin.getLogger().warning("[Unlink] Failed retrieve member " + discordId + " from " + guild.getName() + ": " + memberError.getMessage());
                                } // else: Unknown member is expected, ignore logging noise
                            }

                            // Check if last guild processed
                            if (guildsProcessed.incrementAndGet() >= guildCount) {
                                plugin.getServer().getScheduler().runTaskLater(plugin, () -> { // Final message/log after delay
                                    if (successCount.get() > 0) {
                                        sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " successfully.", NamedTextColor.GREEN)));
                                    } else {
                                        sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB. No Discord actions (user not found?).", NamedTextColor.GREEN)));
                                    }
                                    // Ensure log uses user.getAsTag() if user object is valid
                                    discordLinking.getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord: " + user.getAsTag() + " ID: `" + discordId + "`)");
                                }, 20L); // 1 sec delay
                            }
                        }); // End whenComplete
            }); // End forEach guild

        }, failure -> { // Failure user retrieval
            plugin.getLogger().warning("[Unlink] Failed retrieve User ID " + discordId + ".");
            sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB, but couldn't find Discord user.", NamedTextColor.GREEN)));
            discordLinking.getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord User Not Found: ID `" + discordId + "`)");
        });
    }
}