package me.login.pets;

import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.gui.PetMenu;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Handles all /pet commands for players and admins.
 */
public class PetCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final PetManager petManager;
    private final PetMessageHandler messageHandler;
    private final PetsLogger logger;
    private final PetsConfig config;

    public PetCommand(Login plugin, PetManager petManager, PetMessageHandler messageHandler, PetsLogger logger, PetsConfig config) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.config = config;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            messageHandler.sendConsoleMessage("<red>Only players can use this command.</red>");
            return true;
        }

        Player player = (Player) sender;
        if (args.length == 0) {
            // Open the main pet menu
            new PetMenu(player, petManager, PetMenu.PetMenuSort.RARITY).open();
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "summon":
                return handleSummon(player, args);
            case "despawn":
                return handleDespawn(player);
            case "check":
                return handleAdminCheck(player, args);
            case "add":
                return handleAdminAdd(player, args);
            case "remove":
                return handleAdminRemove(player, args);
            case "give":
                return handleAdminGive(player, args);
            case "revive":
                return handleAdminRevive(player, args);
            case "menu":
                new PetMenu(player, petManager, PetMenu.PetMenuSort.RARITY).open();
                return true;
            default:
                messageHandler.sendPlayerMessage(player, "<red>Unknown command. Use /pet</red>");
                return true;
        }
    }

    private boolean handleSummon(Player player, String[] args) {
        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet summon <pet_name></red>");
            return true;
        }

        EntityType petType;
        try {
            petType = EntityType.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            messageHandler.sendPlayerMessage(player, "<red>'" + args[1] + "' is not a valid pet type.</red>");
            return true;
        }

        if (!petManager.hasPet(player.getUniqueId(), petType)) {
            messageHandler.sendPlayerMessage(player, "<red>You do not own this pet.</red>");
            return true;
        }

        if (petManager.isPetOnCooldown(player.getUniqueId(), petType)) {
            long remaining = petManager.getPetCooldownRemaining(player.getUniqueId(), petType);
            messageHandler.sendPlayerMessage(player, "<red>Your " + petType.name() + " is on cooldown for " + remaining + "s.</red>");
            return true;
        }

        petManager.despawnPet(player.getUniqueId(), false);
        petManager.summonPet(player, petType);
        messageHandler.sendPlayerMessage(player, "<green>Your " + petType.name() + " has been summoned!</green>");
        return true;
    }

    private boolean handleDespawn(Player player) {
        if (!petManager.hasActivePet(player.getUniqueId())) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have a pet summoned.</red>");
            return true;
        }
        petManager.despawnPet(player.getUniqueId(), true);
        return true;
    }

    // ... Admin commands ...

    private boolean handleAdminCheck(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.check")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        messageHandler.sendPlayerMessage(player, "<yellow>Feature under development.</yellow>");
        return true;
    }

    private boolean handleAdminAdd(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.add")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }

        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet add <player> <pet_name></red>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try {
            petType = EntityType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            messageHandler.sendPlayerMessage(player, "<red>'" + args[2] + "' is not a valid pet type.</red>");
            return true;
        }

        if (!config.isCapturable(petType)) {
            messageHandler.sendPlayerMessage(player, "<red>This entity type is not a capturable pet.</red>");
            return true;
        }

        // Run async
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (petManager.addPet(target.getUniqueId(), petType)) {
                messageHandler.sendPlayerMessage(player, "<green>Gave " + petType.name() + " pet to " + target.getName() + ".</green>");
                logger.logAdmin(player.getName(), "Added pet " + petType.name() + " to " + target.getName());
            } else {
                messageHandler.sendPlayerMessage(player, "<red>That player already has that pet.</red>");
            }
        });
        return true;
    }

    private boolean handleAdminRemove(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.remove")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet remove <player> <pet_name></red>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try {
            petType = EntityType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            messageHandler.sendPlayerMessage(player, "<red>'" + args[2] + "' is not a valid pet type.</red>");
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (petManager.removePet(target.getUniqueId(), petType)) {
                messageHandler.sendPlayerMessage(player, "<green>Removed " + petType.name() + " pet from " + target.getName() + ".</green>");
                logger.logAdmin(player.getName(), "Removed pet " + petType.name() + " from " + target.getName());
            } else {
                messageHandler.sendPlayerMessage(player, "<red>That player does not have that pet.</red>");
            }
        });
        return true;
    }

    private boolean handleAdminGive(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.give")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }

        // Usage:
        // 1. /pet give <item/fruit> [amount]  (Self)
        // 2. /pet give <player> <item/fruit> [amount] (Target)

        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet give [player] <item/fruit> [amount]</red>");
            return true;
        }

        Player target = player;
        String itemName;
        int amount = 1;
        int argOffset = 0;

        // Check if first arg is a player
        Player potentialTarget = Bukkit.getPlayer(args[1]);
        if (potentialTarget != null) {
            target = potentialTarget;
            if (args.length < 3) {
                messageHandler.sendPlayerMessage(player, "<red>Usage: /pet give " + target.getName() + " <item/fruit> [amount]</red>");
                return true;
            }
            itemName = args[2];
            argOffset = 1;
        } else {
            itemName = args[1];
        }

        // Check for amount
        if (args.length > 2 + argOffset) {
            try {
                amount = Integer.parseInt(args[2 + argOffset]);
            } catch (NumberFormatException e) {
                messageHandler.sendPlayerMessage(player, "<red>'" + args[2 + argOffset] + "' is not a valid amount.</red>");
                return true;
            }
        }

        // Try getting as Capture Item
        ItemStack item = config.getCaptureItem(itemName);
        // If not found, try getting as Fruit
        if (item == null) {
            item = config.getFruit(itemName);
        }

        if (item == null) {
            messageHandler.sendPlayerMessage(player, "<red>No item or fruit found for '" + itemName + "'.</red>");
            return true;
        }

        item.setAmount(amount);
        target.getInventory().addItem(item);

        String msg = "<green>Gave " + amount + "x " + itemName;
        if (target.equals(player)) {
            messageHandler.sendPlayerMessage(player, msg + " to yourself.</green>");
        } else {
            messageHandler.sendPlayerMessage(player, msg + " to " + target.getName() + ".</green>");
            messageHandler.sendPlayerMessage(target, "<green>Received " + amount + "x " + itemName + ".</green>");
        }

        logger.logAdmin(player.getName(), "Gave " + target.getName() + " " + amount + "x " + itemName);
        return true;
    }

    private boolean handleAdminRevive(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.revive")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet revive <player> <pet_name></red>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try {
            petType = EntityType.valueOf(args[2].toUpperCase());
        } catch (IllegalArgumentException e) {
            messageHandler.sendPlayerMessage(player, "<red>'" + args[2] + "' is not a valid pet type.</red>");
            return true;
        }

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            if (petManager.revivePet(target.getUniqueId(), petType)) {
                messageHandler.sendPlayerMessage(player, "<green>Revived " + petType.name() + " for " + target.getName() + ".</green>");
                logger.logAdmin(player.getName(), "Revived pet " + petType.name() + " for " + target.getName());
            } else {
                messageHandler.sendPlayerMessage(player, "<red>That player does not have that pet.</red>");
            }
        });
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            return Collections.emptyList();
        }
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(Arrays.asList("summon", "despawn", "menu"));
            if (player.hasPermission("mineaurora.pets.admin.check")) options.add("check");
            if (player.hasPermission("mineaurora.pets.admin.add")) options.add("add");
            if (player.hasPermission("mineaurora.pets.admin.remove")) options.add("remove");
            if (player.hasPermission("mineaurora.pets.admin.give")) options.add("give");
            if (player.hasPermission("mineaurora.pets.admin.revive")) options.add("revive");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase()) {
                case "summon":
                    // Suggest player's own pets
                    options.addAll(petManager.getPlayerData(player.getUniqueId()).stream()
                            .map(pet -> pet.getPetType().name())
                            .toList());
                    break;
                case "give":
                    // Suggest capture items AND fruits
                    options.addAll(config.getCaptureItemNames());
                    options.addAll(config.getFruitNames());
                    // Also suggest players for the target argument
                    options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    break;
                case "check":
                case "add":
                case "remove":
                case "revive":
                    // Suggest online players
                    options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    break;
            }
        } else if (args.length == 3) {
            switch (args[0].toLowerCase()) {
                case "give":
                    // If arg 2 was a player, suggest items now
                    if (Bukkit.getPlayer(args[1]) != null) {
                        options.addAll(config.getCaptureItemNames());
                        options.addAll(config.getFruitNames());
                    }
                    break;
                case "add":
                    // Suggest all capturable pets
                    options.addAll(config.getAllCapturablePetTypes().stream()
                            .map(type -> type.name())
                            .toList());
                    break;
                case "remove":
                case "revive":
                    // Suggest pets the target player owns (for simplicity suggest types)
                    options.addAll(config.getAllCapturablePetTypes().stream()
                            .map(type -> type.name())
                            .toList());
                    break;
            }
        }

        String currentArg = args[args.length - 1].toLowerCase();
        for (String option : options) {
            if (option.toLowerCase().startsWith(currentArg)) {
                completions.add(option);
            }
        }
        return completions;
    }
}