package me.login.discord.moderation; // <-- CHANGED

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
            plugin.getLogger().severe("LuckPerms API not found. DiscordRankCommand will not work.");
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("rank")) return;
        if (luckPerms == null) {
            event.reply("This command is disabled because the LuckPerms API is not available.").setEphemeral(true).queue();
            return;
        }

        // Example: /rank set <player_name> <rank_name>
        if (event.getOption("set") != null) {
            handleRankSet(event);
            return;
        }

        // Example: /rank check (for self)
        handleRankCheck(event);
    }

    private void handleRankCheck(SlashCommandInteractionEvent event) {
        User user = event.getUser();
        UUID uuid = plugin.getDiscordLinking().getLinkedUuid(user.getIdLong());

        if (uuid == null) {
            event.reply("Your Discord account is not linked to a Minecraft account. Use `/discord link` in-game.").setEphemeral(true).queue();
            return;
        }

        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        String playerName = player.getName() != null ? player.getName() : "Unknown";

        luckPerms.getUserManager().loadUser(uuid).thenAccept(lpUser -> {
            String primaryGroup = lpUser.getPrimaryGroup();
            String playtime = "Unknown"; // You would get this from your playtime manager
            // String nextRank = "Unknown"; // You would get this from your rank manager
            // String nextRankTime = "N/A"; // You would get this from your rank manager

            EmbedBuilder eb = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("Rank Info for " + playerName)
                    .addField("Current Rank", primaryGroup, true)
                    .addField("Total Playtime", playtime, true)
                    // .addField("Next Rank", nextRank, true)
                    // .addField("Time to Next Rank", nextRankTime, false)
                    .setFooter("User: " + user.getAsTag());

            event.replyEmbeds(eb.build()).setEphemeral(true).queue();

        }).exceptionally(ex -> {
            event.reply("An error occurred while loading your player data.").setEphemeral(true).queue();
            ex.printStackTrace();
            return null;
        });
    }

    private void handleRankSet(SlashCommandInteractionEvent event) {
        // This is a placeholder for a sub-command that doesn't exist yet
        // You would need to register "set" as a subcommand
        event.reply("Setting ranks is not yet implemented via this command.").setEphemeral(true).queue();
    }
}