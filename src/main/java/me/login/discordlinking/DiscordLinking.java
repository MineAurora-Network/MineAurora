package me.login.discordlinking; // Correct package

import club.minnced.discord.webhook.WebhookClient;
import me.login.Login; // Import from base package
// --- FIX: ADD IMPORTS ---
import me.login.discordcommand.DiscordCommandManager;
import me.login.discordcommand.DiscordCommandRegistrar;
import me.login.discordcommand.DiscordModConfig;
import me.login.discordcommand.DiscordModCommands;
import me.login.discordcommand.DiscordRankCommand;
// --- END IMPORTS ---
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.*;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.guild.member.GuildMemberJoinEvent;
import net.dv8tion.jda.api.events.guild.member.GuildMemberRemoveEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag; // <-- IMPORT ADDED
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumSet; // <-- IMPORT ADDED
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DiscordLinking extends ListenerAdapter {

    private final Login plugin;
    private JDA jda;
    private final WebhookClient logWebhook;
    private final DiscordModConfig modConfig; // <-- FIX: ADDED FIELD

    private final Map<String, UUID> verificationCodes = new ConcurrentHashMap<>();
    private final Map<UUID, Long> linkedAccounts = new ConcurrentHashMap<>();
    private final Map<Long, UUID> reverseLinkedAccounts = new ConcurrentHashMap<>();
    private final Map<UUID, Long> codeCooldowns = new ConcurrentHashMap<>();

    // --- FIX: UPDATED CONSTRUCTOR ---
    public DiscordLinking(Login plugin, WebhookClient logWebhook, DiscordModConfig modConfig) {
        this.plugin = plugin;
        this.logWebhook = logWebhook;
        this.modConfig = modConfig; // <-- Store this
    }
    // --- END FIX ---

    public JDA getJDA() { return jda; }

    public void sendLogMessage(String content) {
        plugin.getLogger().info("[DiscordLink Log] " + ChatColor.stripColor(content.replace("`", "").replace("*", "")));
        if (logWebhook != null) {
            logWebhook.send(content).exceptionally(error -> {
                plugin.getLogger().warning("Failed send link webhook: " + error.getMessage());
                return null;
            });
        }
    }

    // --- **FIX: MODIFIED STARTBOT METHOD** ---
    public void startBot(String token) {
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
                            this,
                            new DiscordCommandManager(plugin),
                            new DiscordModCommands(plugin, modConfig),
                            new DiscordRankCommand(plugin)
                    );

            jda = builder.build().awaitReady();
            plugin.getLogger().info("Discord bot connected successfully and ready!");

            plugin.getDatabase().loadAllLinks().forEach((discordId, uuid) -> {
                linkedAccounts.put(uuid, discordId);
                reverseLinkedAccounts.put(discordId, uuid);
            });
            plugin.getLogger().info("Loaded " + linkedAccounts.size() + " links.");
        } catch (Exception e) {
            plugin.getLogger().severe("Error during JDA startup: " + e.getMessage());
            e.printStackTrace();
            plugin.getServer().getPluginManager().disablePlugin(plugin);
        }
    }

    // --- **END OF FIX** ---

    public void shutdown() { if (jda != null) jda.shutdownNow(); }

    public String generateCode(UUID uuid, String playerName) {
        Random random = new Random(); String code;
        do { code = String.format("%04d", random.nextInt(10000)); } while (verificationCodes.containsKey(code));
        final String finalCode = code;

        verificationCodes.put(finalCode, uuid);
        codeCooldowns.put(uuid, System.currentTimeMillis());
        long expirySeconds = plugin.getConfig().getLong("code-expiry-seconds", 60);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            boolean removedCode = verificationCodes.remove(finalCode, uuid);
            boolean removedCooldown = codeCooldowns.remove(uuid) != null;
            if (removedCode || removedCooldown) {
                plugin.getLogger().info("[DEBUG] Expired code '" + finalCode + "' for " + playerName + " (RCd: " + removedCode + ", RCld: " + removedCooldown + ")");
            }
        }, 20L * expirySeconds);

        return code;
    }

    public boolean hasActiveCode(UUID uuid) { return codeCooldowns.containsKey(uuid); }
    public UUID getPlayerByCode(String code) { return verificationCodes.get(code); }
    public Long getLinkedDiscordId(UUID uuid) { return linkedAccounts.get(uuid); }

    public UUID getLinkedUuid(long discordId) {
        return reverseLinkedAccounts.get(discordId);
    }

    public List<Role> linkUser(UUID uuid, long discordId, Member member) {
        List<Role> assignedRoles = new ArrayList<>();

        plugin.getDatabase().linkUser(discordId, uuid); // Use correct DB instance
        linkedAccounts.put(uuid, discordId);
        reverseLinkedAccounts.put(discordId, uuid);

        Guild guild = member.getGuild();
        long verifiedRoleId = plugin.getConfig().getLong("verified-role-id");
        long unverifiedRoleId = plugin.getConfig().getLong("unverified-role-id");
        Role verifiedRole = guild.getRoleById(verifiedRoleId);
        Role unverifiedRole = guild.getRoleById(unverifiedRoleId);

        if (verifiedRole != null) {
            guild.addRoleToMember(member, verifiedRole).queue(s -> assignedRoles.add(verifiedRole), e -> {});
        } else { plugin.getLogger().warning("Verified Role ID invalid!"); }
        if (unverifiedRole != null) {
            guild.removeRoleFromMember(member, unverifiedRole).queue();
        } else { plugin.getLogger().warning("Unverified Role ID invalid!"); }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                ConfigurationSection ranksSection = plugin.getConfig().getConfigurationSection("ranks");
                if (ranksSection != null) {
                    for (String rankName : ranksSection.getKeys(false)) {
                        long roleId = ranksSection.getLong(rankName);
                        if (player.hasPermission("rank." + rankName)) {
                            Role role = guild.getRoleById(roleId);
                            if (role != null) guild.addRoleToMember(member, role)
                                    .queue(s -> assignedRoles.add(role), e -> plugin.getLogger().warning("Failed add rank role " + rankName + ": " + e.getMessage()));
                        }
                    }
                }
            }
        });

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(uuid);
                String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";
                member.modifyNickname(mcName).queue((v) -> {}, (error) -> {
                    if (error instanceof HierarchyException) plugin.getLogger().warning("Failed set nick (Hierarchy): " + member.getUser().getName());
                    else plugin.getLogger().warning("Failed set nick: " + error.getMessage());
                });
                // Log moved here ensures mcName is resolved and includes tag
                sendLogMessage("✅ **" + member.getUser().getAsTag() + "** linked to **" + mcName + "** (UUID: `" + uuid + "`)");
            } catch (Exception e) { plugin.getLogger().severe("[DEBUG] Error in async linkUser name/log:"); e.printStackTrace(); }
        });
        return assignedRoles;
    }

    public void unlinkUser(long discordId) {
        plugin.getDatabase().unlinkUser(discordId); // Use correct DB instance
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
                sendLogMessage("❌ **" + event.getUser().getAsTag() + "** left Discord. Link with **" + mcName + "** removed.");
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

        // --- BUG FIX ---
        // Atomically remove the code. If it's null, it was invalid or already used.
        UUID playerUUID = verificationCodes.remove(code);

        if (playerUUID == null || member == null) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.RED).setTitle("❌ Verification Failed")
                    .setDescription(event.getAuthor().getAsMention() + ", code `" + code + "` invalid/expired.")
                    .addField("Action Required", "Use `/discord link` in-game for a new code.", false);
            channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            return;
        }

        // If we are here, the code was valid and has been removed.
        codeCooldowns.remove(playerUUID);

        long discordId = member.getIdLong();

        if (linkedAccounts.containsKey(playerUUID)) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.ORANGE).setTitle("⚠️ Verification Issue")
                    .setDescription(event.getAuthor().getAsMention() + ", MC account already linked!");
            channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            return;
        }
        if (reverseLinkedAccounts.containsKey(discordId)) {
            EmbedBuilder eb = new EmbedBuilder().setColor(Color.ORANGE).setTitle("⚠️ Verification Issue")
                    .setDescription(event.getAuthor().getAsMention() + ", Discord account already linked!");
            channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
            return;
        }

        List<Role> intendedRoles = linkUser(playerUUID, discordId, member);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = plugin.getServer().getOfflinePlayer(playerUUID);
            String mcName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Player";

            // --- NEW FEATURE: Send DM to User ---
            try {
                EmbedBuilder dmEmbed = new EmbedBuilder()
                        .setColor(Color.GREEN)
                        .setTitle("✅ Account Linked!")
                        .setDescription("Your Discord account has been successfully linked to the Minecraft account: **" + mcName + "**.")
                        .setFooter(member.getGuild().getName(), member.getGuild().getIconUrl());
                sendPrivateEmbed(member.getUser(), dmEmbed.build());
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to send private message during link: " + e.getMessage());
            }
            // --- END NEW FEATURE ---


            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                EmbedBuilder eb = new EmbedBuilder().setColor(Color.GREEN).setTitle("✅ Verification Successful!")
                        .setDescription(member.getAsMention() + " linked to **" + mcName + "**.");

                member.getGuild().retrieveMember(member.getUser()).queue(freshMember -> {
                    String rolesString = freshMember.getRoles().stream()
                            .filter(intendedRoles::contains)
                            .map(Role::getName)
                            .collect(Collectors.joining(", "));
                    if (!rolesString.isEmpty()) eb.addField("Roles Updated", "`" + rolesString + "`", false);
                    else eb.addField("Roles Updated", "`Verified` (check profile)", false);
                    eb.setFooter("Welcome!");
                    channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
                }, failure -> {
                    eb.addField("Roles Updated", "`Verified` (check profile)", false);
                    eb.setFooter("Welcome!");
                    channel.sendMessageEmbeds(eb.build()).queue(msg -> msg.delete().queueAfter(15, TimeUnit.SECONDS));
                });
            }, 20L);
        });

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = Bukkit.getPlayer(playerUUID);
            if (player != null && player.isOnline()) {
                player.sendMessage("§a§lSUCCESS! §aDiscord linked to " + member.getUser().getAsTag() + "!");
            }
        });
    }

    // These helpers remain
    private void sendPrivateMessage(User user, String message) { user.openPrivateChannel().queue( c -> c.sendMessage(message).queue(), e -> {}); }
    public void sendPrivateEmbed(User user, MessageEmbed embed) { user.openPrivateChannel().queue( c -> c.sendMessageEmbeds(embed).queue(), e -> {}); }
}