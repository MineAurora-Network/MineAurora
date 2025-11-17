package me.login.misc.rtp;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer; // Corrected: Import for legacy colors
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class RTPCommand implements CommandExecutor, TabCompleter {

    private final RTPModule module;
    private final RTPLocationFinder locationFinder;
    private final RTPMenu rtpMenu; // Added menu
    private final List<String> allowedWorlds = Arrays.asList("overworld", "nether", "end");

    public RTPCommand(RTPModule module, RTPLocationFinder locationFinder, RTPMenu rtpMenu) {
        this.module = module;
        this.locationFinder = locationFinder;
        this.rtpMenu = rtpMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        Component prefix = MiniMessage.miniMessage().deserialize(module.getServerPrefix());

        if (!player.hasPermission("mineaurora.rtp")) {
            player.sendMessage(prefix.append(Component.text(" You don't have permission to use this command.").color(NamedTextColor.RED)));
            return true;
        }

        // Cooldown Check
        long remainingCooldown = module.getCooldownManager().getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            player.sendMessage(prefix.append(Component.text(" You must wait " + remainingCooldown + " seconds before using RTP again.").color(NamedTextColor.RED)));
            return true;
        }

        if (args.length == 0) {
            // No arguments, open the GUI
            rtpMenu.open(player);
            return true;
        }

        String worldAlias = args[0].toLowerCase();
        if (!allowedWorlds.contains(worldAlias)) {
            player.sendMessage(prefix.append(Component.text(" Invalid world. Use: overworld, nether, or end.").color(NamedTextColor.RED)));
            return true;
        }

        World world;
        switch (worldAlias) {
            case "nether":
                world = Bukkit.getWorld(module.getPlugin().getConfig().getString("worlds.nether", "world_nether"));
                break;
            case "end":
                world = Bukkit.getWorld(module.getPlugin().getConfig().getString("worlds.end", "world_the_end"));
                break;
            case "overworld":
            default:
                world = Bukkit.getWorld(module.getPlugin().getConfig().getString("worlds.overworld", "world"));
                break;
        }

        if (world == null) {
            player.sendMessage(prefix.append(Component.text(" The " + worldAlias + " world could not be found or isn't loaded.").color(NamedTextColor.RED)));
            module.getLogger().log("RTP Error: World '" + worldAlias + "' is null.");
            return true;
        }

        // Start the teleport logic
        startTeleport(player, world, worldAlias, module);
        return true;
    }

    /**
     * Static method to handle the actual teleport logic, usable by the command and GUI
     */
    public static void startTeleport(Player player, World world, String worldAlias, RTPModule module) {
        Component prefix = MiniMessage.miniMessage().deserialize(module.getServerPrefix());

        // Cooldown Check (for GUI)
        long remainingCooldown = module.getCooldownManager().getRemainingCooldown(player);
        if (remainingCooldown > 0) {
            player.sendMessage(prefix.append(Component.text(" You must wait " + remainingCooldown + " seconds before using RTP again.").color(NamedTextColor.RED)));
            return;
        }

        player.sendMessage(prefix.append(Component.text(" Searching for a safe location in the " + worldAlias + "...").color(NamedTextColor.GRAY)));

        // Get the finder from the module
        RTPLocationFinder locationFinder = module.getLocationFinder();

        // Run the async location finder with a 5-second timeout
        locationFinder.findSafeLocationAsync(world)
                .orTimeout(5, TimeUnit.SECONDS) // Added 5-second timeout
                .whenComplete((safeLocation, ex) -> {
                    // Bridge to main thread to send messages and teleport
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (!player.isOnline()) return; // Player logged off

                            // Handle exceptions (like TimeoutException)
                            if (ex != null) {
                                if (ex instanceof TimeoutException) {
                                    player.sendMessage(prefix.append(Component.text(" Could not find a safe location in time. Please try again.").color(NamedTextColor.RED)));
                                } else {
                                    player.sendMessage(prefix.append(Component.text(" An error occurred. Please try again.").color(NamedTextColor.RED)));
                                    module.getLogger().log("RTP Error: " + ex.getMessage());
                                }
                                return;
                            }

                            // Handle failure to find a location
                            if (safeLocation == null) {
                                player.sendMessage(prefix.append(Component.text(" Could not find a safe location after " + RTPLocationFinder.MAX_TRIES + " tries. Please try again.").color(NamedTextColor.RED)));
                                return;
                            }

                            // Success! Teleport on main thread.
                            player.teleportAsync(safeLocation);

                            // Give 3 seconds of darkness (60 ticks)
                            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 1));

                            // Send the title using the correct legacy serializer
                            final Title.Times times = Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(2), Duration.ofMillis(500));
                            final Title title = Title.title(
                                    // Corrected: Use LegacyComponentSerializer for server_prefix_2
                                    LegacyComponentSerializer.legacyAmpersand().deserialize(module.getServerPrefix2()),
                                    Component.text("You have been teleported!").color(NamedTextColor.WHITE),
                                    times
                            );
                            player.showTitle(title);

                            // Set cooldown on success
                            module.getCooldownManager().setCooldown(player); // <-- ADDED

                            // Log to Discord
                            module.getLogger().logRTP(player.getName(), safeLocation);
                        }
                    }.runTask(module.getPlugin());
                });
    }


    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return allowedWorlds.stream()
                    .filter(world -> world.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return null;
    }
}