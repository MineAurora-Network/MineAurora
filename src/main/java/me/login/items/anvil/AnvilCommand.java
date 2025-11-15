package me.login.items.anvil;

import me.login.Login;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class AnvilCommand implements CommandExecutor {

    private final DyeAnvilMenu anvilMenu;

    public AnvilCommand(DyeAnvilMenu anvilMenu) {
        this.anvilMenu = anvilMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this.");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("dye")) {
            anvilMenu.open(player);
            return true;
        }

        player.sendMessage(MiniMessage.miniMessage().deserialize("<red>Usage: /anvil dye"));
        return true;
    }
}