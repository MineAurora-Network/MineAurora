package me.login.discord.linking;

import me.login.Login;
import me.login.discord.moderation.*;
import me.login.misc.rank.RankManager;
import me.login.moderation.ModerationModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordLinking extends ListenerAdapter {

    private final Login plugin;
    private JDA jda;
    private final DiscordModConfig modConfig;
    private final DiscordLinkLogger logger;
    private final RankManager rankManager;
    private final Component prefix;

    private final Map<String, UUID> verificationCodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<Long, UUID> reverseLinkedAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> codeCooldowns = new ConcurrentHashMap<>();

    public DiscordLinking(Login plugin, DiscordModConfig modConfig, DiscordLinkLogger logger, RankManager rankManager) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.logger = logger;
        this.rankManager = rankManager;

        String prefixStr = plugin.getConfig().getString("server_prefix");
        if (prefixStr == null || prefixStr.isEmpty()) {
            prefixStr = plugin.getConfig().getString("server_prefix_2", "&cError: Prefix not found. ");
        }

        if (prefixStr.contains("<")) {
            this.prefix = MiniMessage.miniMessage().deserialize(prefixStr);
        } else {
            this.prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(prefixStr);
        }
    }

    /**
     * Starts the Main Bot using 'bot-token'.
     * Passes ModerationModule to ensure Discord commands write to the SAME database as in-game commands.
     */
    public JDA startBot(String token, DiscordCommandLogger commandLogger, ModerationModule moderationModule) {
        try {
            JDABuilder builder = JDABuilder.createLight(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .disableCache(EnumSet.of(
                            CacheFlag.ACTIVITY,
                            CacheFlag.VOICE_STATE,
                            CacheFlag.EMOJI,
                            CacheFlag.STICKER,
                            CacheFlag.CLIENT_STATUS,
                            CacheFlag.ONLINE_STATUS,
                            CacheFlag.SCHEDULED_EVENTS
                    ))
                    .addEventListeners(
                            this, // Linking Logic
                            new DiscordCommandManager(plugin, commandLogger), // Utility commands
                            // Critical: Pass ModerationModule here for shared DB access
                            new DiscordModCommands(plugin, modConfig, commandLogger, this, moderationModule),
                            new DiscordRankCommand(plugin, rankManager)
                    );

            jda = builder.build().awaitReady();
            plugin.getLogger().info("Main Discord Bot connected!");

            // Load existing links into memory
            plugin.getDiscordLinkDatabase().loadAllLinks().forEach((discordId, uuid) -> {
                linkedAccounts.put(uuid, discordId);
                reverseLinkedAccounts.put(discordId, uuid);
            });
            plugin.getLogger().info("Loaded " + linkedAccounts.size() + " linked accounts.");

            return jda;
        } catch (Exception e) {
            plugin.getLogger().severe("Error during Main Discord Bot startup: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    public void shutdown() {
        if (jda != null) jda.shutdownNow();
    }

    public JDA getJDA() { return jda; }

    public DiscordLinkLogger getLogger() {
        return this.logger;
    }

    // --- Linking Logic ---

    public String generateCode(UUID uuid, String playerName) {
        Random random = new Random();
        String code;
        do { code = String.format("%04d", random.nextInt(10000)); } while (verificationCodes.containsKey(code));
        final String finalCode = code;

        verificationCodes.put(finalCode, uuid);
        codeCooldowns.put(uuid, System.currentTimeMillis());
        long expirySeconds = plugin.getConfig().getLong("code-expiry-seconds", 60);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            verificationCodes.remove(finalCode, uuid);
            codeCooldowns.remove(uuid);
        }, 20L * expirySeconds);

        return code;
    }

    public boolean hasActiveCode(UUID uuid) { return codeCooldowns.containsKey(uuid); }
    public UUID getPlayerByCode(String code) { return verificationCodes.get(code); }
    public Long getLinkedDiscordId(UUID uuid) { return linkedAccounts.get(uuid); }
    public UUID getLinkedUuid(long discordId) { return reverseLinkedAccounts.get(discordId); }

    public List<Role> linkUser(UUID uuid, long discordId, Member member) {
        List<Role> assignedRoles = new ArrayList<>();

        // 1. Save to DB
        plugin.getDiscordLinkDatabase().linkUser(discordId, uuid);
        linkedAccounts.put(uuid, discordId);
        reverseLinkedAccounts.put(discordId, uuid);

        // 2. Assign Basic Roles
        Guild guild = member.getGuild();
        long verifiedRoleId = plugin.getConfig().getLong("verified-role-id");
        long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
        Role verifiedRole = guild.getRoleById(verifiedRoleId);
        Role unverifiedRole = guild.getRoleById(unverifiedRoleId);

        if (verifiedRole != null) {
            guild.addRoleToMember(member, verifiedRole).queue(s -> assignedRoles.add(verifiedRole));
        }
        if (unverifiedRole != null) {
            guild.removeRoleFromMember(member, unverifiedRole).queue();
        }

        // 3. Sync Ranks based on Permissions
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                // Get the section 'rank_role_id' from config
                ConfigurationSection rolesSection = plugin.getConfig().getConfigurationSection("rank_role_id");

                if (rolesSection != null) {
                    for (String rankKey : rolesSection.getKeys(false)) {
                        // Check if player has "rank.<key>" (e.g. rank.ace)
                        if (player.hasPermission("rank." + rankKey)) {
                            long roleId = rolesSection.getLong(rankKey);
                            Role rankRole = guild.getRoleById(roleId);

                            if (rankRole != null) {
                                guild.addRoleToMember(member, rankRole).queue(
                                        s -> assignedRoles.add(rankRole),
                                        e -> plugin.getLogger().warning("Failed to add rank role for " + rankKey + ": " + e.getMessage())
                                );
                            } else {
                                plugin.getLogger().warning("Rank role ID for '" + rankKey + "' is invalid in config.");
                            }
                        }
                    }
                }
            }
        });

        // 4. Update Nickname & Log
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";

                member.modifyNickname(mcName).queue(
                        null,
                        error -> plugin.getLogger().warning("Could not set nickname for " + member.getUser().getAsTag() + ": " + error.getMessage())
                );

                logger.sendLog("✅ **" + member.getUser().getAsTag() + "** linked to **" + mcName + "** (UUID: `" + uuid + "`)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        return assignedRoles;
    }

    public void unlinkUser(long discordId) {
        plugin.getDiscordLinkDatabase().unlinkUser(discordId);
        UUID uuid = reverseLinkedAccounts.remove(discordId);
        if (uuid != null) linkedAccounts.remove(uuid);
    }

    // --- JDA Event Listeners ---

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
        Role unverifiedRole = event.getGuild().getRoleById(unverifiedRoleId);
        if (unverifiedRole != null) {
            event.getGuild().addRoleToMember(event.getMember(), unverifiedRole).queue();
        }
    }

    @Override
    public void onGuildMemberRemove(GuildMemberRemoveEvent event) {
        long discordId = event.getUser().getIdLong();
        UUID uuid = reverseLinkedAccounts.get(discordId);
        if (uuid != null) {
            unlinkUser(discordId);
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "UnknownPlayer";
                logger.sendLog("❌ **" + event.getUser().getAsTag() + "** left Discord. Link with **" + mcName + "** removed.");
            });
        }
    }

    @Override
    public void onMessageReceived(MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        long verificationChannelId = plugin.getConfig().getLong("verification-channel-id");
        if (!(event.getChannel() instanceof GuildMessageChannel channel) || channel.getIdLong() != verificationChannelId) return;

        event.getMessage().delete().queue();

        String code = event.getMessage().getContentRaw().trim();
        Member member = event.getMember();

        UUID playerUUID = verificationCodes.remove(code);

        if (playerUUID == null || member == null) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED).setTitle("❌ Verification Failed")
                    .setDescription(event.getAuthor().getAsMention() + ", code `" + code + "` invalid/expired.")
                    .addField("Action Required", "Use `/discord link` in-game for a new code.", false);
            channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            return;
        }

        codeCooldowns.remove(playerUUID);
        long discordId = member.getIdLong();

        if (linkedAccounts.containsKey(playerUUID)) {
            sendError(channel, event.getAuthor(), "MC account already linked!");
            return;
        }
        if (reverseLinkedAccounts.containsKey(discordId)) {
            sendError(channel, event.getAuthor(), "Discord account already linked!");
            return;
        }

        // Perform Link
        List<Role> intendedRoles = linkUser(playerUUID, discordId, member);

        // Success Logic
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
            String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";

            // DM User
            EmbedBuilder dmEmbed = new EmbedBuilder()
                    .setColor(Color.GREEN)
                    .setTitle("✅ Account Linked!")
                    .setDescription("Your Discord account has been successfully linked to **" + mcName + "**.")
                    .setFooter(member.getGuild().getName(), member.getGuild().getIconUrl());
            sendPrivateEmbed(member.getUser(), dmEmbed.build());

            // Public Confirm
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.GREEN).setTitle("✅ Verification Successful!")
                        .setDescription(member.getAsMention() + " linked to **" + mcName + "**.");

                member.getGuild().retrieveMember(member.getUser()).queue(freshMember -> {
                    String rolesString = freshMember.getRoles().stream()
                            .filter(intendedRoles::contains)
                            .map(Role::getName)
                            .collect(Collectors.joining(", "));
                    if (!rolesString.isEmpty()) eb.addField("Roles Updated", "`" + rolesString + "`", false);
                    else eb.addField("Roles Updated", "Synced (Check Profile)", false);
                    eb.setFooter("Welcome!");
                    channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
                });
            }, 20L);
        });

        // In-Game Message
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage(prefix.append(Component.text("SUCCESS! ", NamedTextColor.GREEN, TextDecoration.BOLD))
                        .append(Component.text("Discord linked to " + member.getUser().getAsTag() + "!", NamedTextColor.GREEN)));
            }
        });
    }

    private void sendError(GuildMessageChannel channel, User author, String reason) {
        EmbedBuilder eb = new EmbedBuilder().setColor(Color.ORANGE).setTitle("⚠️ Verification Issue")
                .setDescription(author.getAsMention() + ", " + reason);
        channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
    }

    public void sendPrivateEmbed(User user, MessageEmbed embed) {
        user.openPrivateChannel().queue(c -> c.sendMessageEmbeds(embed).queue(null, e -> {}), e -> {});
    }
}