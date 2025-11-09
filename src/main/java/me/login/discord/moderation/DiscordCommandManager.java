package me.login.discord.moderation; // <-- CHANGED

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import org.bukkit.Bukkit;
// import org.bukkit.ChatColor; // <-- REMOVED
import org.bukkit.entity.Player;

// --- NEW KYORI IMPORTS ---\
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
// --- END NEW IMPORTS ---\

import java.awt.Color;
import java.util.stream.Collectors;

public class DiscordCommandManager extends ListenerAdapter {

    private final Login plugin;
    // --- NEW FIELD ---
    private final DiscordCommandLogger logger;
    // --- END FIELD ---

    // --- CONSTRUCTOR UPDATED ---
    public DiscordCommandManager(Login plugin, DiscordCommandLogger logger) {
        this.plugin = plugin;
        this.logger = logger;
    }
    // --- END UPDATE ---

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        switch (event.getName()) {
            case "playerlist":
                handlePlayerList(event);
                break;
            case "online":
                handleOnlineCommand(event);
                break;
            case "msg":
                handleMsgCommand(event);
                break;
        }
    }

    private void handlePlayerList(SlashCommandInteractionEvent event) {
        String players = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        if (players.isEmpty()) {
            players = "No players are online.";
        }
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.CYAN)
                .setTitle("Online Players (" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription("`" + players + "`");
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleMsgCommand(SlashCommandInteractionEvent event) {
        User sender = event.getUser();
        String playerName = event.getOption("player").getAsString();
        String message = event.getOption("message").getAsString();

        // Check for staff permissions
        Member member = event.getMember();
        if (member == null || !member.hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("You do not have permission to use this command.").setEphemeral(true).queue();
            return;
        }

        Player player = Bukkit.getPlayer(playerName);
        if (player == null || !player.isOnline()) {
            event.reply("Player `" + playerName + "` is not online.").setEphemeral(true).queue();
            return;
        }

        // --- KYORI ADVENTURE UPDATE ---
        Component formattedMessage = Component.text("[", NamedTextColor.GRAY)
                .append(Component.text("Discord", NamedTextColor.BLUE))
                .append(Component.text("] ", NamedTextColor.GRAY))
                .append(Component.text(sender.getName(), NamedTextColor.YELLOW))
                .append(Component.text(": ", NamedTextColor.WHITE))
                .append(Component.text(message, NamedTextColor.WHITE));

        player.sendMessage(formattedMessage);
        // --- END UPDATE ---

        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setDescription("✅ Message sent to **" + player.getName() + "**:\n" + message);
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        // --- UPDATED TO USE LOGGER ---
        logger.logNormal("`[Discord -> " + player.getName() + "]` **" + sender.getAsTag() + "**: " + message);
    }

    private void handleOnlineCommand(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder()
                .setColor(Color.GREEN)
                .setDescription("Players Online: **" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + "**");
        event.replyEmbeds(eb.build()).setEphemeral(false).queue();
    }
}