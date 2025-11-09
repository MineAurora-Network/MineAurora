package me.login.discord.moderation; // <-- CHANGED

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.awt.Color;
import java.util.EnumSet;

public class DiscordStaffCommands extends ListenerAdapter {

    private final Login plugin;
    // --- NEW FIELD ---
    private final DiscordCommandLogger logger;
    // --- END NEW FIELD ---
    // private final long staffChannelId; // <-- REMOVED

    // --- CONSTRUCTOR UPDATED ---
    public DiscordStaffCommands(Login plugin, DiscordCommandLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
        // this.staffChannelId = plugin.getConfig().getLong("staff-bot-channel"); // <-- REMOVED
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("staffchat")) return;

        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) { // Or your own mod check
            event.reply("You don't have permission for staff chat.").setEphemeral(true).queue();
            return;
        }

        OptionMapping messageOption = event.getOption("message");
        if (messageOption == null) {
            event.reply("You must provide a message.").setEphemeral(true).queue();
            return;
        }

        String message = messageOption.getAsString();
        String senderName = member.getEffectiveName();
        String senderAvatar = member.getUser().getEffectiveAvatarUrl();

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.MAGENTA)
                .setAuthor(senderName + " (Discord Staff)", null, senderAvatar)
                .setDescription(message);

        // --- UPDATED TO USE LOGGER ---
        // This is a special case. The logger is for simple text.
        // We will send an embed to the staff channel.
        long staffChannelId = plugin.getConfig().getLong("staff-bot-channel", 0);
        if (staffChannelId != 0) {
            MessageChannel staffChannel = event.getJDA().getTextChannelById(staffChannelId);
            if (staffChannel != null) {
                staffChannel.sendMessageEmbeds(eb.build()).queue();
                event.reply("Staff message sent.").setEphemeral(true).queue();
            } else {
                event.reply("Error: Staff channel not found.").setEphemeral(true).queue();
            }
        } else {
            event.reply("Error: Staff channel ID not set in config.").setEphemeral(true).queue();
        }
        // --- END UPDATE ---
    }
}