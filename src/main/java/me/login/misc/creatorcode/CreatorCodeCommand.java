package me.login.misc.creatorcode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set; // <-- THE FIX: Added this import
import java.util.stream.Collectors;

public class CreatorCodeCommand implements CommandExecutor, TabCompleter {

    private final CreatorCodeManager manager;
    private final CreatorCodeLogger logger;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public CreatorCodeCommand(CreatorCodeManager manager, CreatorCodeLogger logger, Component serverPrefix) {
        this.manager = manager;
        this.logger = logger;
        this.serverPrefix = serverPrefix;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (command.getName().equalsIgnoreCase("removecreditcode")) {
            if (!sender.hasPermission("login.creatorcode.admin")) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You do not have permission to use this command.</red>")));
                return true;
            }
            if (args.length != 1) {
                sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /removecreditcode <name></red>")));
                return true;
            }
            handleRemove(sender, args[0]);
            return true;
        }

        // Handling /creatorcode
        if (!sender.hasPermission("login.creatorcode.admin")) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>You do not have permission to use this command.</red>")));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                if (args.length != 2) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /creatorcode add <name></red>")));
                    return true;
                }
                handleAdd(sender, args[1]);
                break;
            case "remove":
                if (args.length != 2) {
                    sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Usage: /creatorcode remove <name></red>")));
                    return true;
                }
                handleRemove(sender, args[1]);
                break;
            case "list":
                handleList(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String code) {
        if (manager.addCode(code)) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Added creator code: </green><yellow>" + code + "</yellow>")));
            logger.logAdmin("Admin `" + sender.getName() + "` added creator code: `" + code + "`");
        } else {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Creator code </red><yellow>" + code + "</yellow><red> already exists.</red>")));
        }
    }

    private void handleRemove(CommandSender sender, String code) {
        if (manager.removeCode(code)) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Removed creator code: </green><yellow>" + code + "</yellow>")));
            logger.logAdmin("Admin `" + sender.getName() + "` removed creator code: `" + code + "`");
        } else {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<red>Creator code </red><yellow>" + code + "</yellow><red> does not exist.</red>")));
        }
    }

    private void handleList(CommandSender sender) {
        Set<String> codes = manager.getCodes(); // This line (100) will now compile
        if (codes.isEmpty()) {
            sender.sendMessage(serverPrefix.append(mm.deserialize("<yellow>There are no creator codes.</yellow>")));
            return;
        }
        String codeList = String.join("<gray>, </gray>", codes);
        sender.sendMessage(serverPrefix.append(mm.deserialize("<green>Creator Codes:</green> <white>" + codeList + "</white>")));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(mm.deserialize("<gold>--- Creator Code Admin Help ---</gold>"));
        sender.sendMessage(mm.deserialize("<yellow>/creatorcode add <name></yellow> <gray>- Adds a creator code.</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/creatorcode remove <name></yellow> <gray>- Removes a creator code.</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/creatorcode list</yellow> <gray>- Lists all creator codes.</gray>"));
        sender.sendMessage(mm.deserialize("<yellow>/removecreditcode <name></yellow> <gray>- Alias for remove.</gray>"));
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("creatorcode")) {
            if (args.length == 1) {
                return Arrays.asList("add", "remove", "list").stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                return manager.getCodes().stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }

        if (command.getName().equalsIgnoreCase("removecreditcode")) {
            if (args.length == 1) {
                return manager.getCodes().stream()
                        .filter(s -> s.startsWith(args[0].toLowerCase()))
                        .collect(Collectors.toList());
            }
        }
        return null;
    }
}