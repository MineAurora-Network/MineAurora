package me.login.moderation.staff.commands;

import me.login.Login;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.Utils;
import me.login.moderation.staff.StaffLogger;
import me.login.moderation.staff.StaffManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ReportCommand implements CommandExecutor {

    private final Login plugin;
    private final ModerationDatabase database;
    private final StaffManager manager;
    private final StaffLogger logger;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public ReportCommand(Login plugin, ModerationDatabase database, StaffManager manager, StaffLogger logger) {
        this.plugin = plugin;
        this.database = database;
        this.manager = manager;
        this.logger = logger;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        // --- /report <player> <reason> ---
        if (label.equalsIgnoreCase("report")) {
            if (!(sender instanceof Player)) { sender.sendMessage("Players only."); return true; }
            Player reporter = (Player) sender;

            if (args.length < 2) {
                sender.sendMessage(Utils.color("&cUsage: /report <player> <reason>"));
                return true;
            }

            Player reported = Bukkit.getPlayer(args[0]);
            if (reported == null) {
                sender.sendMessage(Utils.color("&cPlayer not found or offline."));
                return true;
            }

            if (reported.getUniqueId().equals(reporter.getUniqueId())) {
                sender.sendMessage(Utils.color("&cYou cannot report yourself."));
                return true;
            }

            String reason = String.join(" ", Arrays.copyOfRange(args, 1, args.length));

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                // Save to DB
                database.addReport(reporter.getUniqueId(), reporter.getName(), reported.getUniqueId(), reported.getName(), reason);

                // Build Notification Component
                Component prefix = Utils.getServerPrefix(plugin).append(Component.text(" New Report", NamedTextColor.RED));

                Component reporterComp = Component.text("Reporter: ", NamedTextColor.GRAY)
                        .append(Component.text(reporter.getName(), NamedTextColor.YELLOW)
                                .clickEvent(ClickEvent.runCommand("/tp " + reporter.getName()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to " + reporter.getName()))));

                Component reportedComp = Component.text("Reported: ", NamedTextColor.GRAY)
                        .append(Component.text(reported.getName(), NamedTextColor.RED)
                                .clickEvent(ClickEvent.runCommand("/tp " + reported.getName()))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to teleport to " + reported.getName()))));

                Component reasonComp = Component.text("Reason: ", NamedTextColor.GRAY)
                        .append(Component.text(reason, NamedTextColor.WHITE));

                // Send to Staff
                for (Player staff : Bukkit.getOnlinePlayers()) {
                    if (staff.hasPermission("staff.staff") && manager.isNotificationEnabled(staff.getUniqueId())) {
                        staff.sendMessage(prefix);
                        staff.sendMessage(reporterComp);
                        staff.sendMessage(reportedComp);
                        staff.sendMessage(reasonComp);
                    }
                }

                // Log
                logger.log(StaffLogger.LogType.REPORT, "**REPORT** | " + reporter.getName() + " reported " + reported.getName() + " for: `" + reason + "`");

                reporter.sendMessage(Utils.color("&aReport submitted successfully."));
            });
            return true;
        }

        // --- /reportlist [player] [page] ---
        if (label.equalsIgnoreCase("reportlist")) {
            if (!sender.hasPermission("staff.command.report")) { sender.sendMessage(Utils.color("&cNo permission.")); return true; }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                List<Map<String, Object>> reports;
                String targetName = "All";
                int page = 1;

                // Parse Args: /reportlist or /reportlist <page> or /reportlist <player> <page>
                if (args.length > 0) {
                    // Check if first arg is number (page for all) or player
                    if (args[0].matches("\\d+")) {
                        page = Integer.parseInt(args[0]);
                        reports = database.getAllReports();
                    } else {
                        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
                        targetName = target.getName();
                        reports = database.getReportsForPlayer(target.getUniqueId());
                        if (args.length > 1 && args[1].matches("\\d+")) {
                            page = Integer.parseInt(args[1]);
                        }
                    }
                } else {
                    reports = database.getAllReports();
                }

                if (reports.isEmpty()) {
                    sender.sendMessage(Utils.color("&cNo reports found."));
                    return;
                }

                int totalPages = (int) Math.ceil((double) reports.size() / 10);
                if (page < 1) page = 1;
                if (page > totalPages) page = totalPages;

                int startIndex = (page - 1) * 10;
                int endIndex = Math.min(startIndex + 10, reports.size());

                sender.sendMessage(Utils.color("&e--- Reports (" + targetName + ") Page " + page + "/" + totalPages + " ---"));

                for (int i = startIndex; i < endIndex; i++) {
                    Map<String, Object> r = reports.get(i);
                    String line = String.format("&7[&c%d&7] &e%s &7reported &c%s&7: &f%s",
                            r.get("id"), r.get("reporter_name"), r.get("reported_name"), r.get("reason"));
                    sender.sendMessage(Utils.color(line));
                }

                Component nav = Component.empty();
                if (page > 1) {
                    nav = nav.append(Component.text("<< Prev ", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/reportlist " + (args.length > 0 && !args[0].matches("\\d+") ? args[0] + " " : "") + (page - 1))));
                }
                if (page < totalPages) {
                    nav = nav.append(Component.text(" Next >>", NamedTextColor.YELLOW)
                            .clickEvent(ClickEvent.runCommand("/reportlist " + (args.length > 0 && !args[0].matches("\\d+") ? args[0] + " " : "") + (page + 1))));
                }
                sender.sendMessage(nav);
            });
            return true;
        }

        // --- /reportclear <player> <id> ---
        if (label.equalsIgnoreCase("reportclear")) {
            if (!sender.hasPermission("staff.command.report")) { sender.sendMessage(Utils.color("&cNo permission.")); return true; }
            if (args.length < 2) { sender.sendMessage(Utils.color("&cUsage: /reportclear <player> <id>")); return true; }

            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            int id;
            try { id = Integer.parseInt(args[1]); } catch (NumberFormatException e) { sender.sendMessage(Utils.color("&cInvalid ID.")); return true; }

            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                if (database.deleteReport(target.getUniqueId(), id)) {
                    sender.sendMessage(Utils.color("&aReport #" + id + " cleared for " + target.getName()));
                } else {
                    sender.sendMessage(Utils.color("&cReport not found matching that player and ID."));
                }
            });
            return true;
        }

        return false;
    }
}