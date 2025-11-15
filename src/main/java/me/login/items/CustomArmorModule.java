package me.login.items;

import me.login.Login;
import me.login.items.anvil.AnvilCommand;
import me.login.items.anvil.DyeAnvilMenu;

public class CustomArmorModule {

    private final Login plugin;
    private ArmorManager armorManager;
    private ArmorLogger armorLogger;
    private DyeAnvilMenu dyeAnvilMenu;

    public CustomArmorModule(Login plugin) {
        this.plugin = plugin;
    }

    public void enable() {
        this.armorManager = new ArmorManager(plugin);
        this.armorLogger = new ArmorLogger(plugin);

        // Initialize Anvil System
        this.dyeAnvilMenu = new DyeAnvilMenu(plugin, armorManager);
        plugin.getServer().getPluginManager().registerEvents(dyeAnvilMenu, plugin);
        plugin.getCommand("anvil").setExecutor(new AnvilCommand(dyeAnvilMenu));

        // Setup Armor Command
        ArmorsCommand armorsCommand = new ArmorsCommand(plugin, armorManager, armorLogger);
        plugin.getCommand("armourgive").setExecutor(armorsCommand);
        plugin.getCommand("armourgive").setTabCompleter(armorsCommand);

        // Register Armor Listener (and pass ArmorManager to it)
        plugin.getServer().getPluginManager().registerEvents(new ArmorListener(plugin, armorManager), plugin);

        plugin.getLogger().info("Custom Armor Module enabled.");
    }

    public ArmorManager getArmorManager() {
        return armorManager;
    }
}