package me.login.pets;

import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.gui.PetMenu;
import me.login.pets.listeners.PetInventoryListener;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class PetCommand implements CommandExecutor, TabCompleter {

    private final Login plugin;
    private final PetManager petManager;
    private final PetMessageHandler messageHandler;
    private final PetsLogger logger;
    private final PetsConfig config;
    private final PetItemManager itemManager;
    private final PetInventoryListener inventoryListener;

    private final String permAdmin = "mineaurora.pets.admin";

    public PetCommand(Login plugin, PetManager petManager, PetMessageHandler messageHandler, PetsLogger logger, PetsConfig config, PetItemManager itemManager, PetInventoryListener inventoryListener) {
        this.plugin = plugin;
        this.petManager = petManager;
        this.messageHandler = messageHandler;
        this.logger = logger;
        this.config = config;
        this.itemManager = itemManager;
        this.inventoryListener = inventoryListener;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                messageHandler.sendSenderMessage(sender, "<red>Only players can open the pet menu.</red>");
                return true;
            }
            Player player = (Player) sender;
            // --- FIX: Pass 'player' to open() ---
            new PetMenu(player, petManager, PetMenu.PetMenuSort.RARITY).open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (!sender.hasPermission(permAdmin)) {
            if (!(sender instanceof Player)) {
                messageHandler.sendSenderMessage(sender, "<red>You do not have permission.</red>");
                return true;
            }
            // --- FIX: Pass 'sender' cast to Player to open() ---
            new PetMenu((Player) sender, petManager, PetMenu.PetMenuSort.RARITY).open((Player) sender);
            return true;
        }

        switch (sub) {
            case "reload":
                config.loadConfig();
                itemManager.loadPetItems();
                messageHandler.sendSenderMessage(sender, "<green>Pets config and items reloaded.</green>");
                logger.log("Pets config reloaded by " + sender.getName());
                break;

            case "check":
                handleCheck(sender, args);
                break;

            case "give":
                handleGive(sender, args);
                break;

            case "remove":
                handleRemove(sender, args);
                break;

            case "add":
                handleAdd(sender, args);
                break;

            case "addallpets":
                handleAddAllPets(sender, args);
                break;

            case "revive":
                handleRevive(sender, args);
                break;

            case "petinvcheck":
                handlePetInvCheck(sender, args);
                break;

            default:
                sendAdminHelp(sender);
                break;
        }
        return true;
    }

    private void sendAdminHelp(CommandSender sender) {
        messageHandler.sendSenderMessage(sender, "<gold>--- Pet Admin Commands ---</gold>");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet check <player></yellow> - Check a player's pets.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet give <player> <item> [amount]</yellow> - Give pet items.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet remove <player> <pet_type></yellow> - Remove a pet.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet add <player> <pet_type></yellow> - Add a pet.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet addallpets <player></yellow> - Give ALL pets to a player.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet revive <player> <pet_type | all></yellow> - Revive pet(s).");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet petinvcheck <player> <pet_type></yellow> - Open a pet's inventory.");
        messageHandler.sendSenderMessage(sender, "<yellow>/pet reload</yellow> - Reload config.");
    }

    private void handleAddAllPets(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet addallpets <player></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>");
            return;
        }

        int count = 0;
        Set<EntityType> allTypes = config.getAllCapturablePetTypes();

        for (EntityType type : allTypes) {
            if (!petManager.hasPet(target.getUniqueId(), type)) {
                if (petManager.addPet(target.getUniqueId(), type)) {
                    count++;
                }
            }
        }

        messageHandler.sendSenderMessage(sender, "<green>Successfully added " + count + " new pets to " + target.getName() + "!</green>");
        logger.log(sender.getName() + " ran addallpets for " + target.getName());
    }

    private void handleCheck(CommandSender sender, String[] args) {
        if (args.length < 2) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet check <player></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>");
            return;
        }
        List<Pet> pets = petManager.getPlayerData(target.getUniqueId());
        if (pets.isEmpty()) {
            messageHandler.sendSenderMessage(sender, "<yellow>" + target.getName() + " has no pets.</yellow>");
            return;
        }
        messageHandler.sendSenderMessage(sender, "<gold>" + target.getName() + "'s Pets:</gold>");
        for (Pet pet : pets) {
            String status = pet.isOnCooldown() ? "<red>[Cooldown]</red>" : "<green>[Ready]</green>";
            messageHandler.sendSenderMessage(sender,
                    "<gray>- <white>" + pet.getPetType() + "</white> [Lvl " + pet.getLevel() + "] " +
                            "<yellow>[Hunger: " + String.format("%.1f", pet.getHunger()) + "]</yellow> " + status + "</gray>");
        }
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet give <player> <item_key> [amount]</red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>");
            return;
        }
        String itemKey = args[2];
        ItemStack item = itemManager.getItem(itemKey);
        if (item == null) {
            messageHandler.sendSenderMessage(sender, "<red>Unknown item key: " + itemKey + "</red>");
            messageHandler.sendSenderMessage(sender, "<gray>Valid keys: " + itemManager.getItemKeys().toString() + "</gray>");
            return;
        }
        int amount = 1;
        if (args.length > 3) {
            try { amount = Integer.parseInt(args[3]); } catch (NumberFormatException e) { /* default 1 */ }
        }
        item.setAmount(amount);
        target.getInventory().addItem(item);
        messageHandler.sendSenderMessage(sender, "<green>Gave " + amount + "x " + itemKey + " to " + target.getName() + ".</green>");
        logger.log(sender.getName() + " gave " + amount + "x " + itemKey + " to " + target.getName());
    }

    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet remove <player> <pet_type></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>"); return; }
        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException e) {
            messageHandler.sendSenderMessage(sender, "<red>Invalid pet type: " + args[2] + "</red>"); return;
        }

        if (!petManager.hasPet(target.getUniqueId(), petType)) {
            messageHandler.sendSenderMessage(sender, "<red>" + target.getName() + " does not own that pet.</red>");
            return;
        }

        if (petManager.removePet(target.getUniqueId(), petType)) {
            messageHandler.sendSenderMessage(sender, "<green>Removed " + petType + " from " + target.getName() + ".</green>");
            logger.log(sender.getName() + " removed pet " + petType + " from " + target.getName());
        } else {
            messageHandler.sendSenderMessage(sender, "<red>Failed to remove pet from database.</red>");
        }
    }

    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet add <player> <pet_type></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>"); return; }
        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException e) {
            messageHandler.sendSenderMessage(sender, "<red>Invalid pet type: " + args[2] + "</red>"); return;
        }

        if (!config.isCapturable(petType)) {
            messageHandler.sendSenderMessage(sender, "<red>That entity type is not a valid pet in the config.</red>");
            return;
        }
        if (petManager.hasPet(target.getUniqueId(), petType)) {
            messageHandler.sendSenderMessage(sender, "<red>" + target.getName() + " already owns that pet.</red>");
            return;
        }

        if (petManager.addPet(target.getUniqueId(), petType)) {
            messageHandler.sendSenderMessage(sender, "<green>Added " + petType + " to " + target.getName() + ".</green>");
            logger.log(sender.getName() + " added pet " + petType + " to " + target.getName());
        } else {
            messageHandler.sendSenderMessage(sender, "<red>Failed to add pet (database error or already exists).</red>");
        }
    }

    private void handleRevive(CommandSender sender, String[] args) {
        if (args.length < 3) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet revive <player> <pet_type | all></red>");
            return;
        }
        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>"); return; }

        String typeArg = args[2].toUpperCase();
        if (typeArg.equals("ALL")) {
            List<Pet> pets = petManager.getPlayerData(target.getUniqueId());
            int count = 0;
            for (Pet pet : pets) {
                if (pet.isOnCooldown()) {
                    petManager.revivePet(target.getUniqueId(), pet.getPetType());
                    count++;
                }
            }
            messageHandler.sendSenderMessage(sender, "<green>Revived " + count + " pets for " + target.getName() + ".</green>");
            logger.log(sender.getName() + " revived all pets for " + target.getName());
        } else {
            EntityType petType;
            try { petType = EntityType.valueOf(typeArg); } catch (IllegalArgumentException e) {
                messageHandler.sendSenderMessage(sender, "<red>Invalid pet type: " + typeArg + "</red>"); return;
            }
            if (!petManager.hasPet(target.getUniqueId(), petType)) {
                messageHandler.sendSenderMessage(sender, "<red>" + target.getName() + " does not own that pet.</red>");
                return;
            }
            if (petManager.revivePet(target.getUniqueId(), petType)) {
                messageHandler.sendSenderMessage(sender, "<green>Revived " + petType + " for " + target.getName() + ".</green>");
                logger.log(sender.getName() + " revived pet " + petType + " for " + target.getName());
            } else {
                messageHandler.sendSenderMessage(sender, "<red>Failed to revive pet.</red>");
            }
        }
    }

    private void handlePetInvCheck(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            messageHandler.sendSenderMessage(sender, "<red>This command can only be run by a player.</red>");
            return;
        }
        if (args.length < 3) {
            messageHandler.sendSenderMessage(sender, "<red>Usage: /pet petinvcheck <player> <pet_type></red>");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) { messageHandler.sendSenderMessage(sender, "<red>Player not found.</red>"); return; }

        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase()); } catch (IllegalArgumentException e) {
            messageHandler.sendSenderMessage(sender, "<red>Invalid pet type: " + args[2] + "</red>"); return;
        }

        Pet pet = petManager.getPet(target.getUniqueId(), petType);
        if (pet == null) {
            messageHandler.sendSenderMessage(sender, "<red>" + target.getName() + " does not own that pet.</red>");
            return;
        }

        inventoryListener.openPetInventory((Player) sender, pet, true);
    }


    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(permAdmin)) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("check", "give", "remove", "add", "addallpets", "revive", "petinvcheck", "reload")
                    .stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }

        if (args.length == 2) {
            return null;
        }

        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "give":
                    return itemManager.getItemKeys().stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                case "remove":
                case "petinvcheck":
                    Player target = Bukkit.getPlayer(args[1]);
                    if (target == null) return List.of();
                    return petManager.getPlayerData(target.getUniqueId()).stream()
                            .map(pet -> pet.getPetType().name())
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                case "add":
                    Player targetAdd = Bukkit.getPlayer(args[1]);
                    if (targetAdd == null) return List.of();
                    List<String> ownedTypes = petManager.getPlayerData(targetAdd.getUniqueId()).stream()
                            .map(pet -> pet.getPetType().name()).collect(Collectors.toList());
                    return config.getAllCapturablePetTypes().stream()
                            .filter(type -> !ownedTypes.contains(type.name()))
                            .map(Enum::name)
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                case "revive":
                    Player targetRevive = Bukkit.getPlayer(args[1]);
                    if (targetRevive == null) return List.of();
                    List<String> cooldowPets = petManager.getPlayerData(targetRevive.getUniqueId()).stream()
                            .filter(Pet::isOnCooldown)
                            .map(pet -> pet.getPetType().name())
                            .collect(Collectors.toList());
                    cooldowPets.add("ALL");
                    return cooldowPets.stream()
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
            }
        }
        return List.of();
    }
}