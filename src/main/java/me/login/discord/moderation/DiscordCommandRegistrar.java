package me.login.discord.moderation;

import me.login.Login;
import net.dv8tion.jda.api.JDA;
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
                Commands.slash("playerlist", "Shows the current online players."),
                Commands.slash("online", "Shows the server online player count."),
                Commands.slash("msg", "Send a private message to a player.")
                        .addOption(OptionType.STRING, "player", "The player's in-game name.", true)
                        .addOption(OptionType.STRING, "message", "The message to send.", true),

                Commands.slash("staffchat", "Send a message to the staff chat.")
                        .addOption(OptionType.STRING, "message", "The message to send.", true),

                Commands.slash("mccheck", "Check the ban/mute status of a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),

                Commands.slash("mcban", "Ban a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration (e.g., 10m, 1h, 7d, perm).", false),

                Commands.slash("mcipban", "IP-Ban a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the IP-ban.", false)
                        .addOption(OptionType.STRING, "duration", "Duration.", false),

                Commands.slash("mcunban", "Unban a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),

                Commands.slash("mcunbanip", "Un-IP-Ban a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),

                Commands.slash("mcmute", "Mute a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true)
                        .addOption(OptionType.STRING, "reason", "The reason for the mute.", false)
                        .addOption(OptionType.STRING, "duration", "Duration.", false),

                Commands.slash("mcunmute", "Unmute a player.")
                        .addOption(OptionType.STRING, "target", "Minecraft Name or Discord User (ID/Mention).", true),

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