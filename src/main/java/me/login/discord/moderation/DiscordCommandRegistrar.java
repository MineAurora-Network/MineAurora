package me.login.discord.moderation;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData; // <-- IMPORT
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;

public class DiscordCommandRegistrar {

    public static void register(JDA jda, Login plugin, DiscordCommandLogger logger) {
        if (jda == null) {
            plugin.getLogger().warning("JDA is null, skipping Discord command registration.");
            return;
        }

        jda.addEventListener(new DiscordStaffCommands(plugin, logger));

        CommandListUpdateAction commands = jda.updateCommands();

        // --- RENAMED COMMANDS ---
        commands.addCommands(
                Commands.slash("playerlist", "Shows the current online players."),
                Commands.slash("online", "Shows the server online player count."),
                Commands.slash("msg", "Send a private message to a player.")
                        .addOption(OptionType.STRING, "player", "The player's in-game name.", true)
                        .addOption(OptionType.STRING, "message", "The message to send.", true),

                Commands.slash("staffchat", "Send a message to the staff chat.")
                        .addOption(OptionType.STRING, "message", "The message to send.", true),

                Commands.slash("mccheck", "Check the ban/mute status of a linked player.")
                        .addOption(OptionType.USER, "user", "The Discord user to check.", true),

                Commands.slash("mcban", "Ban a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to ban.", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 10m, 1h, 7d, 3mo, perm).", false),

                Commands.slash("mcipban", "IP-Ban a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to IP-ban.", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the IP-ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 10m, 1h, 7d, 3mo, perm).", false),

                Commands.slash("mcunban", "Unban a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to unban.", true),

                Commands.slash("mcunbanip", "Un-IP-Ban a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to un-IP-ban.", true),

                Commands.slash("mcmute", "Mute a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to mute.", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the mute.", false)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 10m, 1h, 7d, 3mo, perm).", false),

                Commands.slash("mcunmute", "Unmute a linked player via their Discord.")
                        .addOption(OptionType.USER, "user", "The Discord user to unmute.", true),

                // --- UPDATED RANK COMMAND ---
                Commands.slash("rank", "Manage player ranks.")
                        .addSubcommands(
                                new SubcommandData("set", "Set a player's rank.")
                                        .addOption(OptionType.USER, "user", "The Discord user to set rank for.", true)
                                        .addOption(OptionType.STRING, "rank", "The rank name.", true)
                                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 1h, 7d, perm).", true),
                                new SubcommandData("info", "Get info on a player's rank.")
                                        .addOption(OptionType.USER, "user", "The Discord user to check.", true)
                        )
                // --- END UPDATED RANK COMMAND ---
        );
        // --- END RENAMING ---

        commands.queue(
                s -> plugin.getLogger().info("Successfully registered " + s.size() + " Discord slash commands."),
                e -> plugin.getLogger().severe("Failed to register Discord slash commands: " + e.getMessage())
        );
    }
}