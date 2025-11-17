package me.login.misc.hologram;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class HologramTextParser {

    /**
     * Parses a string supporting MiniMessage (gradients, hex),
     * and custom placeholders like %nl%.
     */
    public static Component parse(String text) {
        if (text == null) {
            return Component.empty();
        }

        // Replace custom placeholders
        text = text.replace("%nl%", "\n");

        //
        // --- THIS IS THE FIX ---
        //
        // We were incorrectly using LegacyComponentSerializer here, which stripped
        // all the MiniMessage tags like <gray> and <green>.
        //
        // This *only* uses the MiniMessage parser, which is correct
        // for your config.yml format.
        //
        return MiniMessage.miniMessage().deserialize(text);
    }
}