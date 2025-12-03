package me.login.discord.moderation;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public class DiscordCommandRegistrar {

    public static void register(JDA jda, Login plugin, DiscordCommandLogger logger) {
        if (jda == null) return;

        jda.addEventListener(new DiscordStaffCommands(plugin, logger));

        CommandListUpdateAction commands = jda.updateCommands();

        commands.addCommands(
                // --- MINECRAFT COMMANDS (Unchanged) ---
                Commands.slash("mccheck", "Check the ban/mute status of a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),
                Commands.slash("mcban", "Ban a player from Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 10m, 1h, 7d, perm).", false),
                Commands.slash("mcipban", "IP-Ban a player from Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the IP-ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration.", false),
                Commands.slash("mcunban", "Unban a player from Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),
                Commands.slash("mcunbanip", "Un-IP-Ban a player from Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),
                Commands.slash("mcmute", "Mute a player on Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the mute.", false)
                        .addOption(OptionType.STRING, "duration", "Duration.", false),
                Commands.slash("mcunmute", "Unmute a player on Minecraft.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),

                // --- DISCORD MODERATION COMMANDS ---
                Commands.slash("ban", "Ban a user from Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the ban.", true) // Required
                        .addOption(OptionType.INTEGER, "days", "Days of messages to delete (0-7).", true) // Required
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

                Commands.slash("unban", "Unban a user from Discord.")
                        .addOption(OptionType.STRING, "user_id", "The Discord User ID.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)),

                // /mute alias for timeout
                Commands.slash("mute", "Mute (Timeout) a user on Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g. 1h, 10m).", true) // Required
                        .addOption(OptionType.STRING, "reason", "Reason.", true) // Required
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

                // /timeout (Keep for compatibility)
                Commands.slash("timeout", "Timeout a user on Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g. 1h, 10m).", true) // Required
                        .addOption(OptionType.STRING, "reason", "Reason.", true) // Required
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

                Commands.slash("removetimeout", "Remove a timeout from a user.")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.STRING, "reason", "Reason for unmuting.", true) // Required
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)),

                Commands.slash("warn", "Warn a user (Requires linked MC account with perms).")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.STRING, "reason", "Reason for the warning.", true),

                // Renamed from removewarn to unwarn
                Commands.slash("unwarn", "Remove a warning (Requires linked MC account with perms).")
                        .addOption(OptionType.USER, "user", "The Discord user.", true)
                        .addOption(OptionType.INTEGER, "id", "The Warning ID to remove (see /history).", true), // Required

                Commands.slash("history", "View warnings for a user.")
                        .addOption(OptionType.USER, "user", "The Discord user.", true),

                // --- RANK ---
                Commands.slash("rank", "Manage player ranks.")
                        .addSubcommands(
                                new SubcommandData("set", "Set a player's rank.")
                                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User.", true)
                                        .addOption(OptionType.STRING, "rank", "The rank name.", true)
                                        .addOption(OptionType.STRING, "duration", "Duration.", true),
                                new SubcommandData("info", "Get info on a player's rank.")
                                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User.", true)
                        )
        );

        commands.queue();
    }
}