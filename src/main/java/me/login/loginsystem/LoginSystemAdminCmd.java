package me.login.loginsystem;

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase;
import net.dv8tion.jda.api.EmbedBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.mindrot.jbcrypt.BCrypt;

import java.awt.Color;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class LoginSystemAdminCmd implements CommandExecutor {

    private final Login plugin;
    private final LoginSystem loginSystem;
    private final LoginDatabase loginDb;
    private final DiscordLinkDatabase discordLinkDb;
    private final LoginSystemLogger logger;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#_.%&*";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NEW_PASSWORD_LENGTH = 8;

    public LoginSystemAdminCmd(Login plugin, LoginSystem loginSystem, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb, LoginSystemLogger logger) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
        this.loginDb = loginDb;
        this.discordLinkDb = discordLinkDb;
        this.logger = logger;
        this.serverPrefix = loginSystem.getServerPrefix();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        switch (cmdName) {
            case "unregister":
                return handleUnregister(sender, args);
            case "loginhistory":
                return handleLoginHistory(sender, args);
            case "checkalt":
                return handleCheckAlt(sender, args);
            case "adminchangepass":
                return handleAdminChangePass(sender, args);
            default:
                return false;
        }
    }

    private boolean handleUnregister(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.admin")) {
            sender.sendMessage(serverPrefix.append(Component.text("No permission!", NamedTextColor.RED)));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /unregister <player></red>")));
            return true;
        }
        runAdminTask(sender, args[0], (targetUUID, targetName) -> {
            if (loginDb.unregisterPlayer(targetUUID)) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Unregistered " + targetName + "</green>")));
                logger.logAdmin("Admin: " + sender.getName() + " unregistered " + targetName);
                Player targetPlayer = Bukkit.getPlayer(targetUUID);
                if (targetPlayer != null) {
                    plugin.getServer().getScheduler().runTask(plugin, () ->
                            targetPlayer.kick(mm.deserialize("<red>Your account has been unregistered by an admin.</red>"))
                    );
                }
            } else {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player " + targetName + " is not registered.</red>")));
            }
        });
        return true;
    }

    private boolean handleLoginHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.loginhistory")) {
            sender.sendMessage(serverPrefix.append(Component.text("No permission!", NamedTextColor.RED)));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /loginhistory <player></red>")));
            return true;
        }
        runAdminTask(sender, args[0], (targetUUID, targetName) -> {
            LoginDatabase.PlayerAuthData data = loginDb.getAuthData(targetUUID);
            if (data == null) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>No login data for " + targetName + "</red>")));
                return;
            }
            String lastLogin = data.lastLoginTimestamp() == 0 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.lastLoginTimestamp()));
            sender.sendMessage(mm.deserialize("<yellow>--- History for " + targetName + " ---</yellow>"));
            sender.sendMessage(mm.deserialize("<gray>UUID: <white>" + data.uuid() + "</white>"));
            sender.sendMessage(mm.deserialize("<gray>Reg IP: <white>" + (data.registrationIp() != null ? data.registrationIp() : "N/A") + "</white>"));
            sender.sendMessage(mm.deserialize("<gray>Last IP: <white>" + (data.lastLoginIp() != null ? data.lastLoginIp() : "N/A") + "</white>"));
            sender.sendMessage(mm.deserialize("<gray>Last Login: <white>" + lastLogin + "</white>"));

            String history = String.format("History for %s (UUID: %s)\nReg IP: %s\nLast IP: %s\nLast Login: %s\nHashed Pass: %s",
                    targetName, data.uuid(), (data.registrationIp() != null ? data.registrationIp() : "N/A"),
                    (data.lastLoginIp() != null ? data.lastLoginIp() : "N/A"), lastLogin, data.hashedPassword());
            logger.logAdmin("Admin: " + sender.getName() + " checked history for " + targetName + ".\n```\n" + history + "\n```");
        });
        return true;
    }

    private boolean handleCheckAlt(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.checkalt")) {
            sender.sendMessage(serverPrefix.append(Component.text("No permission!", NamedTextColor.RED)));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /checkalt <player></red>")));
            return true;
        }
        runAdminTask(sender, args[0], (targetUUID, targetName) -> {
            LoginDatabase.PlayerAuthData data = loginDb.getAuthData(targetUUID);
            if (data == null || (data.registrationIp() == null && data.lastLoginIp() == null)) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>No IP data for " + targetName + "</red>")));
                return;
            }
            String regIp = (data.registrationIp() != null ? data.registrationIp() : "N/A");
            String lastIp = (data.lastLoginIp() != null ? data.lastLoginIp() : "N/A");
            List<LoginDatabase.PlayerAuthData> regAlts = !regIp.equals("N/A") ? loginDb.getPlayersByIp(regIp) : new ArrayList<>();
            List<LoginDatabase.PlayerAuthData> loginAlts = !lastIp.equals("N/A") ? loginDb.getPlayersByIp(lastIp) : new ArrayList<>();
            Set<String> allAltNames = new HashSet<>();
            regAlts.forEach(alt -> {
                String name = Bukkit.getOfflinePlayer(UUID.fromString(alt.uuid())).getName();
                if (name != null) allAltNames.add(name);
            });
            loginAlts.forEach(alt -> {
                String name = Bukkit.getOfflinePlayer(UUID.fromString(alt.uuid())).getName();
                if (name != null) allAltNames.add(name);
            });
            sender.sendMessage(mm.deserialize("<yellow>--- Alts for " + targetName + " ---</yellow>"));
            sender.sendMessage(mm.deserialize("<gray>(Reg IP: " + regIp + " | Last IP: " + lastIp + ")</gray>"));
            if (allAltNames.isEmpty() || (allAltNames.size() == 1 && allAltNames.contains(targetName))) {
                sender.sendMessage(Component.text("No other accounts found.", NamedTextColor.WHITE));
                logger.logAdmin("Admin: " + sender.getName() + " checked alts for " + targetName + " (IPs: " + regIp + "/" + lastIp + "). None found.");
            } else {
                String altsString = String.join(", ", allAltNames);
                sender.sendMessage(Component.text(altsString, NamedTextColor.WHITE));
                logger.logAdmin("Admin: " + sender.getName() + " checked alts for " + targetName + " (IPs: " + regIp + "/" + lastIp + "). Found: " + altsString);
            }
        });
        return true;
    }

    private boolean handleAdminChangePass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.adminchangepass")) {
            sender.sendMessage(serverPrefix.append(Component.text("No permission!", NamedTextColor.RED)));
            return true;
        }
        if (args.length != 1) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /adminchangepass <player></red>")));
            return true;
        }

        runAdminTask(sender, args[0], (targetUUID, targetName) -> {
            if (!loginDb.isRegistered(targetUUID)) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player " + targetName + " not registered.</red>")));
                return;
            }

            String newPassword = generateRandomPassword();
            String newHashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

            loginDb.updatePassword(targetUUID, newHashedPassword);

            Long discordId = discordLinkDb.getLinkedDiscordId(targetUUID);
            if (discordId != null && plugin.getDiscordLinking() != null && plugin.getDiscordLinking().getJDA() != null) {
                plugin.getDiscordLinking().getJDA().retrieveUserById(discordId).queue(user -> {
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Color.ORANGE)
                            .setTitle("🔒 Admin Password Change")
                            .setDescription("An admin has changed your in-game password.")
                            .addField("New Password", "`" + newPassword + "`", false)
                            .setFooter("Please log in with this new password.");

                    user.openPrivateChannel().flatMap(channel ->
                            channel.sendMessageEmbeds(eb.build())
                    ).queue(
                            success -> {
                                sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Password reset for " + targetName + ". Embed DM sent.</green>")));
                                logger.logAdmin("Admin: " + sender.getName() + " changed pass for " + targetName + ". New Pass: ||" + newPassword + "||");
                            },
                            failure -> {
                                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Password updated, but FAILED to send DM: " + failure.getMessage() + "</red>")));
                                logger.logAdmin("Admin: " + sender.getName() + " changed pass for " + targetName + " BUT FAILED DM. New Pass: ||" + newPassword + "|| Error: " + failure.getMessage());
                            }
                    );
                }, failure -> {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Password updated, but couldn't find Discord user: " + failure.getMessage() + "</red>")));
                    logger.logAdmin("Admin: " + sender.getName() + " changed pass for " + targetName + " BUT COULD NOT FIND USER. New Pass: ||" + newPassword + "|| Error: " + failure.getMessage());
                });
            } else {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Password updated for " + targetName + ".</green>")));
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Could not send DM: Player is not linked to Discord.</red>")));
                logger.logAdmin("Admin: " + sender.getName() + " changed pass for " + targetName + " (unlinked). New Pass: ||" + newPassword + "||");
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(targetUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    loginSystem.unloggedInPlayers.add(targetUUID);

                    Component kickComponent = mm.deserialize(
                            "<yellow><bold>AlphaMc</bold>\n\n" +
                                    "<yellow>Your password was changed by an admin.\n" +
                                    "<green>Your new password (if linked) is in your Discord DMs.\n" +
                                    "<white>Please rejoin and log in."
                    );
                    onlinePlayer.kick(kickComponent);

                    sender.sendMessage(serverPrefix.append(mm.deserialize("<aqua>Player was online and has been kicked.</aqua>")));
                }
            });
        });
        return true;
    }

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(NEW_PASSWORD_LENGTH);
        for (int i = 0; i < NEW_PASSWORD_LENGTH; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }

    private void runAdminTask(CommandSender sender, String playerName, AdminTask task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Player '" + playerName + "' not found.</red>")));
                return;
            }
            task.run(target.getUniqueId(), target.getName());
        });
    }

    @FunctionalInterface
    private interface AdminTask {
        void run(UUID targetUUID, String targetName);
    }
}