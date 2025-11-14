package me.login.misc.generator;

import me.login.Login;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class SellGenDropCommand implements CommandExecutor {

    private final Login plugin;
    private final GenItemManager itemManager;

    public SellGenDropCommand(Login plugin, GenItemManager itemManager) {
        this.plugin = plugin;
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player) sender;

        double totalValue = 0;
        int count = 0;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null || item.getType() == Material.AIR) continue;

            Double val = itemManager.getDropValue(item);
            if (val != null && val > 0) {
                totalValue += val * item.getAmount();
                count += item.getAmount();
                item.setAmount(0); // Remove item
            }
        }

        if (count == 0) {
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>No generator drops found to sell."));
            return true;
        }

        // Give Money (Vault)
        plugin.getVaultEconomy().depositPlayer(player, totalValue);

        String message = "<green>Sold <white>" + count + " <green>drops for <gold>$" + totalValue;
        player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + message));

        // Title (Requested Feature)
        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                LegacyComponentSerializer.legacyAmpersand().deserialize(plugin.getConfig().getString("server_prefix_2")),
                plugin.getComponentSerializer().deserialize(message)
        );
        player.showTitle(title);

        return true;
    }
}