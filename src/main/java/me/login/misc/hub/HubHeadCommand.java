package me.login.misc.hub;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class HubHeadCommand implements CommandExecutor {

    private final HubHeadModule module;

    public HubHeadCommand(HubHeadModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mineaurora.admin")) {
            sender.sendMessage("§cNo permission.");
            return true;
        }

        if (args.length != 1) {
            sender.sendMessage("§cUsage: /rotatinghead <discord/store>");
            return true;
        }

        String type = args[0].toLowerCase();
        if (type.equals("discord")) {
            if (module.getDiscordLoc() != null) {
                module.spawnHead(HubHeadModule.HEAD_DISCORD, module.getDiscordLoc(), module.getDiscordHeadUrl(), "<blue><bold>Discord</bold></blue>");
                sender.sendMessage("§aSpawned/Respawned Discord floating head.");
            } else {
                sender.sendMessage("§cHub world not found or location invalid.");
            }
        } else if (type.equals("store")) {
            if (module.getStoreLoc() != null) {
                module.spawnHead(HubHeadModule.HEAD_STORE, module.getStoreLoc(), module.getStoreHeadUrl(), "<gold><bold>Store</bold></gold>");
                sender.sendMessage("§aSpawned/Respawned Store floating head.");
            } else {
                sender.sendMessage("§cHub world not found or location invalid.");
            }
        } else {
            sender.sendMessage("§cInvalid type. Use 'discord' or 'store'.");
        }

        return true;
    }
}