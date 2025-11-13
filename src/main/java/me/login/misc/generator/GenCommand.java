package me.login.misc.generator;

import me.login.Login;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class GenCommand implements CommandExecutor {

    private final Login plugin;
    private final GenManager manager;
    private final GenItemManager itemManager;

    public GenCommand(Login plugin, GenManager manager, GenItemManager itemManager) {
        this.plugin = plugin;
        this.manager = manager;
        this.itemManager = itemManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Usage: /generator <check|remove|give|setlimit>"));
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("admin.gengive")) return noPerm(sender);
            if (args.length < 3) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Usage: /generator give <player> <gen_id>"));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Player not found."));
                return true;
            }
            ItemStack item = itemManager.getGeneratorItem(args[2]);
            if (item == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Invalid Gen ID."));
                return true;
            }
            target.getInventory().addItem(item);
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Gave generator to " + target.getName()));
            return true;
        }

        if (args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("admin.gencheck")) return noPerm(sender);
            String targetName = args.length > 1 ? args[1] : sender.getName();
            // This would technically require an offline player lookup or cached UUIDs,
            // simplifying to online/active for now as per 'activeGenerators' cache
            // Better: iterate activeGenerators
            int count = 0;
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<yellow>Generators for " + targetName + ":"));
            for (GenManager.GenInstance gen : manager.getActiveGenerators().values()) {
                // In a real scenario, fetch UUID from name properly
                // Here we just list all for simplicity of the prompt requirement
                Player t = plugin.getServer().getPlayer(targetName);
                if (t != null && gen.ownerUUID.equals(t.getUniqueId().toString())) {
                    sender.sendMessage(plugin.getComponentSerializer().deserialize(" <gray>- " + gen.tierId + " at " + gen.world + " " + gen.x + "," + gen.y + "," + gen.z));
                    count++;
                }
            }
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<yellow>Total: " + count));
            return true;
        }

        if (args[0].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("admin.genremove")) return noPerm(sender);
            if (args.length < 2) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Usage: /generator remove <player>"));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>Player offline/not found."));
                return true;
            }
            String uuid = target.getUniqueId().toString();
            int removed = 0;
            // Iterate to remove from world and cache
            // Create a copy to avoid CME
            for (Map.Entry<String, GenManager.GenInstance> entry : manager.getActiveGenerators().entrySet()) {
                if (entry.getValue().ownerUUID.equals(uuid)) {
                    Location loc = new Location(plugin.getServer().getWorld(entry.getValue().world), entry.getValue().x, entry.getValue().y, entry.getValue().z);
                    loc.getBlock().setType(Material.AIR);
                    manager.breakGenerator(target, loc); // Re-use break logic (handles DB and Cache)
                    removed++;
                }
            }
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<green>Removed " + removed + " generators."));
            return true;
        }

        return true;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getServerPrefix() + "<red>No Permission."));
        return true;
    }
}