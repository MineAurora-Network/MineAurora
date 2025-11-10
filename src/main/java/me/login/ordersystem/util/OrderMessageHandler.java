package me.login.ordersystem.util;

import me.login.Login;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;

/**
 * Handles all player-facing messages for the Order System,
 * ensuring correct prefixes and Kyori formatting.
 * (Points 1, 2, 3, 6)
 */
public class OrderMessageHandler {

    private final Login plugin;
    private final MiniMessage miniMessage;
    private final String serverPrefix;
    private final String alertPrefix;

    public OrderMessageHandler(Login plugin, MiniMessage miniMessage) {
        this.plugin = plugin;
        this.miniMessage = miniMessage;
        // (Point 2)
        this.serverPrefix = plugin.getConfig().getString("server_prefix", "<gray>[<aqua>Server</aqua>]</gray> ");
        // (Point 3)
        this.alertPrefix = plugin.getConfig().getString("alert_prefix", "<dark_red>[<red>ALERT</red>]<gray> ");
    }

    /**
     * Sends a standard message to a player with the server_prefix.
     * (Point 2)
     */
    public void sendMessage(Player player, String message) {
        player.sendMessage(deserialize(serverPrefix + message));
    }

    /**
     * Sends a message with the server_prefix, allowing for newlines.
     * (Point 6)
     */
    public void sendMultiLine(Player player, String message) {
        String processedMessage = message.replace("%nl%", "<newline>");
        player.sendMessage(deserialize(serverPrefix + processedMessage));
    }

    /**
     * Sends an alert message to a staff member with the alert_prefix.
     * (Point 3)
     */
    public void sendAlert(Player player, String message) {
        player.sendMessage(deserialize(alertPrefix + message));
    }

    /**
     * Sends a clickable alert message to a staff member.
     * (Point 4)
     */
    public void sendClickableAlert(Player staff, Component message, String command) {
        Component fullMessage = deserialize(alertPrefix)
                .append(message)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand(command))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(Component.text("Click to inspect this order")));

        staff.sendMessage(fullMessage);
    }

    /**
     * Sends a raw message to a player with no prefix.
     */
    public void sendRaw(Player player, String message) {
        player.sendMessage(deserialize(message));
    }

    /**
     * Gets the server prefix as a deserialized component.
     */
    public Component getServerPrefix() {
        return deserialize(serverPrefix);
    }

    /**
     * Deserializes a MiniMessage string into a Component.
     */
    public Component deserialize(String text) {
        return miniMessage.deserialize(text);
    }

    /**
     * Deserializes a MiniMessage string with a single placeholder.
     */
    public Component deserialize(String text, String key, String value) {
        return miniMessage.deserialize(text, Placeholder.unparsed(key, value));
    }
}