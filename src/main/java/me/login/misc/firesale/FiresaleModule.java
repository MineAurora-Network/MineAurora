package me.login.misc.firesale;

import me.login.Login;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import net.citizensnpcs.api.npc.NPC;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.logging.Level;

/**
 * Robust FiresaleModule that uses reflection to adapt to different constructor signatures
 * across versions / generator outputs. This prevents compile-time mismatches and attempts
 * sensible fallbacks.
 */
public class FiresaleModule {
    private final Login plugin;

    // Instances (kept as Object so reflection can be used safely)
    private Object firesaleDatabase;
    private Object firesaleItemManager;
    private Object firesaleManager;
    private Object firesaleLogger;
    private Object firesaleListener;
    private Object firesaleGUI;
    private NPC firesaleNpc;

    public FiresaleModule(Login plugin) {
        this.plugin = plugin;
    }

    /**
     * Initialize the module. This method tries several constructor patterns for each
     * helper class, so it should work regardless of small signature differences.
     */
    public boolean init() {
        plugin.getLogger().info("Initializing FiresaleModule (flexible loader)...");

        try {
            // 1) Load FiresaleDatabase
            firesaleDatabase = instantiateWithFallback("me.login.misc.firesale.database.FiresaleDatabase",
                    new Class[][] { { Login.class }, {} }, // try (Login) then ()
                    new Object[][] { { plugin }, {} });
            // If class provides connect(), try to call it (safe)
            callIfExists(firesaleDatabase, "connect");

            // 2) Item manager (commonly: new FiresaleItemManager(plugin))
            firesaleItemManager = instantiateWithFallback("me.login.misc.firesale.item.FiresaleItemManager",
                    new Class[][] { { Login.class }, {} },
                    new Object[][] { { plugin }, {} });

            // 3) Logger (common patterns: (Login, FiresaleDatabase) or (Login) or (FiresaleDatabase))
            firesaleLogger = instantiateWithFallback("me.login.misc.firesale.FiresaleLogger",
                    new Class[][] { { Login.class, firesaleDatabase != null ? firesaleDatabase.getClass() : Object.class },
                            { Login.class },
                            { firesaleDatabase != null ? firesaleDatabase.getClass() : Object.class } },
                    new Object[][] { { plugin, firesaleDatabase }, { plugin }, { firesaleDatabase } });

            // 4) Manager (try common signatures)
            firesaleManager = instantiateWithFallback("me.login.misc.firesale.FiresaleManager",
                    new Class[][] {
                            { Login.class, firesaleDatabase != null ? firesaleDatabase.getClass() : Object.class,
                                    firesaleLogger != null ? firesaleLogger.getClass() : Object.class,
                                    firesaleItemManager != null ? firesaleItemManager.getClass() : Object.class },
                            { Login.class, firesaleDatabase != null ? firesaleDatabase.getClass() : Object.class,
                                    firesaleItemManager != null ? firesaleItemManager.getClass() : Object.class },
                            { Login.class, firesaleDatabase != null ? firesaleDatabase.getClass() : Object.class }
                    },
                    new Object[][] {
                            { plugin, firesaleDatabase, firesaleLogger, firesaleItemManager },
                            { plugin, firesaleDatabase, firesaleItemManager },
                            { plugin, firesaleDatabase }
                    });

            // 5) Listener (often (Login, FiresaleManager) or (Login) or (FiresaleManager))
            firesaleListener = instantiateWithFallback("me.login.misc.firesale.FiresaleListener",
                    new Class[][] {
                            { Login.class, firesaleManager != null ? firesaleManager.getClass() : Object.class },
                            { Login.class },
                            { firesaleManager != null ? firesaleManager.getClass() : Object.class }
                    },
                    new Object[][] {
                            { plugin, firesaleManager },
                            { plugin },
                            { firesaleManager }
                    });
            // If listener implements Listener, register it
            if (firesaleListener != null && firesaleListener instanceof Listener) {
                Bukkit.getPluginManager().registerEvents((Listener) firesaleListener, plugin);
            } else {
                // Some generated listeners may not implement Listener directly; try reflection-based registration
                if (firesaleListener != null) {
                    try {
                        Class<?> listenerIface = Class.forName("org.bukkit.event.Listener");
                        if (listenerIface.isInstance(firesaleListener)) {
                            Bukkit.getPluginManager().registerEvents((Listener) firesaleListener, plugin);
                        }
                    } catch (ClassNotFoundException ignored) {}
                }
            }

            // 6) GUI (commonly (Login, FiresaleManager) or (FiresaleManager))
            firesaleGUI = instantiateWithFallback("me.login.misc.firesale.gui.FiresaleGUI",
                    new Class[][] {
                            { Login.class, firesaleManager != null ? firesaleManager.getClass() : Object.class },
                            { firesaleManager != null ? firesaleManager.getClass() : Object.class },
                            { Login.class }
                    },
                    new Object[][] {
                            { plugin, firesaleManager },
                            { firesaleManager },
                            { plugin }
                    });

            // 7) NPC setup via Citizens
            int npcId = plugin.getConfig().getInt("firesale-npc-id", -1);
            if (npcId != -1) {
                try {
                    this.firesaleNpc = net.citizensnpcs.api.CitizensAPI.getNPCRegistry().getById(npcId);
                    if (this.firesaleNpc != null) {
                        // try to call manager.setNpc(NPC) via reflection if manager has it
                        callIfExists(firesaleManager, "setNpc", this.firesaleNpc);
                        // start hologram updater (flexible)
                        startHologramUpdaterFlexible();
                        plugin.getLogger().info("Firesale NPC linked to ID " + npcId);
                    } else {
                        plugin.getLogger().warning("Firesale NPC ID " + npcId + " not found.");
                    }
                } catch (NoClassDefFoundError e) {
                    plugin.getLogger().warning("Citizens not present: cannot attach Firesale NPC.");
                }
            } else {
                plugin.getLogger().info("No firesale-npc-id configured; skipping NPC/hologram setup.");
            }

            try {
                Class<?> commandClass = Class.forName("me.login.misc.firesale.command.FiresaleCommand");
                Constructor<?> cmdCtor = commandClass.getConstructor(
                        Login.class,
                        Class.forName("me.login.misc.firesale.FiresaleManager"),
                        Class.forName("me.login.misc.firesale.item.FiresaleItemManager")
                );
                Object cmdInstance = cmdCtor.newInstance(plugin, firesaleManager, firesaleItemManager);

                org.bukkit.command.PluginCommand firesaleCmd = plugin.getCommand("firesale");
                if (firesaleCmd != null) {
                    firesaleCmd.setExecutor((org.bukkit.command.CommandExecutor) cmdInstance);
                    firesaleCmd.setTabCompleter((org.bukkit.command.TabCompleter) cmdInstance);
                    plugin.getLogger().info("Registered /firesale command successfully.");
                } else {
                    plugin.getLogger().warning("Command 'firesale' not found in plugin.yml — please add it!");
                }

            } catch (Throwable t) {
                plugin.getLogger().warning("Failed to register /firesale command: " + t.getMessage());
            }

            plugin.getLogger().info("FiresaleModule initialized successfully.");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "FiresaleModule initialization failed: " + t.getMessage(), t);
            return false;
        }
    }

    /**
     * Attempt to start the hologram updater by trying multiple constructor signatures.
     * If an updater implements BukkitRunnable, schedule it.
     */
    private void startHologramUpdaterFlexible() {
        try {
            // possible constructor parameter patterns
            Class<?> updaterClass = Class.forName("me.login.misc.firesale.npc.FiresaleHologramUpdater");
            Constructor<?>[] ctors = updaterClass.getConstructors();
            Object updaterInstance = null;

            // Try common param combos in order:
            Object[][] tryParams = new Object[][] {
                    { plugin, firesaleManager, firesaleNpc },           // (Login, FiresaleManager, NPC)
                    { firesaleManager, firesaleNpc },                  // (FiresaleManager, NPC)
                    { plugin, firesaleManager },                       // (Login, FiresaleManager)
                    { firesaleManager }                                // (FiresaleManager)
            };

            for (Object[] params : tryParams) {
                Class<?>[] paramTypes = new Class<?>[params.length];
                boolean ok = true;
                for (int i = 0; i < params.length; i++) {
                    if (params[i] == null) { ok = false; break; }
                    paramTypes[i] = params[i].getClass();
                }
                if (!ok) continue;
                try {
                    Constructor<?> c = findMatchingConstructor(updaterClass, paramTypes);
                    if (c != null) {
                        updaterInstance = c.newInstance(params);
                        break;
                    }
                } catch (Throwable ignored) {}
            }

            if (updaterInstance == null) {
                plugin.getLogger().info("No matching FiresaleHologramUpdater constructor found; skipping hologram updater.");
                return;
            }

            // If it's a BukkitRunnable, schedule it with configured interval
            if (updaterInstance instanceof BukkitRunnable runnable) {
                int interval = plugin.getConfig().getInt("firesale-hologram-update-interval", 5);
                runnable.runTaskTimer(plugin, 0L, interval * 20L);
                plugin.getLogger().info("Started FiresaleHologramUpdater (BukkitRunnable).");
                return;
            }

            // Otherwise, try to find runTaskTimer method via reflection
            Method runTaskTimerMethod = null;
            for (Method m : updaterInstance.getClass().getMethods()) {
                if (m.getName().equals("runTaskTimer")) {
                    runTaskTimerMethod = m;
                    break;
                }
            }
            if (runTaskTimerMethod != null) {
                int interval = plugin.getConfig().getInt("firesale-hologram-update-interval", 5);
                // runTaskTimer(plugin, long, long)
                try {
                    runTaskTimerMethod.invoke(updaterInstance, plugin, 0L, interval * 20L);
                    plugin.getLogger().info("Started FiresaleHologramUpdater via reflection runTaskTimer.");
                } catch (Throwable e) {
                    plugin.getLogger().warning("Failed to invoke runTaskTimer on FiresaleHologramUpdater: " + e.getMessage());
                }
                return;
            }

            plugin.getLogger().info("FiresaleHologramUpdater found but not scheduled (no runnable/runner method).");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("FiresaleHologramUpdater class not present; skipping hologram updater.");
        } catch (Throwable t) {
            plugin.getLogger().warning("Error while starting hologram updater: " + t.getMessage());
        }
    }

    /* -------------------- Reflection utilities -------------------- */

    /**
     * Try a list of constructor parameter patterns; returns instance or null.
     *
     * @param className    fully-qualified class name
     * @param tryParamSets list of parameter type arrays to try (Class[][])
     * @param tryArgs      list of argument arrays (Object[][]) matching tryParamSets
     */
    private Object instantiateWithFallback(String className, Class<?>[][] tryParamSets, Object[][] tryArgs) {
        try {
            Class<?> clazz = Class.forName(className);
            for (int i = 0; i < tryParamSets.length; i++) {
                Class<?>[] paramTypes = tryParamSets[i];
                Object[] args = tryArgs[i];
                Constructor<?> ctor = findMatchingConstructor(clazz, paramTypes);
                if (ctor != null) {
                    try {
                        return ctor.newInstance(args);
                    } catch (Throwable t) {
                        // try next pattern
                    }
                }
            }
            // Last resort: try no-arg constructor
            try {
                Constructor<?> noArg = clazz.getDeclaredConstructor();
                noArg.setAccessible(true);
                return noArg.newInstance();
            } catch (Throwable ignored) {}
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("Optional class not found: " + className);
        }
        return null;
    }

    /**
     * Finds a constructor in clazz that matches the provided parameter types,
     * but allows assignable matches (e.g., interface/superclass).
     */
    private Constructor<?> findMatchingConstructor(Class<?> clazz, Class<?>[] requestedParamTypes) {
        for (Constructor<?> ctor : clazz.getConstructors()) {
            Class<?>[] ctorParams = ctor.getParameterTypes();
            if (ctorParams.length != requestedParamTypes.length) continue;
            boolean ok = true;
            for (int i = 0; i < ctorParams.length; i++) {
                if (!ctorParams[i].isAssignableFrom(requestedParamTypes[i])) {
                    ok = false;
                    break;
                }
            }
            if (ok) return ctor;
        }
        return null;
    }

    /**
     * Call a method if it exists (no args).
     */
    private void callIfExists(Object target, String methodName, Object... args) {
        if (target == null) return;
        try {
            Class<?> clazz = target.getClass();
            Method[] methods = clazz.getMethods();
            for (Method m : methods) {
                if (!m.getName().equals(methodName)) continue;
                // quick match by parameter count
                if (m.getParameterCount() != args.length) continue;
                try {
                    m.invoke(target, args);
                    return;
                } catch (Throwable t) {
                    // ignore and continue to find another overload
                }
            }
        } catch (Throwable ignored) {}
    }

    /* -------------------- Shutdown -------------------- */

    public void disable() {
        plugin.getLogger().info("Disabling FiresaleModule...");
        // Try a variety of close/disconnect method names on DB
        if (firesaleDatabase != null) {
            callIfExists(firesaleDatabase, "close");
            callIfExists(firesaleDatabase, "disconnect");
            callIfExists(firesaleDatabase, "shutdown");
        }
    }

    /* -------------------- Small getters -------------------- */
    public Object getManager() {
        return firesaleManager;
    }
}
