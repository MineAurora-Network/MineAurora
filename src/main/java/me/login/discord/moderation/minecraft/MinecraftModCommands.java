package me.login.discord.moderation.minecraft;

import me.login.Login;
import me.login.discord.linking.DiscordLinking;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.discord.DiscordModConfig;
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

/**
 * Handles Discord Slash Commands that effect MINECRAFT (mcban, mcmute, etc).
 */
public class MinecraftModCommands extends ListenerAdapter {

    private final Login plugin;
    private final DiscordModConfig modConfig;
    private final DiscordCommandLogger logger;
    private final DiscordLinking linking;
    private final ModerationModule moderationModule;
    private final UUID CONSOLE_UUID = new UUID(0, 0);

    public MinecraftModCommands(Login plugin, DiscordModConfig modConfig, DiscordCommandLogger logger, DiscordLinking linking, ModerationModule moderationModule) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.logger = logger;
        this.linking = linking;
        this.moderationModule = moderationModule;
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        String cmd = event.getName();

        // Only handle MC commands
        if (!cmd.startsWith("mc")) return;

        // General Discord Permission Check for Staff
        if (!event.getMember().hasPermission(Permission.MESSAGE_MANAGE)) {
            event.reply("No permission (Requires Manage Messages).").setEphemeral(true).queue();
            return;
        }

        // Link Check: Executor MUST be linked
        UUID staffUUID = linking.getLinkedUuid(event.getUser().getIdLong());
        if (staffUUID == null) {
            event.reply("You must link your Minecraft account to use moderation commands. `/discord link` in-game.").setEphemeral(true).queue();
            return;
        }

        if (cmd.equals("mccheck")) handleCheck(event);
        else if (cmd.equals("mcban")) handleBan(event, false);
        else if (cmd.equals("mcipban")) handleBan(event, true);
        else if (cmd.equals("mcunban")) handleUnban(event, false);
        else if (cmd.equals("mcunbanip")) handleUnban(event, true);
        else if (cmd.equals("mcmute")) handleMute(event);
        else if (cmd.equals("mcunmute")) handleUnmute(event);
    }

    private OfflinePlayer resolveTarget(String input) {
        String idStr = input.replaceAll("[^0-9]", "");
        if (idStr.length() > 15) {
            try {
                long discordId = Long.parseLong(idStr);
                UUID linked = linking.getLinkedUuid(discordId);
                if (linked != null) return Bukkit.getOfflinePlayer(linked);
            } catch (Exception ignored) {}
        }
        return Bukkit.getOfflinePlayer(input);
    }

    private void handleCheck(SlashCommandInteractionEvent event) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply(true).queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                event.getHook().sendMessage("Player not found in Minecraft DB.").queue();
                return;
            }

            Map<String, Object> ban = moderationModule.getDatabase().getActiveBanInfo(target.getUniqueId());
            Map<String, Object> mute = moderationModule.getDatabase().getActiveMuteInfo(target.getUniqueId());

            EmbedBuilder eb = new EmbedBuilder().setColor(Color.CYAN).setTitle("MC Check: " + target.getName());
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

            // Use linked UUID if available, otherwise Console UUID for logs (Discord user initiated)
            UUID staffUUID = linking.getLinkedUuid(event.getUser().getIdLong());

            if (ip) {
                if (!target.isOnline()) { event.getHook().sendMessage("Player must be online for IP Ban.").queue(); return; }
                String address = target.getPlayer().getAddress().getAddress().getHostAddress();
                moderationModule.getDatabase().ipBanPlayer(address, staffUUID, event.getUser().getName(), reason, duration, target.getUniqueId(), target.getName());
                event.getHook().sendMessage("IP Banned " + target.getName()).queue();
            } else {
                moderationModule.getDatabase().banPlayer(target.getUniqueId(), target.getName(), staffUUID, event.getUser().getName(), reason, duration);
                event.getHook().sendMessage("Banned " + target.getName()).queue();
            }

            if (target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> target.getPlayer().kickPlayer(ChatColor.RED + "Banned: " + reason));
            }
            logger.logStaff("[MC-Ban] **" + event.getUser().getAsTag() + "** banned **" + target.getName() + "** for: " + reason);
        });
    }

    private void handleUnban(SlashCommandInteractionEvent event, boolean ip) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (target.getName() == null) { event.getHook().sendMessage("Player not found.").queue(); return; }

            moderationModule.getDatabase().unbanPlayer(target.getUniqueId());
            event.getHook().sendMessage("Unbanned " + target.getName()).queue();
            logger.logStaff("[MC-Unban] **" + event.getUser().getAsTag() + "** unbanned **" + target.getName() + "**");
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

            UUID staffUUID = linking.getLinkedUuid(event.getUser().getIdLong());

            moderationModule.getDatabase().mutePlayer(target.getUniqueId(), target.getName(), staffUUID, event.getUser().getName(), reason, duration);
            event.getHook().sendMessage("Muted " + target.getName()).queue();

            if(target.isOnline()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> target.getPlayer().sendMessage(ChatColor.RED + "Muted: " + reason));
            }
            logger.logStaff("[MC-Mute] **" + event.getUser().getAsTag() + "** muted **" + target.getName() + "** for: " + reason);
        });
    }

    private void handleUnmute(SlashCommandInteractionEvent event) {
        String targetStr = event.getOption("target").getAsString();
        event.deferReply().queue();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer target = resolveTarget(targetStr);
            if (target.getName() == null) { event.getHook().sendMessage("Player not found.").queue(); return; }

            moderationModule.getDatabase().unmutePlayer(target.getUniqueId());
            event.getHook().sendMessage("Unmuted " + target.getName()).queue();
            logger.logStaff("[MC-Unmute] **" + event.getUser().getAsTag() + "** unmuted **" + target.getName() + "**");
        });
    }
}