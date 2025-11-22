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

    private final Login plugin;
    private final LoginSystem loginSystem;
    private final LoginDatabase loginDb;
    private final DiscordLinkDatabase discordLinkDb;
    private final LoginSystemLogger logger;
    private final ParkourManager parkourManager; // Added

    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public LoginSystemCmd(Login plugin, LoginSystem loginSystem, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb, LoginSystemLogger logger, ParkourManager parkourManager) {
        this.plugin = plugin;
        this.loginSystem = loginSystem;
        this.loginDb = loginDb;
        this.discordLinkDb = discordLinkDb;
        this.logger = logger;
        this.parkourManager = parkourManager; // Inject
        this.serverPrefix = loginSystem.getServerPrefix();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(Component.text("Only players can use this command.", NamedTextColor.RED));
            return true;
        }
        Player player = (Player) sender;
        UUID uuid = player.getUniqueId();

        if (cmd.getName().equalsIgnoreCase("login")) {
            if (args.length == 1) {
                loginSystem.handleLogin(player, args[0], player.getAddress().getAddress().getHostAddress());
            } else {
                player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /login <password></red>")));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("register")) {
            if (args.length == 2) {
                loginSystem.handleRegister(player, args[0], args[1], player.getAddress().getAddress().getHostAddress());
            } else {
                player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /register <password> <confirm></red>")));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("changepassword")) {
            if (loginSystem.isUnloggedIn(uuid)) {
                player.sendMessage(serverPrefix.append(mm.deserialize("<red>You must be logged in to use this command.</red>")));
                return true;
            }
            if (args.length == 2) {
                loginSystem.handleChangePassword(player, args[0], args[1]);
            } else {
                player.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /changepassword <old> <new></red>")));
            }
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("loginparkour")) {
            if (!player.isOp()) {
                player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length > 0) {
                parkourManager.handleSetupCommand(player, args[0]);
                return true;
            }
            player.sendMessage(Component.text("Usage: /loginparkour <checkpoint|finalpoint|startingpoint>", NamedTextColor.RED));
            return true;
        }

        if (cmd.getName().equalsIgnoreCase("logindisplaykill")) {
            if (!player.isOp()) {
                player.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            parkourManager.killAllDisplays(player);
            return true;
        }

        return false;
    }

    public static class AdminTask { }
}