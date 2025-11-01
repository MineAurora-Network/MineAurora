package me.login.discordcommand;

import me.login.Login;
import me.login.discordlinking.DiscordLinkDatabase;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

// --- ADDED IMPORTS ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
// --- END ADDED IMPORTS ---

import java.awt.Color;
import java.util.Map;
import java.util.UUID;

public class DiscordStaffCommands {

    private final Login plugin;
    private final ModerationDatabase modDb;
    private final DiscordLinkDatabase linkDb;

    public DiscordStaffCommands(Login plugin, ModerationDatabase modDb, DiscordLinkDatabase linkDb) {
        this.plugin = plugin;
        this.modDb = modDb;
        this.linkDb = linkDb;
    }

    // Helper to get OfflinePlayer and check if they exist
    private OfflinePlayer getTargetPlayer(SlashCommandInteractionEvent event, String optionName) {
        String playerName = event.getOption(optionName).getAsString();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            event.getHook().sendMessage("Error: Player `" + playerName + "` not found.").queue();
            return null;
        }
        return target;
    }

    // Helper for hierarchy check
    private boolean checkHierarchy(SlashCommandInteractionEvent event, OfflinePlayer target) {
        User staffUser = event.getUser();
        UUID staffUUID = plugin.getDiscordLinking().getLinkedUuid(staffUser.getIdLong());

        if (staffUUID == null) {
            event.getHook().sendMessage("Error: You must link your Minecraft account with `/discord link` to use staff commands.").queue();
            return false;
        }

        // Self-punish check
        if (staffUUID.equals(target.getUniqueId())) {
            event.getHook().sendMessage("Error: You cannot run this command on yourself.").queue();
            return false;
        }

        // Hierarchy check
        int senderWeight = Utils.getLuckPermsRankWeight(Bukkit.getOfflinePlayer(staffUUID));
        int targetWeight = Utils.getLuckPermsRankWeight(target);

        if (senderWeight > 0 && senderWeight <= targetWeight) {
            event.getHook().sendMessage("Error: You cannot punish a player with an equal or higher rank.").queue();
            return false;
        }

        return true; // Check passed
    }

    public void handleMute(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason").getAsString();
        User staffUser = event.getUser();

        if (!checkHierarchy(event, target)) return;

        long duration = Utils.parseDuration(durationStr);
        if (duration == 0) {
            event.getHook().sendMessage("Error: Invalid duration format. Use 'perm' or units like s, m, h, d.").queue();
            return;
        }

        UUID staffUUID = plugin.getDiscordLinking().getLinkedUuid(staffUser.getIdLong());
        String staffName = staffUser.getName(); // Use Discord name for the log

        boolean success = modDb.mutePlayer(target.getUniqueId(), target.getName(), staffUUID, staffName, reason, duration);

        if (success) {
            String durationFormatted = Utils.formatDuration(duration);
            String msg = String.format("&b%s &fwas muted by &b%s&f (Discord) for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durationFormatted);
            Utils.broadcastToStaff(msg);
            plugin.sendStaffLog("[Discord] " + staffName + " muted " + target.getName() + " for " + durationFormatted + ". Reason: " + reason);

            event.getHook().sendMessage("Success: Muted `" + target.getName() + "` for " + durationFormatted + ". Reason: " + reason).queue();
        } else {
            event.getHook().sendMessage("Error: Could not mute player. Are they already muted?").queue();
        }
    }

    public void handleUnmute(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        User staffUser = event.getUser();
        String staffName = staffUser.getName();

        boolean wasMuted = modDb.unmutePlayer(target.getUniqueId());

        if (wasMuted) {
            String msg = String.format("&b%s &fwas unmuted by &b%s&f (Discord).", target.getName(), staffName);
            Utils.broadcastToStaff(msg);
            plugin.sendStaffLog("[Discord] " + staffName + " unmuted " + target.getName());

            event.getHook().sendMessage("Success: Unmuted `" + target.getName() + "`.").queue();
        } else {
            event.getHook().sendMessage("Error: Player `" + target.getName() + "` is not muted.").queue();
        }
    }

    public void handleBan(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        String durationStr = event.getOption("duration").getAsString();
        String reason = event.getOption("reason").getAsString();
        User staffUser = event.getUser();

        if (!checkHierarchy(event, target)) return;

        long duration = Utils.parseDuration(durationStr);
        if (duration == 0) {
            event.getHook().sendMessage("Error: Invalid duration format. Use 'perm' or units like s, m, h, d.").queue();
            return;
        }

        UUID staffUUID = plugin.getDiscordLinking().getLinkedUuid(staffUser.getIdLong());
        String staffName = staffUser.getName();

        boolean success = modDb.banPlayer(target.getUniqueId(), target.getName(), staffUUID, staffName, reason, duration);
        final String durationFormatted = Utils.formatDuration(duration); // Final for lambda

        if (success) {
            String msg = String.format("&4%s &fwas banned by &4%s&f (Discord) for &e%s &f(&e%s&f).", target.getName(), staffName, reason, durationFormatted);
            Utils.broadcastToStaff(msg);
            plugin.sendStaffLog("[Discord] " + staffName + " banned " + target.getName() + " for " + durationFormatted + ". Reason: " + reason);

            event.getHook().sendMessage("Success: Banned `" + target.getName() + "` for " + durationFormatted + ". Reason: " + reason).queue();

            // Kick player if online (must run sync)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (target.isOnline()) {
                    Component kickMessage = Utils.getServerPrefix(plugin)
                            .append(Component.text("\nYou have been banned from this server.", NamedTextColor.RED))
                            .append(Component.newline())
                            .append(Component.text("Reason: ", NamedTextColor.RED).append(Component.text(reason, NamedTextColor.WHITE)))
                            .append(Component.newline())
                            .append(Component.text("Expires in: ", NamedTextColor.RED).append(Component.text(durationFormatted, NamedTextColor.WHITE)));
                    target.getPlayer().kick(kickMessage);
                }
            });
        } else {
            event.getHook().sendMessage("Error: Could not ban player. Are they already banned?").queue();
        }
    }

    public void handleUnban(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        User staffUser = event.getUser();
        String staffName = staffUser.getName();

        boolean wasBanned = modDb.unbanPlayer(target.getUniqueId());

        if (wasBanned) {
            String msg = String.format("&a%s &fwas unbanned by &a%s&f (Discord).", target.getName(), staffName);
            Utils.broadcastToStaff(msg);
            plugin.sendStaffLog("[Discord] " + staffName + " unbanned " + target.getName());

            event.getHook().sendMessage("Success: Unbanned `" + target.getName() + "`.").queue();
        } else {
            event.getHook().sendMessage("Error: Player `" + target.getName() + "` is not banned by UUID.").queue();
        }
    }

    public void handleMuteInfo(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        Map<String, Object> info = modDb.getActiveMuteInfo(target.getUniqueId());

        if (info == null) {
            event.getHook().sendMessage("Player `" + target.getName() + "` is not currently muted.").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Mute Info: " + target.getName())
                .setColor(Color.ORANGE)
                .addField("Muted By", (String) info.get("staff_name"), true)
                .addField("Reason", (String) info.get("reason"), true)
                .addField("Time Left", Utils.formatTimeLeft((long) info.get("end_time")), true);
        // .setThumbnail(...) line removed

        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    public void handleBanInfo(SlashCommandInteractionEvent event) {
        OfflinePlayer target = getTargetPlayer(event, "player");
        if (target == null) return;

        Map<String, Object> info = modDb.getActiveBanInfo(target.getUniqueId());

        if (info == null) {
            event.getHook().sendMessage("Player `" + target.getName() + "` is not currently banned by UUID.").queue();
            return;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setTitle("Ban Info: " + target.getName())
                .setColor(Color.RED)
                .addField("Banned By", (String) info.get("staff_name"), true)
                .addField("Reason", (String) info.get("reason"), true)
                .addField("Time Left", Utils.formatTimeLeft((long) info.get("end_time")), true);
        // .setThumbnail(...) line removed

        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }
}