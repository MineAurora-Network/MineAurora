package me.login.premiumfeatures.creatorcode;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
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
    private final Login plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ApplyCreatorCodeCommand(CreatorCodeManager manager, CreatorCodeLogger logger, Component serverPrefix, Login plugin) {
        this.manager = manager;
        this.logger = logger;
        this.serverPrefix = serverPrefix;
        this.plugin = plugin;
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

        // Open the Sign GUI
        openCreatorCodeSignInput(player);
        return true;
    }

    private void openCreatorCodeSignInput(Player player) {
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

                        // Run logic on main thread then async for DB if needed
                        Bukkit.getScheduler().runTask(plugin, () -> {

                            if (codeToApply.isEmpty() || codeToApply.equalsIgnoreCase("cancel")) {
                                p.sendMessage(serverPrefix.append(mm.deserialize("<red>Creator code application cancelled.</red>")));
                                return;
                            }

                            // 1. Check validity
                            if (!manager.isCodeValid(codeToApply)) {
                                p.sendMessage(serverPrefix.append(mm.deserialize("<red>That is not a valid creator code.</red>")));
                                return;
                            }

                            String code = codeToApply.toLowerCase();

                            // 2. Async check for existing code to prevent lag
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                String existingCode = manager.getPlayerCode(p.getUniqueId());

                                if (existingCode != null) {
                                    // Player already has a code
                                    Component msg = serverPrefix.append(mm.deserialize("<red>You have already applied a creator code: </red><yellow>" + existingCode + "</yellow>"));
                                    p.sendMessage(msg);
                                    return;
                                }

                                // 3. Apply Code (Save to DB)
                                manager.setPlayerCode(p.getUniqueId(), code);

                                // Notify Player
                                Component successMsg = serverPrefix.append(mm.deserialize("<green>You have successfully applied creator code: </green><yellow>" + code + "</yellow>"));
                                p.sendMessage(successMsg);

                                // Log
                                logger.logUsage("Player `" + p.getName() + "` applied creator code: `" + code + "`");
                            });
                        });
                        return null;
                    })
                    .build()
                    .open(player);

        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error opening Creator Code SignGUI", e);
            player.sendMessage(serverPrefix.append(mm.deserialize("<red>An error occurred while opening the input menu.</red>")));
        }
    }
}