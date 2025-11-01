package me.login.discordcommand;

import me.login.Login;
import me.login.discordlinking.DiscordLinkDatabase;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.Utils;
import me.login.scoreboard.SkriptUtils;
import me.login.scoreboard.SkriptVarParse;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.awt.Color;
import java.util.List;
import java.util.Map;
import java.util.Set; // <-- NEW IMPORT
import java.util.UUID;
import java.util.stream.Collectors;

public class DiscordCommandManager extends ListenerAdapter {

    private final Login plugin;
    private final ModerationDatabase modDb;
    private final DiscordLinkDatabase linkDb;
    private final Economy economy;
    private final DiscordStaffCommands staffCommands;
    private final DiscordDataHelper dataHelper;

    private final long normalChannelId;
    private final long staffChannelId;

    // --- NEW COMMAND SETS ---
    private final Set<String> mcStaffCommands = Set.of("mcmute", "mcunmute", "mcban", "mcunban", "mcmuteinfo", "mcbaninfo");
    private final Set<String> normalCommands = Set.of("staff", "balance", "leaderboard", "profile");
    // --- END NEW COMMAND SETS ---

    public DiscordCommandManager(Login plugin) {
        this.plugin = plugin;
        this.modDb = plugin.getModerationDatabase();
        this.linkDb = plugin.getDatabase();
        this.economy = plugin.getVaultEconomy();
        this.normalChannelId = Long.parseLong(plugin.getConfig().getString("normal-bot-channel", "0"));
        this.staffChannelId = Long.parseLong(plugin.getConfig().getString("staff-bot-channel", "0"));

        this.staffCommands = new DiscordStaffCommands(plugin, modDb, linkDb);
        this.dataHelper = new DiscordDataHelper(plugin, economy, linkDb);
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        long channelId = event.getChannel().getIdLong();
        boolean isStaffChannel = (channelId == staffChannelId);
        boolean isNormalChannel = (channelId == normalChannelId);
        String commandName = event.getName();

        // --- MC STAFF COMMANDS ---
        if (mcStaffCommands.contains(commandName)) {
            if (!isStaffChannel) {
                event.reply("This command can only be used in the staff bot channel.").setEphemeral(true).queue();
                return;
            }

            event.deferReply(true).queue();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    switch (commandName) {
                        case "mcmute":
                            staffCommands.handleMute(event);
                            break;
                        case "mcunmute":
                            staffCommands.handleUnmute(event);
                            break;
                        case "mcban":
                            staffCommands.handleBan(event);
                            break;
                        case "mcunban":
                            staffCommands.handleUnban(event);
                            break;
                        case "mcmuteinfo":
                            staffCommands.handleMuteInfo(event);
                            break;
                        case "mcbaninfo":
                            staffCommands.handleBanInfo(event);
                            break;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error processing Discord staff command: " + e.getMessage());
                    e.printStackTrace();
                    event.getHook().sendMessage("An internal error occurred. Please check the server console.").queue();
                }
            });
            return;
        }

        // --- NORMAL COMMANDS ---
        if (normalCommands.contains(commandName)) {
            if (!isNormalChannel) {
                event.reply("This command can only be used in the normal bot channel.").setEphemeral(true).queue();
                return;
            }

            event.deferReply().queue();

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    switch (commandName) {
                        case "staff":
                            handleStaffList(event);
                            break;
                        case "balance":
                            handleBalance(event);
                            break;
                        case "leaderboard":
                            handleLeaderboard(event);
                            break;
                        case "profile":
                            handleProfile(event);
                            break;
                    }
                } catch (Exception e) {
                    plugin.getLogger().severe("Error processing Discord normal command: " + e.getMessage());
                    e.printStackTrace();
                    event.getHook().sendMessage("An internal error occurred. Please check the server console.").queue();
                }
            });
            return;
        }

        // If the command is not in any list, it's handled by another listener
        // (like DiscordModCommands or DiscordRankCommand), so we do nothing here.
    }

    // --- Normal Command Logic (Unchanged) ---

    private void handleStaffList(SlashCommandInteractionEvent event) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Online Staff");
        eb.setColor(Color.CYAN);

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            List<String> staffList = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> p.hasPermission("staff.staff"))
                    .map(p -> dataHelper.getLuckPermsRank(p) + " " + p.getName())
                    .collect(Collectors.toList());

            if (staffList.isEmpty()) {
                eb.setDescription("There are no staff members currently online.");
            } else {
                eb.setDescription(String.join("\n", staffList));
            }
            eb.setFooter("Total Players Online: " + Bukkit.getOnlinePlayers().size());
            event.getHook().sendMessageEmbeds(eb.build()).queue();
        });
    }

    private void handleBalance(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            event.getHook().sendMessage("Error: Player `" + playerName + "` not found.").queue();
            return;
        }

        EmbedBuilder eb = dataHelper.buildBalanceEmbed(target);
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleLeaderboard(SlashCommandInteractionEvent event) {
        String category = event.getOption("name").getAsString().toLowerCase();
        EmbedBuilder eb = dataHelper.buildLeaderboardEmbed(category);
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }

    private void handleProfile(SlashCommandInteractionEvent event) {
        String playerName = event.getOption("player").getAsString();
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);

        if (!target.hasPlayedBefore() && !target.isOnline()) {
            event.getHook().sendMessage("Error: Player `" + playerName + "` not found.").queue();
            return;
        }

        EmbedBuilder eb = dataHelper.buildProfileEmbed(target);
        event.getHook().sendMessageEmbeds(eb.build()).queue();
    }
}