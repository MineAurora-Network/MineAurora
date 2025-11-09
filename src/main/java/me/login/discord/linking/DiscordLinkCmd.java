package me.login.discord.linking;

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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.jetbrains.annotations.NotNull;

import java.awt.Color;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public class DiscordLinkCmd implements CommandExecutor {

    private final Login plugin;
    private final DiscordLinkingModule module;
    private final Component prefix;

    public DiscordLinkCmd(Login plugin, DiscordLinkingModule module) {
        this.plugin = plugin;
        this.module = module;

        String prefixStr = plugin.getConfig().getString("server_prefix");
        if (prefixStr == null || prefixStr.isEmpty()) {
            prefixStr = plugin.getConfig().getString("server_prefix_2", "&cError: Prefix not found. ");
        }

        if (prefixStr.contains("<")) {
            this.prefix = MiniMessage.miniMessage().deserialize(prefixStr);
        } else {
            this.prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixStr);
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (label.equalsIgnoreCase("discord")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("&cThis command can only be used by players."));
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("link")) {
                handleLink(player);
            } else {
                String link = plugin.getConfig().getString("discord-server-link", "§cLink not set in config!");
                player.sendMessage(prefix.append(Component.text("Join our Discord: " + link, NamedTextColor.AQUA)));
            }
            return true;
        }

        if (label.equalsIgnoreCase("unlink")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("&cThis command can only be used by players."));
                return true;
            }
            handleUnlink(player, player.getUniqueId(), false);
            return true;
        }

        if (label.equalsIgnoreCase("adminunlink")) {
            if (!sender.hasPermission("login.admin.unlink")) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("&cYou do not have permission to use this command."));
                return true;
            }
            if (args.length < 1) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("&cUsage: /adminunlink <player>"));
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("&cPlayer not found."));
                return true;
            }
            handleUnlink(sender, target.getUniqueId(), true);
            return true;
        }
        return false;
    }

    private void handleLink(Player player) {
        if (module.getDiscordLinking() == null) {
            player.sendMessage(prefix.append(Component.text("Discord system is not enabled.", NamedTextColor.RED)));
            return;
        }
        if (module.getDiscordLinking().getLinkedDiscordId(player.getUniqueId()) != null) {
            player.sendMessage(prefix.append(Component.text("Your account is already linked.", NamedTextColor.RED)));
            return;
        }
        if (module.getDiscordLinking().hasActiveCode(player.getUniqueId())) {
            player.sendMessage(prefix.append(Component.text("You already have an active code. Please wait.", NamedTextColor.RED)));
            return;
        }

        String code = module.getDiscordLinking().generateCode(player.getUniqueId(), player.getName());
        long channelId = plugin.getConfig().getLong("verification-channel-id");
        long expiry = plugin.getConfig().getLong("code-expiry-seconds", 60);

        player.sendMessage(prefix.append(Component.text("Go to the ", NamedTextColor.GREEN)
                .append(Component.text("#verify", NamedTextColor.YELLOW))
                .append(Component.text(" channel (" + channelId + ") on our Discord and type:", NamedTextColor.GREEN))));
        player.sendMessage(Component.text(code, NamedTextColor.WHITE, net.kyori.adventure.text.format.TextDecoration.BOLD));
        player.sendMessage(prefix.append(Component.text("This code expires in " + expiry + " seconds.", NamedTextColor.GRAY)));
    }

    private void handleUnlink(CommandSender sender, UUID targetUUID, boolean isAdmin) {
        if (module.getDiscordLinking() == null || module.getDiscordLinkDatabase() == null) {
            sender.sendMessage(prefix.append(Component.text("Discord system is not enabled.", NamedTextColor.RED)));
            return;
        }

        Long discordId = module.getDiscordLinking().getLinkedDiscordId(targetUUID);
        if (discordId == null) {
            sender.sendMessage(prefix.append(Component.text((isAdmin ? "That player is" : "You are") + " not linked.", NamedTextColor.RED)));
            return;
        }

        OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
        String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();

        module.getDiscordLinking().unlinkUser(discordId);
        sender.sendMessage(prefix.append(Component.text("Unlinking... searching for Discord account...", NamedTextColor.GRAY)));

        module.getDiscordLinking().getJDA().retrieveUserById(discordId).queue(user -> {
            if (user == null) {
                sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB, but couldn't find Discord user to update roles.", NamedTextColor.GREEN)));
                module.getDiscordLinking().getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord User Not Found: ID `" + discordId + "`)");
                return;
            }

            long verifiedRoleId = plugin.getConfig().getLong("verified-role-id");
            long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
            Role verifiedRole = module.getDiscordLinking().getJDA().getRoleById(verifiedRoleId);
            Role unverifiedRole = module.getDiscordLinking().getJDA().getRoleById(unverifiedRoleId);

            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger processedCount = new AtomicInteger(0);
            int guildCount = module.getDiscordLinking().getJDA().getGuilds().size();

            if (guildCount == 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB. Bot is not in any guilds.", NamedTextColor.YELLOW)));
                    module.getDiscordLinking().getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord: " + user.getAsTag() + " ID: `" + discordId + "`)");
                });
                return;
            }

            module.getDiscordLinking().getJDA().getGuilds().forEach(guild -> {
                guild.retrieveMember(user).submit().whenComplete((member, throwable) -> {
                    if (member != null) {
                        try {
                            if (verifiedRole != null && member.getRoles().contains(verifiedRole)) {
                                guild.removeRoleFromMember(member, verifiedRole).queue(s -> successCount.incrementAndGet());
                            }
                            if (unverifiedRole != null && !member.getRoles().contains(unverifiedRole)) {
                                guild.addRoleToMember(member, unverifiedRole).queue(s -> successCount.incrementAndGet());
                            }
                            member.modifyNickname(null).queue();
                            plugin.getLogger().info("Successfully removed roles/nick for " + user.getAsTag() + " in " + guild.getName());
                        } catch (HierarchyException | InsufficientPermissionException e) {
                            plugin.getLogger().warning("Failed to update roles/nick for " + user.getAsTag() + ": " + e.getMessage());
                        }

                        EmbedBuilder dmEmbed = new EmbedBuilder()
                                .setColor(Color.ORANGE)
                                .setTitle("Account Unlinked")
                                .setDescription("Your Discord account has been unlinked from the Minecraft account: **" + targetName + "**.")
                                .setFooter(guild.getName(), guild.getIconUrl());
                        module.getDiscordLinking().sendPrivateEmbed(user, dmEmbed.build());

                    } else if (throwable != null) {
                        if (throwable instanceof ErrorResponseException && ((ErrorResponseException) throwable).getErrorResponse() == ErrorResponse.UNKNOWN_MEMBER) {
                        } else {
                            plugin.getLogger().warning("Failed to retrieve member " + user.getAsTag() + " from guild " + guild.getName() + ": " + throwable.getMessage());
                        }
                    }

                    if (processedCount.incrementAndGet() == guildCount) {
                        plugin.getServer().getScheduler().runTask(plugin, () -> {
                            if (successCount.get() > 0) {
                                sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " successfully.", NamedTextColor.GREEN)));
                            } else {
                                sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB. No Discord actions (user not found?).", NamedTextColor.GREEN)));
                            }
                            module.getDiscordLinking().getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord: " + user.getAsTag() + " ID: `" + discordId + "`)");
                        });
                    }
                });
            });

        }, failure -> {
            plugin.getLogger().warning("[Unlink] Failed retrieve User ID " + discordId + ".");
            sender.sendMessage(prefix.append(Component.text("Unlinked " + targetName + " DB, but couldn't find Discord user.", NamedTextColor.GREEN)));
            module.getDiscordLinking().getLogger().sendLog("➖ " + (isAdmin ? "Admin (" + sender.getName() + ")" : "") + " Unlinked **" + targetName + "** (Discord User Not Found: ID `" + discordId + "`)");
        });
    }
}