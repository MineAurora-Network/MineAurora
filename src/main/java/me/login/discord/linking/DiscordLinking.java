package me.login.discord.linking;

import me.login.Login;
import me.login.discord.moderation.DiscordCommandLogger;
import me.login.discord.moderation.DiscordCommandManager;
import me.login.discord.moderation.DiscordRankCommand;
import me.login.discord.moderation.discord.DiscordModConfig;
import me.login.discord.moderation.discord.DiscordModDatabase;
import me.login.discord.moderation.discord.DiscordStaffModCommands;
import me.login.discord.moderation.minecraft.MinecraftModCommands;
import me.login.misc.rank.RankModule;
import me.login.moderation.ModerationModule;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
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

public class DiscordLinking extends ListenerAdapter {

    private final Login plugin;
    private JDA jda;
    private final DiscordModConfig modConfig;
    private final DiscordModDatabase modDatabase;
    private final DiscordLinkLogger logger;
    private final RankModule rankModule;
    private final Component prefix;

    private final Map<String, UUID> verificationCodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<Long, UUID> reverseLinkedAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> codeCooldowns = new ConcurrentHashMap<>();

    public DiscordLinking(Login plugin, DiscordModConfig modConfig, DiscordModDatabase modDatabase, DiscordLinkLogger logger, RankModule rankModule, ModerationModule moderationModule) {
        this.plugin = plugin;
        this.modConfig = modConfig;
        this.modDatabase = modDatabase;
        this.logger = logger;
        this.rankModule = rankModule;

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

    public JDA startBot(String token, DiscordCommandLogger commandLogger, ModerationModule moderationModule, RankModule rankModule, DiscordModDatabase modDatabase) {
        try {
            JDABuilder builder = JDABuilder.createLight(token)
                    .enableIntents(GatewayIntent.GUILD_MEMBERS, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .disableCache(EnumSet.of(CacheFlag.ACTIVITY, CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.CLIENT_STATUS, CacheFlag.ONLINE_STATUS, CacheFlag.SCHEDULED_EVENTS))
                    .addEventListeners(
                            this,
                            new DiscordCommandManager(plugin, commandLogger),
                            new MinecraftModCommands(plugin, modConfig, commandLogger, this, moderationModule),
                            new DiscordStaffModCommands(plugin, modConfig, modDatabase, commandLogger, this, rankModule),
                            new DiscordRankCommand(plugin, rankModule.getManager())
                    );

            jda = builder.build().awaitReady();
            plugin.getLogger().info("Main Discord Bot connected!");

            checkAndSendVerificationPanel();

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

    // New Method: Check verification channel and send embed if empty
    private void checkAndSendVerificationPanel() {
        long channelId = plugin.getConfig().getLong("verification-channel-id");
        if (channelId == 0) return;

        TextChannel channel = jda.getTextChannelById(channelId);
        if (channel != null) {
            channel.getHistory().retrievePast(1).queue(messages -> {
                if (messages.isEmpty()) {
                    EmbedBuilder eb = new EmbedBuilder();
                    eb.setTitle("Sync your Minecraft account with Discord!");
                    eb.setDescription("To protect your account from possible hacks and subsequent crashes or other harm to our server, you need to link your account. It's not difficult at all.\n\n" +
                            "On our Minecraft server you need to enter the `/discord link` command. If you decide to unlink your account, just type `/unlink`.\n\n" +
                            "Your rank on the server will be shown on Discord. Also, in case you lose access to your account, our Staff will be able to restore it to you.\n\n" +
                            "😉 **After account link, enter here the code that you received in the chat.**");
                    eb.setColor(Color.decode("#2f3136")); // Dark Discord theme
                    channel.sendMessageEmbeds(eb.build()).queue();
                }
            });
        }
    }

    public void shutdown() {
        if (jda != null) jda.shutdownNow();
    }

    public JDA getJDA() { return jda; }
    public DiscordLinkLogger getLogger() { return this.logger; }

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
        plugin.getDiscordLinkDatabase().linkUser(discordId, uuid);
        linkedAccounts.put(uuid, discordId);
        reverseLinkedAccounts.put(discordId, uuid);

        Guild guild = member.getGuild();
        long verifiedRoleId = plugin.getConfig().getLong("verified-role-id");
        long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
        Role verifiedRole = guild.getRoleById(verifiedRoleId);
        Role unverifiedRole = guild.getRoleById(unverifiedRoleId);

        if (verifiedRole != null) guild.addRoleToMember(member, verifiedRole).queue(s -> assignedRoles.add(verifiedRole));
        if (unverifiedRole != null) guild.removeRoleFromMember(member, unverifiedRole).queue();

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ConfigurationSection rolesSection = plugin.getConfig().getConfigurationSection("rank_role_id");
                if (rolesSection != null) {
                    for (String rankKey : rolesSection.getKeys(false)) {
                        if (player.hasPermission("rank." + rankKey)) {
                            long roleId = rolesSection.getLong(rankKey);
                            Role rankRole = guild.getRoleById(roleId);
                            if (rankRole != null) guild.addRoleToMember(member, rankRole).queue(s -> assignedRoles.add(rankRole));
                        }
                    }
                }
            }
        });

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";
                member.modifyNickname(mcName).queue(null, error -> {});
                logger.sendLog("✅ **" + member.getUser().getAsTag() + "** linked to **" + mcName + "** (UUID: `" + uuid + "`)");
            } catch (Exception e) {}
        });
        return assignedRoles;
    }

    public void unlinkUser(long discordId) {
        plugin.getDiscordLinkDatabase().unlinkUser(discordId);
        UUID uuid = reverseLinkedAccounts.remove(discordId);
        if (uuid != null) linkedAccounts.remove(uuid);
    }

    @Override
    public void onGuildMemberJoin(GuildMemberJoinEvent event) {
        long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
        Role unverifiedRole = event.getGuild().getRoleById(unverifiedRoleId);
        if (unverifiedRole != null) event.getGuild().addRoleToMember(event.getMember(), unverifiedRole).queue();
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
                    .setDescription(event.getAuthor().getAsMention() + ", code `" + code + "` invalid/expired.");
            channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            return;
        }

        codeCooldowns.remove(playerUUID);
        long discordId = member.getIdLong();

        if (linkedAccounts.containsKey(playerUUID) || reverseLinkedAccounts.containsKey(discordId)) {
            sendError(channel, event.getAuthor(), "Account already linked!");
            return;
        }

        List<Role> intendedRoles = linkUser(playerUUID, discordId, member);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
            String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";

            EmbedBuilder dmEmbed = new EmbedBuilder().setColor(Color.GREEN).setTitle("✅ Account Linked!")
                    .setDescription("Your Discord account has been successfully linked to **" + mcName + "**.")
                    .setFooter(member.getGuild().getName(), member.getGuild().getIconUrl());
            sendPrivateEmbed(member.getUser(), dmEmbed.build());

            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.GREEN).setTitle("✅ Verification Successful!")
                        .setDescription(member.getAsMention() + " linked to **" + mcName + "**.");
                channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            }, 20L);
        });

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