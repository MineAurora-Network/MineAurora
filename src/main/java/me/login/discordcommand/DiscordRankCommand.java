package me.login.discordcommand;

import me.login.Login;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.awt.Color;
import java.util.UUID;

public class DiscordRankCommand extends ListenerAdapter {

    private final Login plugin;
    private final LuckPerms luckPerms;
    private final long staffChannelId;

    public DiscordRankCommand(Login plugin) {
        this.plugin = plugin;
        this.staffChannelId = Long.parseLong(plugin.getConfig().getString("staff-bot-channel", "0"));

        // Get the LuckPerms API
        RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
        if (provider != null) {
            this.luckPerms = provider.getProvider();
        } else {
            this.luckPerms = null;
            plugin.getLogger().severe("LuckPerms API not found! /rank command will not work.");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("rank")) {
            return;
        }

        // --- NEW CHANNEL CHECK ---
        if (event.getChannel().getIdLong() != staffChannelId) {
            event.reply("This command can only be used in the staff bot channel.").setEphemeral(true).queue();
            return;
        }
        // --- END CHANNEL CHECK ---

        if (luckPerms == null) {
            event.reply("Error: The LuckPerms API is not loaded on the server. This command is disabled.").setEphemeral(true).queue();
            return;
        }

        event.deferReply().queue(); // Defer reply here

        // Run async to avoid blocking JDA
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                handleRank(event);
            } catch (Exception e) {
                plugin.getLogger().severe("Error processing /rank command: " + e.getMessage());
                e.printStackTrace();
                event.getHook().sendMessage("An internal error occurred. Please check the server console.").queue();
            }
        });
    }

    private void handleRank(SlashCommandInteractionEvent event) {
        // 1. Get staff's linked UUID
        UUID staffUUID = plugin.getDiscordLinking().getLinkedUuid(event.getMember().getIdLong());
        if (staffUUID == null) {
            event.getHook().sendMessage("Error: You must link your Minecraft account to use this command.").queue();
            return;
        }

        String targetName = event.getOption("player").getAsString();
        String rankName = event.getOption("rank").getAsString().toLowerCase();

        // 2. Perform permission check on staff's linked UUID
        luckPerms.getUserManager().loadUser(staffUUID).thenAccept(staffUser -> {
            boolean hasPerm = staffUser.getCachedData().getPermissionData().checkPermission("rank.discord").asBoolean();

            if (!hasPerm) {
                event.getHook().sendMessage("Error: Your linked Minecraft account does not have the `rank.discord` permission.").queue();
                return;
            }

            // 3. Get target player
            OfflinePlayer targetPlayer = Bukkit.getOfflinePlayer(targetName);
            if (!targetPlayer.hasPlayedBefore() && !targetPlayer.isOnline()) {
                event.getHook().sendMessage("Error: Player `" + targetName + "` not found.").queue();
                return;
            }

            // 4. Check if rank exists
            if (luckPerms.getGroupManager().getGroup(rankName) == null) {
                event.getHook().sendMessage("Error: The rank `" + rankName + "` does not exist in LuckPerms.").queue();
                return;
            }

            // 5. Apply the rank change
            luckPerms.getUserManager().loadUser(targetPlayer.getUniqueId()).thenAccept(targetUser -> {
                // Remove all other rank nodes
                targetUser.data().clear(node -> node instanceof InheritanceNode);
                // Add the new rank node
                Node newRankNode = InheritanceNode.builder(rankName).build();
                targetUser.data().add(newRankNode);

                // Save the user
                luckPerms.getUserManager().saveUser(targetUser);

                EmbedBuilder eb = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("Rank Updated")
                        .setDescription(String.format("Successfully set **%s's** rank to **%s**.", targetPlayer.getName(), rankName))
                        .addField("Moderator", event.getMember().getAsMention(), false);

                event.getHook().sendMessageEmbeds(eb.build()).queue();

            }).exceptionally(ex -> {
                event.getHook().sendMessage("An error occurred while loading the target player's data.").queue();
                ex.printStackTrace();
                return null;
            });

        }).exceptionally(ex -> {
            event.getHook().sendMessage("An error occurred while loading your permission data.").queue();
            ex.printStackTrace();
            return null;
        });
    }
}