package me.login.loginsystem; // Correct package

import me.login.Login; // Import base plugin class
import me.login.discordlinking.DiscordLinkDatabase; // Import renamed class from other package
// LoginSystem and LoginDatabase are imported implicitly from same package

// --- ADDED IMPORTS ---
import net.dv8tion.jda.api.EmbedBuilder;
import java.awt.Color;
// --- END ADDED IMPORTS ---

import net.dv8tion.jda.api.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.*;

public class LoginSystemCmd implements CommandExecutor {

    private final Login plugin;
    private final LoginSystem loginSystem;
    private final LoginDatabase loginDb;
    private final DiscordLinkDatabase discordLinkDb; // Use renamed class/variable name

    private static final String PASSWORD_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#_.%&*";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int NEW_PASSWORD_LENGTH = 8;

    // Constructor updated for renamed class
    public LoginSystemCmd(Login plugin, LoginSystem loginSystem, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
        this.loginDb = loginDb;
        this.discordLinkDb = discordLinkDb; // Use renamed variable
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        // Player Commands
        if (sender instanceof Player player) {
            UUID uuid = player.getUniqueId();
            String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "UNKNOWN";

            switch (cmdName) {
                case "register":
                    if (!loginSystem.isUnloggedIn(uuid)) { loginSystem.sendPrefixedMessage(player, "§cAlready logged in."); return true; } // UPDATED
                    if (args.length != 2) { loginSystem.sendPrefixedMessage(player, "§cUsage: /register <pass> <confirm>"); return true; } // UPDATED
                    loginSystem.handleRegister(player, args[0], args[1], ip); return true;

                case "login":
                    if (!loginSystem.isUnloggedIn(uuid)) { loginSystem.sendPrefixedMessage(player, "§cAlready logged in."); return true; } // UPDATED
                    if (args.length != 1) { loginSystem.sendPrefixedMessage(player, "§cUsage: /login <pass>"); return true; } // UPDATED
                    loginSystem.handleLogin(player, args[0], ip); return true;

                case "changepassword":
                    if (loginSystem.isUnloggedIn(uuid)) { loginSystem.sendPrefixedMessage(player, "§cMust be logged in."); return true; } // UPDATED
                    if (args.length != 2) { loginSystem.sendPrefixedMessage(player, "§cUsage: /changepassword <old> <new>"); return true; } // UPDATED
                    loginSystem.handleChangePassword(player, args[0], args[1]); return true;
            }
        }

        // Admin Commands
        switch (cmdName) {
            case "unregister":        return handleUnregister(sender, args);
            case "loginhistory":      return handleLoginHistory(sender, args);
            case "checkalt":          return handleCheckAlt(sender, args);
            case "adminchangepass":   return handleAdminChangePass(sender, args);
            default:                  return false;
        }
    }

    // --- Admin Command Handlers ---

    private boolean handleUnregister(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.admin")) { sender.sendMessage("§cNo permission!"); return true; } if (args.length != 1) { sender.sendMessage("§cUsage: /unregister <player>"); return true; } runAdminTask(sender, args[0], (targetUUID, targetName) -> { if (loginDb.unregisterPlayer(targetUUID)) { sender.sendMessage("§aUnregistered " + targetName); loginSystem.sendLog("Admin: " + sender.getName() + " unregistered " + targetName); Player targetPlayer = Bukkit.getPlayer(targetUUID); if (targetPlayer != null) plugin.getServer().getScheduler().runTask(plugin, () -> targetPlayer.kickPlayer("§cAccount unregistered.")); } else { sender.sendMessage("§cPlayer " + targetName + " not registered."); } }); return true;
    }

    private boolean handleLoginHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.loginhistory")) { sender.sendMessage("§cNo permission!"); return true; } if (args.length != 1) { sender.sendMessage("§cUsage: /loginhistory <player>"); return true; } runAdminTask(sender, args[0], (targetUUID, targetName) -> { LoginDatabase.PlayerAuthData data = loginDb.getAuthData(targetUUID); if (data == null) { sender.sendMessage("§cNo login data for " + targetName); return; } String lastLogin = data.lastLoginTimestamp() == 0 ? "Never" : new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(data.lastLoginTimestamp())); sender.sendMessage("§e--- History for " + targetName + " ---"); sender.sendMessage("§7UUID: §f" + data.uuid()); sender.sendMessage("§7Reg IP: §f" + (data.registrationIp() != null ? data.registrationIp() : "N/A")); sender.sendMessage("§7Last IP: §f" + (data.lastLoginIp() != null ? data.lastLoginIp() : "N/A")); sender.sendMessage("§7Last Login: §f" + lastLogin); String history = String.format("History for %s (UUID: %s)\nReg IP: %s\nLast IP: %s\nLast Login: %s\nHashed Pass: %s", targetName, data.uuid(), (data.registrationIp()!=null?data.registrationIp():"N/A"), (data.lastLoginIp()!=null?data.lastLoginIp():"N/A"), lastLogin, data.hashedPassword()); loginSystem.sendLog("Admin: " + sender.getName() + " checked history for " + targetName + ".\n```\n" + history + "\n```"); }); return true;
    }

    private boolean handleCheckAlt(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.checkalt")) { sender.sendMessage("§cNo permission!"); return true; } if (args.length != 1) { sender.sendMessage("§cUsage: /checkalt <player>"); return true; } runAdminTask(sender, args[0], (targetUUID, targetName) -> { LoginDatabase.PlayerAuthData data = loginDb.getAuthData(targetUUID); if (data == null || (data.registrationIp() == null && data.lastLoginIp() == null)) { sender.sendMessage("§cNo IP data for " + targetName); return; } String regIp = (data.registrationIp() != null ? data.registrationIp() : "N/A"); String lastIp = (data.lastLoginIp() != null ? data.lastLoginIp() : "N/A"); List<LoginDatabase.PlayerAuthData> regAlts = !regIp.equals("N/A") ? loginDb.getPlayersByIp(regIp) : new ArrayList<>(); List<LoginDatabase.PlayerAuthData> loginAlts = !lastIp.equals("N/A") ? loginDb.getPlayersByIp(lastIp) : new ArrayList<>(); Set<String> allAltNames = new HashSet<>(); regAlts.forEach(alt -> { String name = Bukkit.getOfflinePlayer(UUID.fromString(alt.uuid())).getName(); if (name != null) allAltNames.add(name); }); loginAlts.forEach(alt -> { String name = Bukkit.getOfflinePlayer(UUID.fromString(alt.uuid())).getName(); if (name != null) allAltNames.add(name); }); sender.sendMessage("§e--- Alts for " + targetName + " ---"); sender.sendMessage("§7(Reg IP: " + regIp + " | Last IP: " + lastIp + ")"); if (allAltNames.isEmpty() || (allAltNames.size() == 1 && allAltNames.contains(targetName))) { sender.sendMessage("§fNo other accounts found."); loginSystem.sendLog("Admin: " + sender.getName() + " checked alts for " + targetName + " (IPs: " + regIp + "/" + lastIp + "). None found."); } else { String altsString = String.join(", ", allAltNames); sender.sendMessage("§f" + altsString); loginSystem.sendLog("Admin: " + sender.getName() + " checked alts for " + targetName + " (IPs: " + regIp + "/" + lastIp + "). Found: " + altsString); } }); return true;
    }

    // --- MODIFIED METHOD ---
    private boolean handleAdminChangePass(CommandSender sender, String[] args) {
        if (!sender.hasPermission("logincore.adminchangepass")) { sender.sendMessage("§cNo permission!"); return true; }
        if (args.length != 1) { sender.sendMessage("§cUsage: /adminchangepass <player>"); return true; }

        runAdminTask(sender, args[0], (targetUUID, targetName) -> {
            if (!loginDb.isRegistered(targetUUID)) {
                sender.sendMessage("§cPlayer " + targetName + " not registered.");
                return;
            }

            // 1. Generate new password and hash
            String newPassword = generateRandomPassword();
            String newHashedPassword = BCrypt.hashpw(newPassword, BCrypt.gensalt());

            // 2. Update DB (async)
            loginDb.updatePassword(targetUUID, newHashedPassword);

            // 3. Try to send Discord DM (async)
            Long discordId = plugin.getDiscordLinking().getLinkedDiscordId(targetUUID); // Get from cache
            if (discordId != null && plugin.getDiscordLinking().getJDA() != null) {
                plugin.getDiscordLinking().getJDA().retrieveUserById(discordId).queue(user -> {
                    // Create the embed
                    EmbedBuilder eb = new EmbedBuilder()
                            .setColor(Color.ORANGE)
                            .setTitle("🔒 Admin Password Change")
                            .setDescription("An admin has changed your in-game password.")
                            .addField("New Password", "`" + newPassword + "`", false)
                            .setFooter("Please log in with this new password.");

                    // Send the embed
                    user.openPrivateChannel().flatMap(channel ->
                            channel.sendMessageEmbeds(eb.build())
                    ).queue(
                            success -> {
                                sender.sendMessage("§aPassword reset for " + targetName + ". Embed DM sent.");
                                loginSystem.sendLog("Admin: " + sender.getName() + " changed pass for " + targetName + ". New Pass: ||" + newPassword + "||");
                            },
                            failure -> {
                                sender.sendMessage("§cPassword updated, but FAILED to send DM: " + failure.getMessage());
                                // --- FIXED TYPO 'D' ---
                                loginSystem.sendLog("Admin: " + sender.getName() + " changed pass for " + targetName + " BUT FAILED DM. New Pass: ||" + newPassword + "|| Error: " + failure.getMessage());
                            }
                    );
                }, failure -> {
                    sender.sendMessage("§cPassword updated, but couldn't find Discord user: " + failure.getMessage());
                    loginSystem.sendLog("Admin: " + sender.getName() + " changed pass for " + targetName + " BUT COULD NOT FIND USER. New Pass: ||" + newPassword + "|| Error: " + failure.getMessage());
                });
            } else {
                // No discord link, notify admin
                sender.sendMessage("§aPassword updated for " + targetName + ".");
                sender.sendMessage("§cCould not send DM: Player is not linked to Discord.");
                // --- FIXED TYPO ---
                loginSystem.sendLog("Admin: " + sender.getName() + " changed pass for " + targetName + " (unlinked). New Pass: ||" + newPassword + "||");
            }

            // 4. Kick player if online (must be sync)
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                Player onlinePlayer = Bukkit.getPlayer(targetUUID);
                if (onlinePlayer != null && onlinePlayer.isOnline()) {
                    // Force them to the login screen on rejoin
                    loginSystem.unloggedInPlayers.add(targetUUID);

                    // Kick them
                    onlinePlayer.kickPlayer(ChatColor.YELLOW + "§bAlphaMc\n\n" +
                            ChatColor.YELLOW + "Your password was changed by an admin.\n" +
                            ChatColor.GREEN + "Your new password (if linked) is in your Discord DMs.\n" +
                            ChatColor.WHITE + "Please rejoin and log in.");

                    sender.sendMessage("§bPlayer was online and has been kicked.");
                }
            });
        });
        return true;
    }
    // --- END MODIFIED METHOD ---

    private String generateRandomPassword() {
        StringBuilder sb = new StringBuilder(NEW_PASSWORD_LENGTH);
        for (int i = 0; i < NEW_PASSWORD_LENGTH; i++) sb.append(PASSWORD_CHARS.charAt(RANDOM.nextInt(PASSWORD_CHARS.length())));
        return sb.toString();
    }

    private void runAdminTask(CommandSender sender, String playerName, AdminTask task) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            @SuppressWarnings("deprecation") // Use deprecation for broader compatibility
            OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
            if (target == null || !target.hasPlayedBefore()) { sender.sendMessage("§cPlayer '" + playerName + "' not found."); return; }
            // Use target.getName() to ensure correct capitalization
            task.run(target.getUniqueId(), target.getName());
        });
    }

    @FunctionalInterface
    private interface AdminTask { void run(UUID targetUUID, String targetName); }
}