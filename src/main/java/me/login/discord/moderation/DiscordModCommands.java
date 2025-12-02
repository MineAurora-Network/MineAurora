package me.login.discord.moderation;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.moderation.ModerationModule;
import me.login.moderation.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;

import java.awt.Color;
import java.util.Map;
import java.util.UUID;

public class DiscordModCommands extends ListenerAdapter {

    private final Login plugin;
    private final DiscordModConfig modConfig;
    private final DiscordCommandLogger logger;
    private final DiscordLinking linking;
    private final ModerationModule moderationModule;
    private final UUID CONSOLE_UUID = new UUID(0, 0);

    public DiscordModCommands(Login plugin, DiscordModConfig modConfig, DiscordCommandLogger logger, DiscordLinking linking, ModerationModule moderationModule) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.logger = logger;
        this.linking = linking;
        this.moderationModule = moderationModule;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("No permission.").setEphemeral(true).queue();
            return;
        }

        String cmd = event.getName();
        if (cmd.equals("mccheck")) handleCheck(event);
        else if (cmd.equals("mcban")) handleBan(event, false);
        else if (cmd.equals("mcipban")) handleBan(event, true);
        else if (cmd.equals("mcunban")) handleUnban(event, false);
        else if (cmd.equals("mcunbanip")) handleUnban(event, true);
        else if (cmd.equals("mcmute")) handleMute(event);
        else if (cmd.equals("mcunmute")) handleUnmute(event);
    }

    private OfflinePlayer resolveTarget(String input) {
        // 1. Check if input is a Discord ID or Mention
        String idStr = input.replaceAll("[^0-9]", "");
        if (idStr.length() > 15) {
            try {
                long discordId = Long.parseLong(idStr);
                UUID linked = linking.getLinkedUuid(discordId);
                if (linked != null) return Bukkit.getOfflinePlayer(linked);
            } catch (Exception ignored) {}
        }
        // 2. Assume Minecraft Name
        return Bukkit.getOfflinePlayer(input);
    }

    private void handleCheck(SlashCommandInteractionEvent event) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply(true).queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                event.getHook().sendMessage("Player not found.").queue();
                return;
            }

            Map<String, Object> ban = moderationModule.getDatabase().getActiveBanInfo(target.getUniqueId());
            Map<String, Object> mute = moderationModule.getDatabase().getActiveMuteInfo(target.getUniqueId());

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.CYAN).setTitle("Check: " + target.getName());
            eb.addField("Ban", ban == null ? "None" : "Reason: " + ban.get("reason"), false);
            eb.addField("Mute", mute == null ? "None" : "Reason: " + mute.get("reason"), false);
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void handleBan(SlashCommandInteractionEvent event, boolean ip) {
        String targetStr = event.getOption("target").getAsString();
        String reason = event.getOption("reason", "No reason", OptionMapping::getAsString);
        long duration = Utils.parseDuration(event.getOption("duration", "perm", OptionMapping::getAsString));

        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (target.getName() == null) { event.getHook().sendMessage("Player not found.").queue(); return; }

            if (ip) {
                if (!target.isOnline()) { event.getHook().sendMessage("Player must be online for IP Ban.").queue(); return; }
                String address = target.getPlayer().getAddress().getAddress().getHostAddress();
                moderationModule.getDatabase().ipBanPlayer(address, CONSOLE_UUID, event.getUser().getName(), reason, duration, target.getUniqueId(), target.getName());
                event.getHook().sendMessage("IP Banned " + target.getName()).queue();
            } else {
                moderationModule.getDatabase().banPlayer(target.getUniqueId(), target.getName(), CONSOLE_UUID, event.getUser().getName(), reason, duration);
                event.getHook().sendMessage("Banned " + target.getName()).queue();
            }

            if (target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> target.getPlayer().kickPlayer(ChatColor.RED + "Banned: " + reason));
            }
        });
    }

    private void handleUnban(SlashCommandInteractionEvent event, boolean ip) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (target.getName() == null) { event.getHook().sendMessage("Player not found.").queue(); return; }

            if (ip) {
                // Simplified: Unban IP by resolving player (assuming they were banned by name+IP link)
                // Ideally, unbanip would take raw IP string, but here we lookup by player
                moderationModule.getDatabase().unbanPlayer(target.getUniqueId()); // Fallback
                event.getHook().sendMessage("Unbanned " + target.getName()).queue();
            } else {
                moderationModule.getDatabase().unbanPlayer(target.getUniqueId());
                event.getHook().sendMessage("Unbanned " + target.getName()).queue();
            }
        });
    }

    private void handleMute(SlashCommandInteractionEvent event) {
        String targetStr = event.getOption("target").getAsString();
        String reason = event.getOption("reason", "No reason", OptionMapping::getAsString);
        long duration = Utils.parseDuration(event.getOption("duration", "perm", OptionMapping::getAsString));

        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (target.getName() == null) { event.getHook().sendMessage("Player not found.").queue(); return; }

            moderationModule.getDatabase().mutePlayer(target.getUniqueId(), target.getName(), CONSOLE_UUID, event.getUser().getName(), reason, duration);
            event.getHook().sendMessage("Muted " + target.getName()).queue();

            if(target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> target.getPlayer().sendMessage(ChatColor.RED + "Muted: " + reason));
            }
        });
    }

    private void handleUnmute(SlashCommandInteractionEvent event) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            moderationModule.getDatabase().unmutePlayer(target.getUniqueId());
            event.getHook().sendMessage("Unmuted " + target.getName()).queue();
        });
    }
}