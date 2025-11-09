package me.login.discord.moderation; // <-- CHANGED

import me.login.Login;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DiscordModCommands extends ListenerAdapter {

    private final Login plugin;
    private final DiscordModConfig modConfig;
    private final DiscordCommandLogger logger;
    private final UUID CONSOLE_UUID = new UUID(0, 0); // For staff UUID

    public DiscordModCommands(Login plugin, DiscordModConfig modConfig, DiscordCommandLogger logger) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.logger = logger;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.replyEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("You do not have permission to use this command.").build()).setEphemeral(true).queue();
            return;
        }

        switch (event.getName()) {
            case "mccheck":
                handleCheckCommand(event);
                break;
            case "mcban":
                handleBanCommand(event, false);
                break;
            case "mcipban":
                handleIpBanCommand(event);
                break;
            case "mcunban":
                handleUnbanCommand(event, false);
                break;
            case "mcunbanip":
                handleUnbanCommand(event, true);
                break;
            case "mcmute":
                handleMuteCommand(event);
                break;
            case "mcunmute":
                handleUnmuteCommand(event);
                break;
        }
    }

    private void handleCheckCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        event.deferReply(true).queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            // This 'getDiscordLinking()' call will now work because Login.java is fixed
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked to any Minecraft account.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

            Map<String, Object> ban = plugin.getModerationDatabase().getActiveBanInfo(targetUUID);
            Map<String, Object> mute = plugin.getModerationDatabase().getActiveMuteInfo(targetUUID);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setAuthor("Punishment Check: " + playerName, null, targetUser.getEffectiveAvatarUrl())
                    .addField("User", targetUser.getAsMention() + " (`" + targetUser.getAsTag() + "`)", true)
                    .addField("Player", "`" + playerName + "` (`" + targetUUID + "`)", true);

            if (ban != null) {
                long endTime = (long) ban.get("end_time");
                boolean isPerm = endTime == -1;
                eb.addField("Ban Status", "Banned by `" + ban.get("staff_name") + "` for `" + ban.get("reason") + "`.\nExpires: " + (isPerm ? "Permanent" : Utils.formatDuration(endTime - System.currentTimeMillis())), false);
            } else {
                eb.addField("Ban Status", "Not banned.", false);
            }
            if (mute != null) {
                long endTime = (long) mute.get("end_time");
                boolean isPerm = endTime == -1;
                eb.addField("Mute Status", "Muted by `" + mute.get("staff_name") + "` for `" + mute.get("reason") + "`.\nExpires: " + (isPerm ? "Permanent" : Utils.formatDuration(endTime - System.currentTimeMillis())), false);
            } else {
                eb.addField("Mute Status", "Not muted.", false);
            }
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void handleBanCommand(SlashCommandInteractionEvent event, boolean ipBan) {
        if (ipBan) {
            handleIpBanCommand(event);
            return;
        }

        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason", "No reason provided.", OptionMapping::getAsString);
        String durationStr = event.getOption("duration", "perm", OptionMapping::getAsString);
        long duration = Utils.parseDuration(durationStr);

        event.deferReply(true).queue();

        if (targetUser.getIdLong() == event.getUser().getIdLong()) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("You cannot ban yourself.").build()).queue();
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked. Cannot ban.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();
            String staffName = event.getMember().getEffectiveName();
            String banType = "Ban";

            plugin.getModerationDatabase().banPlayer(targetUUID, playerName, CONSOLE_UUID, staffName, reason, duration);

            String durationText = (duration == -1) ? "Permanent" : Utils.formatDuration(duration);
            String logMessage = "🔨 **" + banType + "** | " + targetUser.getAsMention() + " (`" + playerName + "`) was banned by " + event.getUser().getAsMention() + " for `" + reason + "` (Duration: " + durationText + ")";

            logger.logStaff(logMessage);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle(banType + " Issued")
                    .setDescription("Banned " + targetUser.getAsMention() + " (`" + playerName + "`)")
                    .addField("Reason", reason, true)
                    .addField("Duration", durationText, true)
                    .setFooter("Banned by " + event.getUser().getAsTag());
            event.getHook().sendMessageEmbeds(eb.build()).queue();

            if (targetPlayer.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String kickReason = ChatColor.RED + "You have been banned.\n" +
                            ChatColor.WHITE + "Reason: " + ChatColor.YELLOW + reason + "\n" +
                            ChatColor.WHITE + "Expires: " + ChatColor.YELLOW + Utils.formatTimeLeft(duration == -1 ? -1 : System.currentTimeMillis() + duration);
                    targetPlayer.getPlayer().kickPlayer(kickReason);
                });
            }
        });
    }

    private void handleIpBanCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason", "No reason provided.", OptionMapping::getAsString);
        String durationStr = event.getOption("duration", "perm", OptionMapping::getAsString);
        long duration = Utils.parseDuration(durationStr);

        event.deferReply(true).queue();

        if (targetUser.getIdLong() == event.getUser().getIdLong()) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("You cannot ban yourself.").build()).queue();
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked. Cannot IP-Ban.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();
            String staffName = event.getMember().getEffectiveName();
            String banType = "IP-Ban";

            plugin.getModerationDatabase().banPlayer(targetUUID, playerName, CONSOLE_UUID, staffName, reason, duration);

            String durationText = (duration == -1) ? "Permanent" : Utils.formatDuration(duration);
            String logMessage = "🔨 **" + banType + "** | " + targetUser.getAsMention() + " (`" + playerName + "`) was banned by " + event.getUser().getAsMention() + " for `" + reason + "` (Duration: " + durationText + ")";

            logger.logStaff(logMessage);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setTitle(banType + " Issued")
                    .setDescription("IP-Banned " + targetUser.getAsMention() + " (`" + playerName + "`)")
                    .addField("Reason", reason, true)
                    .addField("Duration", durationText, true)
                    .setFooter("Banned by " + event.getUser().getAsTag());
            event.getHook().sendMessageEmbeds(eb.build()).queue();

            if (targetPlayer.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String kickReason = ChatColor.RED + "You have been IP-banned.\n" +
                            ChatColor.WHITE + "Reason: " + ChatColor.YELLOW + reason + "\n" +
                            ChatColor.WHITE + "Expires: " + ChatColor.YELLOW + Utils.formatTimeLeft(duration == -1 ? -1 : System.currentTimeMillis() + duration);
                    targetPlayer.getPlayer().kickPlayer(kickReason);
                });
            }
        });
    }


    private void handleUnbanCommand(SlashCommandInteractionEvent event, boolean ipUnban) {
        User targetUser = event.getOption("user").getAsUser();
        String unbanType = ipUnban ? "IP-Unban" : "Unban";

        event.deferReply(true).queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();
            boolean unbanned;

            unbanned = plugin.getModerationDatabase().unbanPlayer(targetUUID);

            if (unbanned) {
                String logMessage = "✅ **" + unbanType + "** | " + targetUser.getAsMention() + " (`" + playerName + "`) was unbanned by " + event.getUser().getAsMention();

                logger.logStaff(logMessage);

                EmbedBuilder eb = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle(unbanType + " Successful")
                        .setDescription("Unbanned " + targetUser.getAsMention() + " (`" + playerName + "`)")
                        .setFooter("Unbanned by " + event.getUser().getAsTag());
                event.getHook().sendMessageEmbeds(eb.build()).queue();
            } else {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("`" + playerName + "` was not " + (ipUnban ? "ip-banned" : "banned") + ".").build()).queue();
            }
        });
    }

    private void handleMuteCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        String reason = event.getOption("reason", "No reason provided.", OptionMapping::getAsString);
        String durationStr = event.getOption("duration", "perm", OptionMapping::getAsString);
        long duration = Utils.parseDuration(durationStr);

        event.deferReply(true).queue();

        if (targetUser.getIdLong() == event.getUser().getIdLong()) {
            event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("You cannot mute yourself.").build()).queue();
            return;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked. Cannot mute.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();
            String staffName = event.getMember().getEffectiveName();

            plugin.getModerationDatabase().mutePlayer(targetUUID, playerName, CONSOLE_UUID, staffName, reason, duration);

            String durationText = (duration == -1) ? "Permanent" : Utils.formatDuration(duration);
            String logMessage = "🔇 **Mute** | " + targetUser.getAsMention() + " (`" + playerName + "`) was muted by " + event.getUser().getAsMention() + " for `" + reason + "` (Duration: " + durationText + ")";

            logger.logStaff(logMessage);

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.ORANGE)
                    .setTitle("Mute Issued")
                    .setDescription("Muted " + targetUser.getAsMention() + " (`" + playerName + "`)")
                    .addField("Reason", reason, true)
                    .addField("Duration", durationText, true)
                    .setFooter("Muted by " + event.getUser().getAsTag());
            event.getHook().sendMessageEmbeds(eb.build()).queue();

            if (targetPlayer.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    String muteReason = ChatColor.RED + "You have been muted.\n" +
                            ChatColor.WHITE + "Reason: " + ChatColor.YELLOW + reason + "\n" +
                            ChatColor.WHITE + "Expires: " + ChatColor.YELLOW + Utils.formatTimeLeft(duration == -1 ? -1 : System.currentTimeMillis() + duration);
                    targetPlayer.getPlayer().sendMessage(muteReason);
                });
            }
        });
    }

    private void handleUnmuteCommand(SlashCommandInteractionEvent event) {
        User targetUser = event.getOption("user").getAsUser();
        event.deferReply(true).queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetUUID = plugin.getDiscordLinking().getLinkedUuid(targetUser.getIdLong());
            if (targetUUID == null) {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("User " + targetUser.getAsMention() + " is not linked.").build()).queue();
                return;
            }

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUUID);
            String playerName = targetPlayer.getName() != null ? targetPlayer.getName() : targetUUID.toString();

            boolean unmuted = plugin.getModerationDatabase().unmutePlayer(targetUUID);

            if (unmuted) {
                String logMessage = "🔊 **Unmute** | " + targetUser.getAsMention() + " (`" + playerName + "`) was unmuted by " + event.getUser().getAsMention();

                logger.logStaff(logMessage);

                EmbedBuilder eb = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("Unmute Successful")
                        .setDescription("Unmuted " + targetUser.getAsMention() + " (`" + playerName + "`)")
                        .setFooter("Unmuted by " + event.getUser().getAsTag());
                event.getHook().sendMessageEmbeds(eb.build()).queue();

                if (targetPlayer.isOnline()) {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        targetPlayer.getPlayer().sendMessage(ChatColor.GREEN + "You have been unmuted by " + event.getMember().getEffectiveName() + ".");
                    });
                }
            } else {
                event.getHook().sendMessageEmbeds(new EmbedBuilder().setColor(Color.RED).setDescription("`" + playerName + "` was not muted.").build()).queue();
            }
        });
    }
}