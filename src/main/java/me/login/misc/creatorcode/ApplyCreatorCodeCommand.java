package me.login.misc.creatorcode;

import de.rapha149.signgui.SignGUI;
import me.clip.placeholderapi.PlaceholderAPI;
import me.login.Login; // <-- IMPORT THE LOGIN CLASS
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ApplyCreatorCodeCommand implements CommandExecutor {

    private final CreatorCodeManager manager;
    private final CreatorCodeLogger logger;
    private final Component serverPrefix;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Login plugin; // <-- ADD THIS FIELD

    private final boolean skriptEnabled;
    private final boolean papiEnabled;

    // THIS CONSTRUCTOR IS THE FIX
    public ApplyCreatorCodeCommand(CreatorCodeManager manager, CreatorCodeLogger logger, Component serverPrefix, Login plugin) {
        this.manager = manager;
        this.logger = logger;
        this.serverPrefix = serverPrefix;
        this.plugin = plugin; // <-- ADD THIS LINE
        this.skriptEnabled = Bukkit.getPluginManager().getPlugin("Skript") != null;
        this.papiEnabled = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(mm.deserialize("<red>This command can only be run by a player.</red>"));
            return true;
        }

        if (!player.hasPermission("login.creatorcode.use")) {
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>You do not have permission to use this command.</red>")));
            return true;
        }

        // --- Check Dependencies ---
        if (!skriptEnabled) {
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>The Skript plugin is not enabled. This feature is disabled.</red>")));
            return true;
        }
        if (!papiEnabled) {
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>PlaceholderAPI is not enabled. This feature is disabled.</red>")));
            return true;
        }

        // Open the Sign GUI instead of checking args
        openCreatorCodeSignInput(player);
        return true;
    }

    /**
     * Opens the SignGUI for the player to input their creator code.
     */
    private void openCreatorCodeSignInput(Player player) {
        // We use the LegacyComponentSerializer to set colored lines, just like in your CoinflipMenu
        final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();

        Component line1 = Component.empty();
        Component line2 = Component.text("^^^^^^^^^^^^^^^");
        Component line3 = Component.text("Enter Creator Code", NamedTextColor.GRAY);
        Component line4 = Component.text("Type 'cancel' to exit", NamedTextColor.RED);

        try {
            SignGUI.builder()
                    .setLines(
                            serializer.serialize(line1),
                            serializer.serialize(line2),
                            serializer.serialize(line3),
                            serializer.serialize(line4)
                    )
                    .setHandler((p, result) -> {
                        String codeToApply = result.getLine(0).trim();

                        // Run the logic back on the main thread
                        // THIS LINE IS THE FIX
                        Bukkit.getScheduler().runTask(plugin, () -> {

                            if (codeToApply.isEmpty() || codeToApply.equalsIgnoreCase("cancel")) {
                                p.sendMessage(serverPrefix.append(mm.deserialize("<red>Creator code application cancelled.</red>")));
                                return;
                            }

                            // --- 1. Check if code is valid ---
                            if (!manager.isCodeValid(codeToApply)) {
                                p.sendMessage(serverPrefix.append(mm.deserialize("<red>That is not a valid creator code.</red>")));
                                return;
                            }

                            String code = codeToApply.toLowerCase(); // Use lowercase for comparison and setting

                            // --- 2. Check if player already has a code ---
                            String varName = "credits." + p.getUniqueId() + ".creatorcode";
                            String existingValue = PlaceholderAPI.setPlaceholders(p, "{" + varName + "}");

                            if (existingValue != null && !existingValue.isEmpty() && !existingValue.equals("{" + varName + "}")) {
                                p.sendMessage(serverPrefix.append(mm.deserialize("<red>You have already applied a creator code: </red><yellow>" + existingValue + "</yellow>")));
                                return;
                            }

                            // --- 3. All checks passed. Apply the code ---
                            // --- FIX: Replaced 'sk set' with your requested 'setskriptvariable' command ---
                            String skriptCommand = "setskriptvariable " + p.getName() + " " + code;
                            // --- END FIX ---
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), skriptCommand);

                            p.sendMessage(serverPrefix.append(mm.deserialize("<green>You have successfully applied creator code: </green><yellow>" + code + "</yellow>")));
                            logger.logUsage("Player `" + p.getName() + "` applied creator code: `" + code + "`");
                        });

                        return null; // Close the GUI
                    })
                    .build()
                    .open(player);

        } catch (Exception e) {
            // THIS LINE IS THE FIX
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error opening Creator Code SignGUI", e);
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred while opening the input menu.</red>")));
        }
    }
}