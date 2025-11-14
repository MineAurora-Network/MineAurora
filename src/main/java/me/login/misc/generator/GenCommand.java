package me.login.misc.generator;

import me.login.Login;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GenCommand implements CommandExecutor, TabCompleter {

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
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Usage: /generator <check|remove|give|setlimit>"));
            return true;
        }

        // /generator give <player> <id>
        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("admin.gengive")) return noPerm(sender);
            if (args.length < 3) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Usage: /generator give <player> <gen_id>"));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Player not found."));
                return true;
            }
            ItemStack item = itemManager.getGeneratorItem(args[2]);
            if (item == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Invalid Gen ID."));
                return true;
            }
            target.getInventory().addItem(item);
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Gave generator to " + target.getName()));
            return true;
        }

        // /generator check <player>
        if (args[0].equalsIgnoreCase("check")) {
            if (!sender.hasPermission("admin.gencheck")) return noPerm(sender);
            String targetName = args.length > 1 ? args[1] : sender.getName();

            // Using a simple online player lookup for prompt requirements
            // (In production, this should ideally handle offline UUID lookup)
            Player t = plugin.getServer().getPlayer(targetName);
            if (t == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Player must be online to check active gens."));
                return true;
            }

            int count = 0;
            StringBuilder sb = new StringBuilder();
            sb.append("<yellow>Generators for ").append(targetName).append(":%nl%");

            for (GenManager.GenInstance gen : manager.getActiveGenerators().values()) {
                if (gen.ownerUUID.equals(t.getUniqueId().toString())) {
                    sb.append(" <gray>- ").append(gen.tierId).append(" at ").append(gen.world).append(" ").append(gen.x).append(",").append(gen.y).append(",").append(gen.z).append("%nl%");
                    count++;
                }
            }
            sb.append("<yellow>Total: ").append(count);
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + sb.toString()));
            return true;
        }

        // /generator remove <player>
        if (args[0].equalsIgnoreCase("remove")) {
            if (!sender.hasPermission("admin.genremove")) return noPerm(sender);
            if (args.length < 2) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Usage: /generator remove <player>"));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Player offline/not found."));
                return true;
            }
            String uuid = target.getUniqueId().toString();
            int removed = 0;

            // Create a copy of entries to avoid ConcurrentModificationException
            for (Map.Entry<String, GenManager.GenInstance> entry : new java.util.HashMap<>(manager.getActiveGenerators()).entrySet()) {
                if (entry.getValue().ownerUUID.equals(uuid)) {
                    Location loc = new Location(plugin.getServer().getWorld(entry.getValue().world), entry.getValue().x, entry.getValue().y, entry.getValue().z);
                    loc.getBlock().setType(Material.AIR);
                    manager.breakGenerator(target, loc);
                    removed++;
                }
            }
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Removed " + removed + " generators."));
            return true;
        }

        // /generator setlimit <player> <amount>
        if (args[0].equalsIgnoreCase("setlimit")) {
            if (!sender.hasPermission("admin.gensetlimit")) return noPerm(sender);
            if (args.length < 3) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Usage: /generator setlimit <player> <amount>"));
                return true;
            }
            Player target = plugin.getServer().getPlayer(args[1]);
            if (target == null) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Player not found."));
                return true;
            }
            int limit;
            try {
                limit = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>Invalid number."));
                return true;
            }

            manager.setPlayerLimit(target.getUniqueId().toString(), limit);
            sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<green>Set generator limit for " + target.getName() + " to " + limit));
            return true;
        }

        return true;
    }

    private boolean noPerm(CommandSender sender) {
        sender.sendMessage(plugin.getComponentSerializer().deserialize(plugin.getConfig().getString("server_prefix") + "<red>No Permission."));
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            if (sender.hasPermission("admin.gengive")) completions.add("give");
            if (sender.hasPermission("admin.gencheck")) completions.add("check");
            if (sender.hasPermission("admin.genremove")) completions.add("remove");
            if (sender.hasPermission("admin.gensetlimit")) completions.add("setlimit");
            return completions;
        }

        if (args.length == 2) {
            // Suggest player names for all subcommands
            return null; // Return null to let Bukkit suggest player names automatically
        }

        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("give") && sender.hasPermission("admin.gengive")) {
                // Suggest Gen IDs (Assuming we can get IDs from ItemManager, need a getter)
                // Since ItemManager doesn't expose the map keys publicly in my previous code,
                // let's assume 'wheat', 'melon', etc. are known or update ItemManager to expose keys.
                // For now, I'll manually list the common ones or leave empty if dynamic lookup isn't added.
                // Ideally: return itemManager.getGenIds();
                completions.add("wheat");
                completions.add("melon");
                completions.add("pumpkin");
                completions.add("coal_ore");
                // ... etc
                return completions;
            }
            if (args[0].equalsIgnoreCase("setlimit") && sender.hasPermission("admin.gensetlimit")) {
                completions.add("5");
                completions.add("10");
                completions.add("20");
                return completions;
            }
        }

        return completions;
    }
}