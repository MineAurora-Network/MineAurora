package me.login.misc.holograms;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * Utility class to handle text serialization.
 * Supports MiniMessage tags (<gradient>, <color>, etc.) and
 * legacy color codes (&).
 */
public class TextUtil {

    // MiniMessage serializer for modern formats like <gradient>
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();

    // Legacy serializer for '&' color codes
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacyAmpersand();

    /**
     * Deserializes a string containing MiniMessage and/or legacy codes into a Component.
     *
     * @param text The string to deserialize.
     * @return A Component with all formatting applied.
     */
    public static Component deserialize(String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        // 1. Parse legacy color codes (&a, &c, etc.) into a Component.
        // This will leave MiniMessage tags (<gradient>) as literal text.
        Component legacyParsed = LEGACY_SERIALIZER.deserialize(text);

        // 2. Serialize this component to a MiniMessage string.
        // This converts the legacy-parsed component into a string that
        // MiniMessage can understand, while preserving the literal <gradient> tags.
        String miniMessageString = MINI_MESSAGE.serialize(legacyParsed);

        // 3. Parse the resulting string with MiniMessage.
        // This will now correctly parse the <gradient> tags and all other
        // modern formatting.
        return MINI_MESSAGE.deserialize(miniMessageString);
    }
}