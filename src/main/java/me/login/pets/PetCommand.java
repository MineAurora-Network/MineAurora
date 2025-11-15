package me.login.pets;

import me.login.Login;
import me.login.pets.data.Pet;
import me.login.pets.gui.PetMenu;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;

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
            new PetMenu(player, petManager, PetMenu.PetMenuSort.RARITY).open();
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "summon": return handleSummon(player, args);
            case "despawn": return handleDespawn(player);
            case "check": return handleAdminCheck(player, args);
            case "add": return handleAdminAdd(player, args);
            case "remove": return handleAdminRemove(player, args);
            case "give": return handleAdminGive(player, args);
            case "revive": return handleAdminRevive(player, args);
            case "menu":
                new PetMenu(player, petManager, PetMenu.PetMenuSort.RARITY).open();
                return true;
            case "debug":
                return handleDebugToggle(player, args);
            default:
                messageHandler.sendPlayerMessage(player, "<red>Unknown command. Use /pet</red>");
                return true;
        }
    }

    private boolean handleDebugToggle(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.debug")) {
            messageHandler.sendPlayerMessage(player, "<red>You don't have permission to toggle pet debug.</red>");
            return true;
        }
        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<yellow>Usage: /pet debug on|off OR /pet debug <category> on|off</yellow>");
            return true;
        }

        String a1 = args[1].toLowerCase(Locale.ROOT);
        if ("on".equals(a1) || "off".equals(a1)) {
            boolean on = "on".equals(a1);
            PetDebug.setMaster(on);
            return true;
        }

        // category toggle
        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<yellow>Usage: /pet debug <category> on|off</yellow>");
            return true;
        }
        String cat = a1.toUpperCase(Locale.ROOT);
        String mode = args[2].toLowerCase(Locale.ROOT);
        try {
            PetDebug.Cat c = PetDebug.Cat.valueOf(cat);
            boolean on = "on".equals(mode);
            PetDebug.setCategory(c, on);
            return true;
        } catch (IllegalArgumentException ex) {
            messageHandler.sendPlayerMessage(player, "<red>Unknown debug category. Available: ALL, AI, TARGET, FEEDING, SUMMON, COMBAT</red>");
            return true;
        }
    }

    private boolean handleSummon(Player player, String[] args) {
        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet summon <pet_type></red>");
            return true;
        }
        EntityType petType;
        try { petType = EntityType.valueOf(args[1].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
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
        PetDebug.debugOwner(player, PetDebug.Cat.SUMMON, "Summoned pet " + petType);
        return true;
    }

    private boolean handleDespawn(Player player) {
        if (!petManager.hasActivePet(player.getUniqueId())) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have a pet summoned.</red>");
            return true;
        }
        petManager.despawnPet(player.getUniqueId(), true);
        PetDebug.debugOwner(player, PetDebug.Cat.AI, "Despawned active pet.");
        return true;
    }

    private boolean handleAdminCheck(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.check")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        if (args.length < 2) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet check <player></red>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Pet> pets = petManager.getPlayerData(target.getUniqueId());
            if (pets == null || pets.isEmpty()) {
                messageHandler.sendPlayerMessage(player, "<red>" + target.getName() + " has no pets.</red>");
                return;
            }
            messageHandler.sendPlayerMessage(player, "<gold>Pets owned by " + target.getName() + ":</gold>");
            for (Pet pet : pets) {
                String color = pet.isOnCooldown() ? "<red>" : "<green>";
                messageHandler.sendPlayerMessage(player, " <gray>-</gray> " + color + pet.getDisplayName() + "</" + (pet.isOnCooldown() ? "red" : "green") + ">" +
                        " <gray>(" + pet.getPetType().name() + ")</gray> <yellow>Lvl " + pet.getLevel() + "</yellow>");
            }
        });
        return true;
    }

    private boolean handleAdminAdd(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.add")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet add <player> <pet_type></red>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
            messageHandler.sendPlayerMessage(player, "<red>'" + args[2] + "' is not a valid pet type.</red>");
            return true;
        }
        if (!config.isCapturable(petType)) {
            messageHandler.sendPlayerMessage(player, "<red>This entity type is not a capturable pet.</red>");
            return true;
        }
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
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet remove <player> <pet_type></red>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
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
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet give [player] <item> [amount]</red>");
            return true;
        }

        Player target = player;
        String itemName;
        int amount = 1;
        int offset = 0;

        Player possible = Bukkit.getPlayer(args[1]);
        if (possible != null) {
            target = possible;
            if (args.length < 3) {
                messageHandler.sendPlayerMessage(player, "<red>Usage: /pet give " + target.getName() + " <item> [amount]</red>");
                return true;
            }
            itemName = args[2];
            offset = 1;
        } else {
            itemName = args[1];
        }

        if (args.length > 2 + offset) {
            try { amount = Integer.parseInt(args[2 + offset]); }
            catch (NumberFormatException ex) {
                messageHandler.sendPlayerMessage(player, "<red>'" + args[2 + offset] + "' is not a valid amount.</red>");
                return true;
            }
        }

        ItemStack item = config.getCaptureItem(itemName);
        if (item == null) item = config.getFruit(itemName);
        if (item == null) item = config.getUtilityItem(itemName);

        if (item == null) {
            messageHandler.sendPlayerMessage(player, "<red>No item found for '" + itemName + "'.</red>");
            return true;
        }

        item.setAmount(amount);
        target.getInventory().addItem(item);

        String msg = "<green>Gave " + amount + "x " + itemName;
        if (target.equals(player)) messageHandler.sendPlayerMessage(player, msg + " to yourself.</green>");
        else {
            messageHandler.sendPlayerMessage(player, msg + " to " + target.getName() + ".</green>");
            messageHandler.sendPlayerMessage(target, "<green>Received " + amount + "x " + itemName + ".</green>");
        }

        logger.logAdmin(player.getName(), "Gave " + target.getName() + " " + amount + "x " + itemName);
        PetDebug.debugOwner(player, PetDebug.Cat.AI, "Gave item " + itemName + " x" + amount + " to " + target.getName());
        return true;
    }

    private boolean handleAdminRevive(Player player, String[] args) {
        if (!player.hasPermission("mineaurora.pets.admin.revive")) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have permission.</red>");
            return true;
        }
        if (args.length < 3) {
            messageHandler.sendPlayerMessage(player, "<red>Usage: /pet revive <player> <pet_type></red>");
            return true;
        }
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        EntityType petType;
        try { petType = EntityType.valueOf(args[2].toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e) {
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
        if (!(sender instanceof Player)) return Collections.emptyList();
        Player player = (Player) sender;
        List<String> completions = new ArrayList<>();
        List<String> options = new ArrayList<>();

        if (args.length == 1) {
            options.addAll(Arrays.asList("summon", "despawn", "menu", "debug"));
            if (player.hasPermission("mineaurora.pets.admin.check")) options.add("check");
            if (player.hasPermission("mineaurora.pets.admin.add")) options.add("add");
            if (player.hasPermission("mineaurora.pets.admin.remove")) options.add("remove");
            if (player.hasPermission("mineaurora.pets.admin.give")) options.add("give");
            if (player.hasPermission("mineaurora.pets.admin.revive")) options.add("revive");
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "summon":
                    options.addAll(petManager.getPlayerData(player.getUniqueId()).stream().map(p -> p.getPetType().name()).toList());
                    break;
                case "give":
                    options.addAll(config.getCaptureItemNames());
                    options.addAll(config.getFruitNames());
                    options.addAll(config.getUtilityItemNames());
                    options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    break;
                case "check":
                case "add":
                case "remove":
                case "revive":
                    options.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
                    break;
                case "debug":
                    options.addAll(List.of("on", "off", "ALL", "AI", "TARGET", "FEEDING", "SUMMON", "COMBAT"));
                    break;
            }
        } else if (args.length == 3) {
            if ("give".equalsIgnoreCase(args[0])) {
                if (Bukkit.getPlayer(args[1]) != null) {
                    options.addAll(config.getCaptureItemNames());
                    options.addAll(config.getFruitNames());
                    options.addAll(config.getUtilityItemNames());
                }
            } else if ("add".equalsIgnoreCase(args[0]) || "remove".equalsIgnoreCase(args[0]) || "revive".equalsIgnoreCase(args[0])) {
                options.addAll(config.getAllCapturablePetTypes().stream().map(Enum::name).toList());
            } else if ("debug".equalsIgnoreCase(args[0])) {
                options.addAll(List.of("on", "off"));
            }
        }

        String cur = args[args.length - 1].toLowerCase(Locale.ROOT);
        for (String opt : options) if (opt.toLowerCase(Locale.ROOT).startsWith(cur)) completions.add(opt);
        return completions;
    }
}