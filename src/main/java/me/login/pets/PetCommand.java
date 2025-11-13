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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections; // --- FIXED: Added import ---
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
            // --- FIXED: Added missing method to PetMessageHandler ---
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
        // ... (implementation)
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
        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet give <item_name> [amount]</red>");
            return true;
        }

        String itemName = args[1];
        // --- FIXED: Added missing method to PetsConfig ---
        ItemStack item = config.getCaptureItem(itemName);

        if (item == null) {
            messageHandler.sendPlayerMessage(player, "<red>No item found for '" + itemName + "'. Check items.yml.</red>");
            return true;
        }

        int amount = 1;
        if (args.length == 3) {
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                messageHandler.sendPlayerMessage(player, "<red>'" + args[2] + "' is not a valid amount.</red>");
                return true;
            }
        }

        item.setAmount(amount);
        player.getInventory().addItem(item);
        messageHandler.sendPlayerMessage(player, "<green>Gave you " + amount + "x " + itemName + ".</green>");
        logger.logAdmin(player.getName(), "Gave self " + amount + "x " + itemName);
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
            options.addAll(Arrays.asList("summon", "despawn"));
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
                    // Suggest all configured capture items
                    // --- FIXED: Added missing method to PetsConfig ---
                    options.addAll(config.getCaptureItemNames());
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
                case "add":
                    // Suggest all capturable pets
                    // --- FIXED: Changed lambda syntax ---
                    options.addAll(config.getAllCapturablePetTypes().stream()
                            .map(type -> type.name())
                            .toList());
                    break;
                case "remove":
                case "revive":
                    // Suggest pets the target player owns (this is complex, skip for now or do basic)
                    // For simplicity, just suggest all capturable pets
                    // --- FIXED: Changed lambda syntax ---
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