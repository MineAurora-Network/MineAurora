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

// --- NEW KYORI IMPORTS ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
// --- END NEW IMPORTS ---

import java.awt.Color;
import java.util.stream.Collectors;

public class DiscordCommandManager extends ListenerAdapter {

    private final Login plugin;
    // --- NEW FIELDS ---
    private final DiscordCommandLogger logger;
    private final Component prefix;
    // --- END NEW FIELDS ---

    // --- CONSTRUCTOR UPDATED ---
    public DiscordCommandManager(Login plugin, DiscordCommandLogger logger) {
        this.plugin = plugin;
        this.logger = logger;

        // --- ADDED PREFIX INITIALIZATION ---
        String prefixStr = plugin.getConfig().getString("server_prefix");
        if (prefixStr == null || prefixStr.isEmpty()) {
            prefixStr = plugin.getConfig().getString("server_prefix_2", "&cError: Prefix not found. ");
        }

        if (prefixStr.contains("<")) {
            this.prefix = MiniMessage.miniMessage().deserialize(prefixStr);
        } else {
            this.prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixStr);
        }
        // --- END PREFIX ---
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (event.getGuild() == null) return;
        Member member = event.getMember();
        if (member == null || member.getUser().isBot()) return;

        switch (event.getName()) {
            case "playerlist":
                handlePlayerListCommand(event);
                break;
            case "msg":
                handleMessageCommand(event);
                break;
            case "online":
                handleOnlineCommand(event);
                break;
        }
    }

    private void handlePlayerListCommand(SlashCommandInteractionEvent event) {
        String players = Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .collect(Collectors.joining(", "));
        if (players.isEmpty()) players = "No players online.";

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Online Players (" + Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers() + ")")
                .setDescription("`" + players + "`")
                .setColor(Color.CYAN);
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }

    private void handleMessageCommand(SlashCommandInteractionEvent event) {
        OptionMapping playerOption = event.getOption("player");
        OptionMapping messageOption = event.getOption("message");

        if (playerOption == null || messageOption == null) {
            event.reply("Player and message are required.").setEphemeral(true).queue();
            return;
        }

        String playerName = playerOption.getAsString();
        String message = messageOption.getAsString();
        User sender = event.getUser();

        Player player = Bukkit.getPlayerExact(playerName);
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
        event.replyEmbeds(eb.build()).setEphemeral(true).queue();
    }
}