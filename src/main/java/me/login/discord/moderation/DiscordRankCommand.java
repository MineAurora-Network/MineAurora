package me.login.discord.moderation;

import me.login.Login;
import me.login.misc.rank.RankManager;
import me.login.misc.rank.util.TimeUtil;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.group.Group;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.awt.Color;
import java.util.UUID;

public class DiscordRankCommand extends ListenerAdapter {

    private final Login plugin;
    private final LuckPerms luckPerms;
    private final long staffChannelId;
    private final RankManager rankManager;

    public DiscordRankCommand(Login plugin, RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
        this.staffChannelId = Long.parseLong(plugin.getConfig().getString("staff-bot-channel", "0"));

        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        } else {
            this.luckPerms = null;
            plugin.getLogger().severe("LuckPerms API not found! /rank command disabled.");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("rank")) return;
        if (luckPerms == null || rankManager == null) {
            event.reply("Error: The Rank System is not connected. Please contact an admin.").setEphemeral(true).queue();
            return;
        }
        if (event.getChannel().getIdLong() != staffChannelId) {
            event.reply("This command can only be used in the staff channel.").setEphemeral(true).queue();
            return;
        }

        String subcommand = event.getSubcommandName();
        if (subcommand == null) {
            event.reply("Error: Unknown subcommand.").setEphemeral(true).queue();
            return;
        }

        switch (subcommand) {
            case "set":
                handleSetRank(event);
                break;
            case "info":
                handleRankInfo(event);
                break;
        }
    }

    private void handleSetRank(SlashCommandInteractionEvent event) {
        User discordTarget = event.getOption("user").getAsUser();
        String rankName = event.getOption("rank").getAsString();
        String durationString = event.getOption("duration").getAsString();
        User discordSender = event.getUser();

        event.deferReply().queue();

        UUID senderUuid = plugin.getDiscordLinking().getLinkedUuid(discordSender.getIdLong());
        if (senderUuid == null) {
            event.getHook().sendMessage("You must have a linked account to use this command.").queue();
            return;
        }

        UUID targetUuid = plugin.getDiscordLinking().getLinkedUuid(discordTarget.getIdLong());
        if (targetUuid == null) {
            event.getHook().sendMessage("Target user " + discordTarget.getAsMention() + " does not have a linked Minecraft account.").queue();
            return;
        }

        long durationMillis;
        try {
            durationMillis = TimeUtil.parseDuration(durationString);
        } catch (IllegalArgumentException e) {
            event.getHook().sendMessage("Invalid time format: `" + durationString + "`. Use `1h`, `7d`, `perm`, etc.").queue();
            return;
        }

        Group group = luckPerms.getGroupManager().getGroup(rankName);
        if (group == null) {
            event.getHook().sendMessage("The rank `" + rankName + "` does not exist.").queue();
            return;
        }

        luckPerms.getUserManager().loadUser(senderUuid).thenAcceptAsync(senderUser -> {
            luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(targetUser -> {
                OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
                String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

                int senderWeight = rankManager.getWeight(senderUser.getPrimaryGroup());
                int targetWeight = rankManager.getWeight(targetUser.getPrimaryGroup());

                boolean isOwner = event.getMember() != null && event.getMember().isOwner();
                if (senderWeight <= targetWeight && !isOwner) {
                    event.getHook().sendMessage("You cannot modify the rank of a player with an equal or higher rank.").queue();
                    return;
                }

                rankManager.setRank(discordSender.getName(), senderUuid, targetUser, group, durationMillis);

                String timeString = (durationMillis == -1) ? "permanent" : TimeUtil.formatDuration(durationMillis);
                EmbedBuilder eb = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("Rank Updated")
                        .setDescription(String.format("Successfully set **%s's** rank to **%s** for **%s**.",
                                targetName, rankName, timeString))
                        .addField("Moderator", discordSender.getAsMention(), false);
                event.getHook().sendMessageEmbeds(eb.build()).queue();

            });
        });
    }

    private void handleRankInfo(SlashCommandInteractionEvent event) {
        User discordTarget = event.getOption("user").getAsUser();
        event.deferReply().queue();

        UUID targetUuid = plugin.getDiscordLinking().getLinkedUuid(discordTarget.getIdLong());
        if (targetUuid == null) {
            event.getHook().sendMessage("User " + discordTarget.getAsMention() + " does not have a linked Minecraft account.").queue();
            return;
        }

        luckPerms.getUserManager().loadUser(targetUuid).thenAcceptAsync(targetUser -> {
            if (targetUser == null) {
                event.getHook().sendMessage("Could not load Minecraft user data.").queue();
                return;
            }
            Component infoComponent = rankManager.getRankInfo(targetUser);

            String plainInfo = PlainTextComponentSerializer.plainText().serialize(infoComponent);
            plainInfo = plainInfo.replace("%nl%", "\n");

            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetUuid);
            String targetName = targetPlayer.getName() != null ? targetPlayer.getName() : "Unknown";

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.CYAN)
                    .setAuthor(targetName + "'s Rank Info", null, "https://crafatar.com/avatars/" + targetUuid + "?overlay")
                    .setDescription(plainInfo);

            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }
}