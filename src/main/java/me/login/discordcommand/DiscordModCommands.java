package me.login.discordcommand;

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.exceptions.HierarchyException;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordModCommands extends ListenerAdapter {

    private final Login plugin;
    private final DiscordModConfig modConfig;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("MMM dd, yyyY 'at' HH:mm z");
    private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)([smhd])");

    public DiscordModCommands(Login plugin, DiscordModConfig modConfig) {
        this.plugin = plugin;
        this.modConfig = modConfig;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String commandName = event.getName();
        // Run async to avoid blocking JDA
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                switch (commandName) {
                    case "warn":
                        handleWarn(event);
                        break;
                    case "removewarn":
                        handleRemoveWarn(event);
                        break;
                    case "timeout":
                        handleTimeout(event);
                        break;
                    case "removetimeout":
                        handleRemoveTimeout(event);
                        break;
                    case "ban":
                        handleBan(event);
                        break;
                    case "unban":
                        handleUnban(event);
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing Discord moderation command: " + e.getMessage());
                e.printStackTrace();
                String reply = "An internal error occurred. Please check the server console.";
                if (event.isAcknowledged()) {
                    event.getHook().sendMessage(reply).setEphemeral(true).queue();
                } else {
                    event.reply(reply).setEphemeral(true).queue();
                }
            }
        });
    }

    // --- Command Handlers ---

    private void handleWarn(SlashCommandInteractionEvent event) {
        Member target = event.getOption("user").getAsMember();
        String reason = event.getOption("reason").getAsString();
        Member staff = event.getMember();

        // --- PERMISSION & HIERARCHY CHECKS FIRST ---
        if (!checkPerms(event, Permission.MODERATE_MEMBERS)) return;
        if (!checkHierarchy(event, staff, target, true)) return;
        // --- END CHECKS ---

        event.deferReply().queue(); // Now it's safe to defer

        String date = dateFormat.format(new Date());
        String warnMessage = String.format("`%s` - by **%s** on %s", reason, staff.getEffectiveName(), date);

        modConfig.addWarning(target.getIdLong(), warnMessage);
        List<String> warnings = modConfig.getWarnings(target.getIdLong());
        int maxWarnings = modConfig.getMaxWarnings();
        int warnCount = warnings.size();

        EmbedBuilder dm = new EmbedBuilder()
                .setColor(Color.ORANGE)
                .setAuthor("You have been warned in " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                .addField("Reason", reason, false)
                .addField("Moderator", staff.getAsMention(), false)
                .addField("Current Warnings", String.format("%d / %d", warnCount, maxWarnings), false)
                .setFooter(date);

        String logMessage;

        if (warnCount >= maxWarnings) {
            dm.setColor(Color.RED)
                    .setTitle("This was your FINAL warning.")
                    .setDescription("\nThis was the last warning. For multiple violations, you were given a mute for 3 hours. The old warnings have been removed.");

            try {
                target.timeoutFor(3, TimeUnit.HOURS).reason("Reached max warnings").queue();
                logMessage = String.format("Warned %s (Warning %d/%d). User has reached max warnings and has been timed out for 3 hours.",
                        target.getAsMention(), warnCount, maxWarnings);
                event.getHook().sendMessage(logMessage).queue();

                modConfig.clearWarnings(target.getIdLong());

            } catch (HierarchyException e) {
                logMessage = "Warned " + target.getAsMention() + ", but I could not apply the timeout. My roles are too low.";
                event.getHook().sendMessage(logMessage).queue();
            }
        } else {
            logMessage = String.format("Warned %s. They now have %d / %d warnings.",
                    target.getAsMention(), warnCount, maxWarnings);
            event.getHook().sendMessage(logMessage).queue();
        }

        sendPrivateEmbed(target.getUser(), dm);
        plugin.sendDiscordStaffLog(String.format("`%s` warned `%s` (now %d/%d). Reason: %s",
                staff.getEffectiveName(), target.getEffectiveName(), warnCount, maxWarnings, reason));
    }

    private void handleRemoveWarn(SlashCommandInteractionEvent event) {
        Member target = event.getOption("user").getAsMember();

        if (!checkPerms(event, Permission.MODERATE_MEMBERS)) return;
        if (target == null) {
            event.reply("Error: You must specify a member in the server.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();
        String removedWarning = modConfig.removeWarning(target.getIdLong());

        if (removedWarning == null) {
            event.getHook().sendMessage(target.getAsMention() + " has no warnings to remove.").queue();
            return;
        }

        int warnCount = modConfig.getWarnings(target.getIdLong()).size();

        EmbedBuilder dm = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setAuthor("A warning was removed in " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                .setDescription("Your most recent warning has been removed by a moderator.")
                .addField("Removed Warning", removedWarning.split("- by")[0], false) // Show only the reason
                .addField("Current Warnings", String.format("%d / %d", warnCount, modConfig.getMaxWarnings()), false);

        sendPrivateEmbed(target.getUser(), dm);
        event.getHook().sendMessage(String.format("Removed one warning from %s. They now have %d warnings.",
                target.getAsMention(), warnCount)).queue();

        plugin.sendDiscordStaffLog(String.format("`%s` removed one warning from `%s` (now %d).",
                event.getMember().getEffectiveName(), target.getEffectiveName(), warnCount));
    }

    private void handleTimeout(SlashCommandInteractionEvent event) {
        Member target = event.getOption("user").getAsMember();
        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason").getAsString();
        Member staff = event.getMember();

        if (!checkPerms(event, Permission.MODERATE_MEMBERS)) return;
        if (!checkHierarchy(event, staff, target, true)) return;

        long durationMillis = parseSimpleDuration(durationStr);
        if (durationMillis == 0) {
            event.reply("Invalid duration format. Use s, m, h, d (e.g., `30m`, `2h`, `1d`).").setEphemeral(true).queue();
            return;
        }
        if (durationMillis > TimeUnit.DAYS.toMillis(28)) {
            event.reply("Duration cannot be longer than 28 days.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue();

        try {
            Duration duration = Duration.ofMillis(durationMillis);
            target.timeoutFor(duration).reason(reason).queue();

            String date = dateFormat.format(new Date());
            EmbedBuilder dm = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setAuthor("You have been timed out in " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                    .addField("Duration", durationStr, false)
                    .addField("Reason", reason, false)
                    .addField("Moderator", staff.getAsMention(), false)
                    .setFooter(date);

            sendPrivateEmbed(target.getUser(), dm);
            event.getHook().sendMessage(String.format("Timed out %s for %s. Reason: %s",
                    target.getAsMention(), durationStr, reason)).queue();

            plugin.sendDiscordStaffLog(String.format("`%s` timed out `%s` for %s. Reason: %s",
                    staff.getEffectiveName(), target.getEffectiveName(), durationStr, reason));

        } catch (HierarchyException e) {
            event.getHook().sendMessage("Error: Cannot timeout that user. My roles are too low.").queue();
        }
    }

    private void handleRemoveTimeout(SlashCommandInteractionEvent event) {
        Member target = event.getOption("user").getAsMember();
        Member staff = event.getMember();

        if (!checkPerms(event, Permission.MODERATE_MEMBERS)) return;
        if (!checkHierarchy(event, staff, target, true)) return;

        event.deferReply().queue();

        try {
            if (!target.isTimedOut()) {
                event.getHook().sendMessage(target.getAsMention() + " is not currently timed out.").queue();
                return;
            }
            target.removeTimeout().reason("Timeout removed by " + staff.getEffectiveName()).queue();

            EmbedBuilder dm = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setAuthor("Your timeout has been removed in " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                    .setDescription("You are now able to chat again.")
                    .addField("Moderator", staff.getAsMention(), false);

            sendPrivateEmbed(target.getUser(), dm);
            event.getHook().sendMessage("Removed timeout for " + target.getAsMention()).queue();

            plugin.sendDiscordStaffLog(String.format("`%s` removed timeout for `%s`.",
                    staff.getEffectiveName(), target.getEffectiveName()));

        } catch (HierarchyException e) {
            event.getHook().sendMessage("Error: Cannot remove timeout. My roles are too low.").queue();
        }
    }

    private void handleBan(SlashCommandInteractionEvent event) {
        User target = event.getOption("user").getAsUser();
        String durationStr = event.getOption("remove_messages").getAsString();
        String reason = event.getOption("reason").getAsString();
        Member staff = event.getMember();

        if (!checkPerms(event, Permission.BAN_MEMBERS)) return;
        Member targetMember = event.getOption("user").getAsMember(); // Can be null if user isn't in server
        if (!checkHierarchy(event, staff, targetMember, false)) return; // 'false' because target can be null

        event.deferReply().queue();

        long durationMillis = parseSimpleDuration(durationStr);
        int deletionDays = (int) TimeUnit.MILLISECONDS.toDays(durationMillis);
        deletionDays = Math.max(0, Math.min(7, deletionDays));
        TimeUnit deletionUnit = TimeUnit.DAYS;

        try {
            EmbedBuilder dm = new EmbedBuilder()
                    .setColor(Color.RED)
                    .setAuthor("You have been banned from " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                    .addField("Reason", reason, false)
                    .addField("Moderator", staff.getAsMention(), false)
                    .setFooter(dateFormat.format(new Date()));

            sendPrivateEmbed(target, dm);

            event.getGuild().ban(target, deletionDays, deletionUnit)
                    .reason(String.format("Banned by %s. Reason: %s", staff.getEffectiveName(), reason))
                    .queue();

            event.getHook().sendMessage(String.format("Banned %s and deleted their messages from the last %d days. Reason: %s",
                    target.getAsMention(), deletionDays, reason)).queue();

            plugin.sendDiscordStaffLog(String.format("`%s` banned `%s` (deleted %d days). Reason: %s",
                    staff.getEffectiveName(), target.getAsTag(), deletionDays, reason));

        } catch (HierarchyException e) {
            event.getHook().sendMessage("Error: Cannot ban that user. My roles are too low.").queue();
        }
    }

    private void handleUnban(SlashCommandInteractionEvent event) {
        User target = event.getOption("user").getAsUser();
        Member staff = event.getMember();

        if (!checkPerms(event, Permission.BAN_MEMBERS)) return;

        event.deferReply().queue();

        event.getGuild().unban(target)
                .reason("Unbanned by " + staff.getEffectiveName())
                .queue(
                        success -> {
                            EmbedBuilder dm = new EmbedBuilder()
                                    .setColor(Color.GREEN)
                                    .setAuthor("You have been unbanned from " + event.getGuild().getName(), null, event.getGuild().getIconUrl())
                                    .setDescription("You are now able to rejoin the server.");
                            sendPrivateEmbed(target, dm);

                            event.getHook().sendMessage("Unbanned " + target.getAsMention()).queue();

                            plugin.sendDiscordStaffLog(String.format("`%s` unbanned `%s`.",
                                    staff.getEffectiveName(), target.getAsTag()));
                        },
                        failure -> {
                            event.getHook().sendMessage("Could not unban " + target.getAsMention() + ". Are you sure they are banned?").queue();
                        }
                );
    }

    // --- Helper Methods (MODIFIED) ---

    private boolean checkPerms(SlashCommandInteractionEvent event, Permission perm) {
        if (!event.getMember().hasPermission(perm)) {
            event.reply("You do not have the required Discord permission (`" + perm.getName() + "`) to use this command.")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private boolean checkHierarchy(SlashCommandInteractionEvent event, Member staff, Member target, boolean targetRequiredInServer) {
        if (target == null) {
            if (targetRequiredInServer) {
                event.reply("Error: You must specify a member currently in the server.").setEphemeral(true).queue();
                return false;
            }
            return true; // Target isn't in server (e.g., for /ban), so hierarchy check is skipped
        }
        if (staff.equals(target)) {
            event.reply("Error: You cannot run this command on yourself.").setEphemeral(true).queue();
            return false;
        }
        if (!staff.canInteract(target)) {
            event.reply("Error: You cannot moderate this user. Their roles are higher than or equal to yours.")
                    .setEphemeral(true).queue();
            return false;
        }
        if (!event.getGuild().getSelfMember().canInteract(target)) {
            event.reply("Error: I cannot moderate this user. My roles are lower than theirs.")
                    .setEphemeral(true).queue();
            return false;
        }
        return true;
    }

    private long parseSimpleDuration(String durationStr) {
        Matcher matcher = DURATION_PATTERN.matcher(durationStr.toLowerCase());
        if (matcher.matches()) {
            long value = Long.parseLong(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "s": return TimeUnit.SECONDS.toMillis(value);
                case "m": return TimeUnit.MINUTES.toMillis(value);
                case "h": return TimeUnit.HOURS.toMillis(value);
                case "d": return TimeUnit.DAYS.toMillis(value);
            }
        }
        return 0; // Invalid format
    }

    private void sendPrivateEmbed(User user, EmbedBuilder embed) {
        user.openPrivateChannel().queue(
                channel -> channel.sendMessageEmbeds(embed.build()).queue(),
                error -> plugin.getLogger().warning("Failed to send DM to " + user.getAsTag() + ": " + error.getMessage())
        );
    }
}