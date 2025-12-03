package me.login.discord.moderation.discord;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.misc.rank.RankModule;
import me.login.misc.rank.util.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.luckperms.api.model.user.UserManager;
import org.bukkit.Bukkit;

import java.awt.Color;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class DiscordStaffModCommands extends ListenerAdapter {

    private final Login plugin;
    private final DiscordModConfig modConfig;
    private final DiscordModDatabase modDatabase;
    private final DiscordCommandLogger logger;
    private final DiscordLinking linking;
    private final RankModule rankModule;

    public DiscordStaffModCommands(Login plugin, DiscordModConfig modConfig, DiscordModDatabase modDatabase, DiscordCommandLogger logger, DiscordLinking linking, RankModule rankModule) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.modDatabase = modDatabase;
        this.logger = logger;
        this.linking = linking;
        this.rankModule = rankModule;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmd = event.getName();

        if (cmd.equals("ban")) handleDiscordBan(event);
        else if (cmd.equals("unban")) handleDiscordUnban(event);
        else if (cmd.equals("timeout") || cmd.equals("mute")) handleDiscordMute(event);
        else if (cmd.equals("removetimeout") || cmd.equals("unmute")) handleRemoveTimeout(event);
        else if (cmd.equals("warn")) handleWarn(event);
        else if (cmd.equals("unwarn") || cmd.equals("removewarn")) handleUnwarn(event);
        else if (cmd.equals("history") || cmd.equals("mutehistory")) handleHistory(event);
    }

    private void sendDM(User user, MessageEmbed embed) {
        user.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed).queue(null, e -> {}),
                e -> {}
        );
    }

    // Generates the "Moderator: name • time" string with dynamic timezone
    private String getModLine(User staff) {
        long seconds = System.currentTimeMillis() / 1000;
        return "**Moderator:** " + staff.getName() + " • <t:" + seconds + ":f>";
    }

    private boolean checkLinkedPermission(SlashCommandInteractionEvent event, String permissionNode) {
        UUID staffUuid = linking.getLinkedUuid(event.getUser().getIdLong());
        if (staffUuid == null) {
            event.reply("You must link your Minecraft account to use this command. `/discord link`").setEphemeral(true).queue();
            return false;
        }

        UserManager userManager = Bukkit.getServicesManager().getRegistration(net.luckperms.api.LuckPerms.class).getProvider().getUserManager();
        try {
            net.luckperms.api.model.user.User user = userManager.loadUser(staffUuid).join();
            if (user == null) {
                event.reply("Could not load your permission data.").setEphemeral(true).queue();
                return false;
            }
            boolean hasPerm = user.getCachedData().getPermissionData().checkPermission(permissionNode).asBoolean();
            if (!hasPerm) {
                event.reply("You do not have the in-game permission: `" + permissionNode + "`").setEphemeral(true).queue();
                return false;
            }
            return true;
        } catch (Exception e) {
            event.reply("Error checking permissions.").setEphemeral(true).queue();
            return false;
        }
    }

    private void handleWarn(SlashCommandInteractionEvent event) {
        if (!checkLinkedPermission(event, "discord.staff.command.warn")) return;

        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        String staffName = event.getUser().getName();

        // 1. Add Warning
        modDatabase.addWarning(target.getIdLong(), event.getUser().getIdLong(), staffName, reason);

        // 2. Count
        int totalWarns = modDatabase.getActiveWarningCount(target.getIdLong());
        int maxWarns = modConfig.getMaxWarnings();
        boolean punishmentTriggered = false;

        // 3. Auto-Timeout Logic (3 warns)
        if (totalWarns >= maxWarns) {
            punishmentTriggered = true;
            modDatabase.clearWarnings(target.getIdLong()); // Reset warnings

            Member member = event.getGuild().getMember(target);
            if (member != null) {
                if (!member.isOwner() && !member.hasPermission(Permission.ADMINISTRATOR)) {
                    long duration = TimeUnit.HOURS.toMillis(3);
                    member.timeoutFor(duration, TimeUnit.MILLISECONDS).reason("Exceeded max warnings (3/3)").queue();

                    // Log the timeout
                    modDatabase.logPunishment(target.getIdLong(), event.getUser().getIdLong(), "System", "TIMEOUT", "Max Warnings Reached", "3h");
                }
            }
            // Temporarily set count to max for display purposes before reset logic fully takes over visually
            totalWarns = maxWarns;
        }

        // 4. Build Embed
        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("You've been warned");
        embed.setColor(Color.decode("#2F3136")); // Dark background

        StringBuilder desc = new StringBuilder();
        desc.append("Reason: **").append(reason).append("**\n");
        desc.append("The total number of warns: **").append(totalWarns).append("/").append(maxWarns).append("**\n\n");

        if (punishmentTriggered) {
            desc.append("This was the last warning. For multiple violations, you were given a mute for 3 hours. The old warnings have been removed.\n\n");
        }

        desc.append(getModLine(event.getUser()));
        embed.setDescription(desc.toString());

        // Send to User
        sendDM(target, embed.build());

        // Send to Staff (Same embed, just confirms action)
        event.replyEmbeds(embed.build()).queue();

        logger.logStaff("[Discord-Warn] **" + staffName + "** warned **" + target.getAsTag() + "**: " + reason);
    }

    private void handleUnwarn(SlashCommandInteractionEvent event) {
        if (!checkLinkedPermission(event, "discord.staff.command.removewarn")) return;

        User target = event.getOption("user").getAsUser();
        Integer id = event.getOption("id", OptionMapping::getAsInt);

        boolean success = modDatabase.removeWarning(target.getIdLong(), id);
        if (success) {
            int totalWarns = modDatabase.getActiveWarningCount(target.getIdLong());
            int maxWarns = modConfig.getMaxWarnings();

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("You've been unwarned");
            embed.setColor(Color.decode("#2F3136"));

            String desc = "The total number of warns: **" + totalWarns + "/" + maxWarns + "**\n\n" + getModLine(event.getUser());
            embed.setDescription(desc);

            sendDM(target, embed.build());

            // Staff Reply
            EmbedBuilder staffEmbed = new EmbedBuilder(embed);
            staffEmbed.setTitle("Unwarned " + target.getName());
            staffEmbed.setDescription("Removed warning ID #" + id + "\n" + desc);
            event.replyEmbeds(staffEmbed.build()).queue();

            logger.logStaff("[Discord-Unwarn] **" + event.getUser().getAsTag() + "** removed warning #" + id + " from **" + target.getAsTag() + "**");
        } else {
            event.reply("Warning ID #" + id + " not found or already inactive.").setEphemeral(true).queue();
        }
    }

    private void handleHistory(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS) && !checkLinkedPermission(event, "discord.staff.command.history")) {
            event.reply("No permission.").setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        List<String> warns = modDatabase.getActiveWarnings(target.getIdLong());
        List<String> punishments = modDatabase.getPunishmentHistory(target.getIdLong());
        int maxWarns = modConfig.getMaxWarnings();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(Color.ORANGE); // Matches the yellow bar in screenshot
        eb.setAuthor("History for " + target.getAsTag(), null, target.getEffectiveAvatarUrl());

        // Active Warnings Section
        StringBuilder warnBody = new StringBuilder();
        if (warns.isEmpty()) {
            warnBody.append("*No active warnings.*");
        } else {
            for (String w : warns) warnBody.append(w).append("\n");
        }
        eb.addField("Active Warnings (" + warns.size() + "/" + maxWarns + ")", warnBody.toString(), false);

        // Recent Punishments Section
        StringBuilder punishBody = new StringBuilder();
        if (punishments.isEmpty()) {
            punishBody.append("*No punishment history.*");
        } else {
            for (String p : punishments) punishBody.append(p).append("\n");
        }
        eb.addField("Recent Punishments", punishBody.toString(), false);

        event.replyEmbeds(eb.build()).queue();
    }

    // --- Standard Moderation ---

    private boolean verifyStaffLink(SlashCommandInteractionEvent event) {
        if (linking.getLinkedUuid(event.getUser().getIdLong()) == null) {
            event.reply("You must link your Minecraft account to use moderation commands. `/discord link`").setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private void handleDiscordBan(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You lack the BAN_MEMBERS permission.").setEphemeral(true).queue();
            return;
        }
        if (!verifyStaffLink(event)) return;

        User target = event.getOption("user").getAsUser();
        String reason = event.getOption("reason").getAsString();
        int days = event.getOption("days").getAsInt();

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("You've been banned");
        embed.setColor(Color.RED);
        embed.setDescription("Reason: **" + reason + "**\n\n" + getModLine(event.getUser()));

        sendDM(target, embed.build());

        event.getGuild().ban(target, days, TimeUnit.DAYS).reason(reason).queue(
                s -> {
                    modDatabase.logPunishment(target.getIdLong(), event.getUser().getIdLong(), event.getUser().getName(), "BAN", reason, "Permanent");

                    EmbedBuilder staffEmbed = new EmbedBuilder(embed);
                    staffEmbed.setTitle("Banned " + target.getName());
                    event.replyEmbeds(staffEmbed.build()).queue();

                    logger.logStaff("[Discord-Ban] **" + event.getUser().getAsTag() + "** banned **" + target.getAsTag() + "**");
                },
                e -> event.reply("Failed to ban: " + e.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleDiscordUnban(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.BAN_MEMBERS)) {
            event.reply("You lack the BAN_MEMBERS permission.").setEphemeral(true).queue();
            return;
        }
        if (!verifyStaffLink(event)) return;

        String userId = event.getOption("user_id").getAsString();
        event.getGuild().unban(User.fromId(userId)).queue(
                s -> {
                    modDatabase.logPunishment(Long.parseLong(userId), event.getUser().getIdLong(), event.getUser().getName(), "UNBAN", "N/A", "N/A");
                    event.reply("**Unbanned** ID: " + userId).queue();
                    logger.logStaff("[Discord-Unban] **" + event.getUser().getAsTag() + "** unbanned ID **" + userId + "**");
                },
                e -> event.reply("Failed to unban: " + e.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleDiscordMute(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("You lack the MODERATE_MEMBERS permission.").setEphemeral(true).queue();
            return;
        }
        if (!verifyStaffLink(event)) return;

        Member target = event.getOption("user").getAsMember();
        if (target == null) { event.reply("User is not in the guild.").setEphemeral(true).queue(); return; }

        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("I cannot timeout this user (they have a higher role).").setEphemeral(true).queue();
            return;
        }

        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason").getAsString();

        long millis;
        try {
            millis = TimeUtil.parseDuration(durationStr);
            if (millis == -1) millis = TimeUnit.DAYS.toMillis(28);
        } catch (Exception e) {
            event.reply("Invalid duration format (10m, 1h).").setEphemeral(true).queue();
            return;
        }

        target.timeoutFor(millis, TimeUnit.MILLISECONDS).reason(reason).queue(
                s -> {
                    modDatabase.logPunishment(target.getIdLong(), event.getUser().getIdLong(), event.getUser().getName(), "TIMEOUT", reason, durationStr);

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Hello! 👋 You're muted");
                    embed.setColor(Color.decode("#2F3136"));
                    embed.setDescription("Time of punishment: **" + durationStr + "**\nReason: **" + reason + "**\n\n" + getModLine(event.getUser()));

                    sendDM(target.getUser(), embed.build());

                    // Staff Embed (Same content, Title changed)
                    EmbedBuilder staffEmbed = new EmbedBuilder(embed);
                    staffEmbed.setTitle("Muted " + target.getUser().getName());
                    event.replyEmbeds(staffEmbed.build()).queue();

                    logger.logStaff("[Discord-Mute] **" + event.getUser().getAsTag() + "** muted **" + target.getUser().getAsTag() + "** (" + durationStr + ")");
                },
                e -> event.reply("Failed to timeout: " + e.getMessage()).setEphemeral(true).queue()
        );
    }

    private void handleRemoveTimeout(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            event.reply("You lack the MODERATE_MEMBERS permission.").setEphemeral(true).queue();
            return;
        }
        if (!verifyStaffLink(event)) return;

        Member target = event.getOption("user").getAsMember();
        if (target == null) { event.reply("User is not in the guild.").setEphemeral(true).queue(); return; }

        // Logic for "Reason of revoking"
        OptionMapping reasonOpt = event.getOption("reason");
        String reason = (reasonOpt != null) ? reasonOpt.getAsString() : "The reason is not provided";

        target.removeTimeout().reason(reason).queue(
                s -> {
                    modDatabase.logPunishment(target.getIdLong(), event.getUser().getIdLong(), event.getUser().getName(), "UNMUTE", reason, "N/A");

                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setTitle("Hello again, you were unmuted");
                    embed.setColor(Color.decode("#2F3136"));
                    embed.setDescription("Reason of revoking the punishment: **" + reason + "**\n\n" + getModLine(event.getUser()));

                    sendDM(target.getUser(), embed.build());

                    // Staff Embed
                    EmbedBuilder staffEmbed = new EmbedBuilder(embed);
                    staffEmbed.setTitle("Unmuted " + target.getUser().getName());
                    event.replyEmbeds(staffEmbed.build()).queue();

                    logger.logStaff("[Discord-Unmute] **" + event.getUser().getAsTag() + "** unmuted **" + target.getUser().getAsTag() + "**");
                },
                e -> event.reply("Failed: " + e.getMessage()).setEphemeral(true).queue()
        );
    }
}