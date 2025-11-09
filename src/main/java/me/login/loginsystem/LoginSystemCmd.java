package me.login.loginsystem;

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class LoginSystemCmd implements CommandExecutor {

    private final LoginSystem loginSystem;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoginSystemCmd(Login plugin, LoginSystem loginSystem, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb, LoginSystemLogger logger) {
        this.loginSystem = loginSystem;
        this.serverPrefix = loginSystem.getServerPrefix();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        String cmdName = cmd.getName().toLowerCase();

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("These commands can only be used by players.", NamedTextColor.RED));
            return true;
        }

        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "UNKNOWN";

        switch (cmdName) {
            case "register":
                if (!loginSystem.isUnloggedIn(uuid)) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>You are already logged in.</red>")));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /register <password> <confirm-password></red>")));
                    return true;
                }
                loginSystem.handleRegister(player, args[0], args[1], ip);
                return true;

            case "login":
                if (!loginSystem.isUnloggedIn(uuid)) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>You are already logged in.</red>")));
                    return true;
                }
                if (args.length != 1) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /login <password></red>")));
                    return true;
                }
                loginSystem.handleLogin(player, args[0], ip);
                return true;

            case "changepassword":
                if (loginSystem.isUnloggedIn(uuid)) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>You must be logged in to use this command.</red>")));
                    return true;
                }
                if (args.length != 2) {
                    player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /changepassword <old-password> <new-password></red>")));
                    return true;
                }
                loginSystem.handleChangePassword(player, args[0], args[1]);
                return true;
        }
        return false;
    }
}