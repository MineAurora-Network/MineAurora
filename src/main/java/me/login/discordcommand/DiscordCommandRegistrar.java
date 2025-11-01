package me.login.discordcommand;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission; // <-- NEW IMPORT
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions; // <-- NEW IMPORT
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

public class DiscordCommandRegistrar {

    public static void register(JDA jda) {
        jda.updateCommands().addCommands(
                // --- Normal Commands (Visible to all) ---
                Commands.slash("staff", "Shows a list of all online staff members."),

                Commands.slash("balance", "Checks the in-game balance of a player.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true),

                Commands.slash("profile", "Shows the in-game profile of a player.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true),

                Commands.slash("leaderboard", "Shows the server leaderboards.")
                        .addOptions(
                                new OptionData(OptionType.STRING, "name", "The leaderboard category to view.", true)
                                        .addChoice("Top Kills", "kills")
                                        .addChoice("Top Deaths", "deaths")
                                        .addChoice("Top Playtime", "playtime")
                                        .addChoice("Top Balance", "balance")
                                        .addChoice("Top Credits", "credits")
                                        .addChoice("Top Lifesteal Level", "lifesteal")
                                        .addChoice("Top Mob Kills", "mobkills")
                        ),

                // --- Minecraft Staff Commands (Hidden) ---
                Commands.slash("mcmute", "Mutes a player in-game.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 3d, perm).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the mute.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("mcunmute", "Unmutes a player in-game.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("mcmuteinfo", "Checks a player's active in-game mute status.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("mcban", "Bans a player in-game.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 3d, perm).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the ban.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)), // <-- HIDDEN

                Commands.slash("mcunban", "Unbans a player in-game.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)), // <-- HIDDEN

                Commands.slash("mcbaninfo", "Checks a player's active in-game ban status.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)), // <-- HIDDEN

                // --- NEW Discord Staff Commands (Hidden) ---
                Commands.slash("warn", "Warns a Discord member.")
                        .addOption(OptionType.USER, "user", "The user to warn.", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the warning.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("removewarn", "Removes the last warning from a Discord member.")
                        .addOption(OptionType.USER, "user", "The user to remove a warning from.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("timeout", "Times out a Discord member.")
                        .addOption(OptionType.USER, "user", "The user to time out.", true)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 30m, 2h, 1d).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the timeout.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("removetimeout", "Removes a timeout from a Discord member.")
                        .addOption(OptionType.USER, "user", "The user to remove the timeout from.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)), // <-- HIDDEN

                Commands.slash("ban", "Bans a user from the Discord server.")
                        .addOption(OptionType.USER, "user", "The user to ban.", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the ban.", true)
                        .addOptions(
                                new OptionData(OptionType.STRING, "remove_messages", "How much of their message history to delete.", true)
                                        .addChoice("Don't delete any", "0d")
                                        .addChoice("Last 24 hours", "1d")
                                        .addChoice("Last 3 days", "3d")
                                        .addChoice("Last 7 days", "7d")
                        )
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)), // <-- HIDDEN

                Commands.slash("unban", "Unbans a user from the Discord server.")
                        .addOption(OptionType.USER, "user", "The user to unban (by ID or tag).", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.BAN_MEMBERS)), // <-- HIDDEN

                // --- NEW Admin Command (Hidden) ---
                Commands.slash("rank", "Sets a player's in-game rank via Discord.")
                        .addOption(OptionType.STRING, "player", "The Minecraft name of the player.", true)
                        .addOption(OptionType.STRING, "rank", "The name of the LuckPerms rank.", true)
                        .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)) // <-- HIDDEN

        ).queue(
                success -> System.out.println("[Login Plugin] Successfully registered " + success.size() + " Discord commands."),
                error -> System.err.println("[Login Plugin] Failed to register Discord commands: " + error.getMessage())
        );
    }
}