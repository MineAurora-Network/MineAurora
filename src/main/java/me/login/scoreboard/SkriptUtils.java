package me.login.scoreboard;

import ch.njol.skript.Skript;
import ch.njol.skript.variables.Variables;
import org.bukkit.event.Event;

public class SkriptUtils {

    // Private constructor so it can't be instantiated
    private SkriptUtils() {
    }

    /**
     * Gets the value of a Skript variable.
     *
     * @param varName The name of the variable (e.g., "itemsdropped" or "Notch.jumped").
     * @return The variable's value as an Object, or null if not set.
     */
    public static Object getVar(String varName) {
        // The 'null' is for the event, which isn't needed for simple lookups.
        // 'false' means it's not a local variable.
        return Variables.getVariable(varName, null, false);
    }

    /**
     * Sets the value of a Skript variable.
     *
     * @param varName The name of the variable to set.
     * @param value   The value to set it to.
     */
    public static void setVar(String varName, Object value) {
        // The 'null' is for the event, 'false' for global.
        Variables.setVariable(varName, value, null, false);
    }
}