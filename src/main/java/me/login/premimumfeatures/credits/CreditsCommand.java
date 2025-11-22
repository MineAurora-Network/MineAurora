package me.login.premimumfeatures.credits;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class CreditsCommand implements CommandExecutor {

    private final Login plugin;
    private final CreditsManager manager;

    public CreditsCommand(Login plugin, CreditsManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        // Fetch prefix from config using Adventure Component
        String rawPrefix = plugin.getConfig().getString("server_prefix", "&b[MineAurora] ");
        // Simple legacy converter for the prefix part if it uses '&' in config
        Component prefix = Component.text(rawPrefix.replace("&", "§") + " ");

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                sender.sendMessage(prefix.append(Component.text("Console usage: /credits check <player>", NamedTextColor.RED)));
                return true;
            }
            // Check own balance
            double balance = manager.getBalance((Player) sender);
            sender.sendMessage(prefix.append(Component.text("Your credits: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.2f", balance), NamedTextColor.GOLD)));
            return true;
        }

        // /credits check <player>
        if (args[0].equalsIgnoreCase("check")) {
            if (args.length < 2) {
                if (sender instanceof Player) {
                    double balance = manager.getBalance((Player) sender);
                    sender.sendMessage(prefix.append(Component.text("Your credits: ", NamedTextColor.GRAY))
                            .append(Component.text(String.format("%.2f", balance), NamedTextColor.GOLD)));
                } else {
                    sender.sendMessage(prefix.append(Component.text("Usage: /credits check <player>", NamedTextColor.RED)));
                }
                return true;
            }

            if (!sender.hasPermission("login.credits.check.others")) {
                sender.sendMessage(prefix.append(Component.text("You do not have permission to check others' credits.", NamedTextColor.RED)));
                return true;
            }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            double balance = manager.getBalance(target);
            sender.sendMessage(prefix.append(Component.text(target.getName() + "'s credits: ", NamedTextColor.GRAY))
                    .append(Component.text(String.format("%.2f", balance), NamedTextColor.GOLD)));
            return true;
        }

        // Admin commands
        if (!sender.hasPermission("login.credits.admin")) {
            sender.sendMessage(prefix.append(Component.text("You do not have permission.", NamedTextColor.RED)));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(prefix.append(Component.text("Usage: /credits <add/remove/set> <player> <amount>", NamedTextColor.RED)));
            return true;
        }

        String subCmd = args[0];
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        double amount;

        try {
            amount = Double.parseDouble(args[2]);
            if (amount < 0) throw new NumberFormatException();
        } catch (NumberFormatException e) {
            sender.sendMessage(prefix.append(Component.text("Invalid amount. Must be a positive number.", NamedTextColor.RED)));
            return true;
        }

        String adminName = sender.getName();

        switch (subCmd.toLowerCase()) {
            case "add":
                manager.addCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(Component.text("Added ", NamedTextColor.GREEN))
                        .append(Component.text(amount, NamedTextColor.GOLD))
                        .append(Component.text(" credits to " + target.getName(), NamedTextColor.GREEN)));
                break;
            case "remove":
                manager.removeCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(Component.text("Removed ", NamedTextColor.RED))
                        .append(Component.text(amount, NamedTextColor.GOLD))
                        .append(Component.text(" credits from " + target.getName(), NamedTextColor.RED)));
                break;
            case "set":
                manager.setCredits(adminName, target, amount);
                sender.sendMessage(prefix.append(Component.text("Set " + target.getName() + "'s credits to ", NamedTextColor.YELLOW))
                        .append(Component.text(amount, NamedTextColor.GOLD)));
                break;
            default:
                sender.sendMessage(prefix.append(Component.text("Usage: /credits <add/remove/set/check> <player> <amount>", NamedTextColor.RED)));
                break;
        }

        return true;
    }
}