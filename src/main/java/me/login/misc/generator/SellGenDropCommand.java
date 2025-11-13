package me.login.misc.generator;

import me.login.Login;
import me.login.scoreboard.SkriptUtils;
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
            player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>No generator drops found to sell."));
            return true;
        }

        // Give Money (Vault)
        plugin.getVaultEconomy().depositPlayer(player, totalValue);

        player.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Sold " + count + " drops for <gold>$" + totalValue));
        return true;
    }
}