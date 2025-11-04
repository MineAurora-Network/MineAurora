package me.login;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import me.login.discordlinking.*;
// --- MODIFIED IMPORTS ---
import me.login.discordcommand.DiscordCommandManager;
import me.login.discordcommand.DiscordCommandRegistrar;
import me.login.discordcommand.DiscordModConfig;
import me.login.discordcommand.DiscordModCommands;
import me.login.discordcommand.DiscordRankCommand;
// --- END MODIFIED IMPORTS ---
import me.login.loginsystem.*;
import me.login.ordersystem.*;
import me.login.DamageIndicator;
import me.login.scoreboard.ScoreboardManager;
import me.login.clearlag.CleanupTask;
import me.login.clearlag.HopperLimit;
import me.login.clearlag.LagClearCommand;
import me.login.clearlag.TPSWatcher;
import me.login.clearlag.ArmorStandLimit;
import me.login.clearlag.LagClearConfig;
import me.login.clearlag.LagClearLogger; // <-- 1. IMPORT ADDED
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.login.coinflip.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.ChatColor;
import me.login.leaderboards.LeaderboardCommand;
import me.login.leaderboards.LeaderboardDisplayManager;
import me.login.leaderboards.LeaderboardUpdateTask;
import org.bukkit.scheduler.BukkitTask;
import me.login.leaderboards.KillLeaderboardCommand;
import me.login.leaderboards.LeaderboardProtectionListener;

// --- ADDED FOR COINFLIP ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.List;
import java.util.Map; // --- STAFF SYSTEM ADDITION ---
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap; // --- STAFF SYSTEM ADDITION ---
import java.util.stream.Collectors;

// --- STAFF SYSTEM IMPORTS ---
import me.login.moderation.ModerationDatabase;
import me.login.moderation.ModerationListener;
import me.login.moderation.BanCommand;
import me.login.moderation.CheckInvCommand;
import me.login.moderation.MuteCommand;
// --- END STAFF SYSTEM IMPORTS ---

// --- LIFESTEAL IMPORTS ---
import me.login.lifesteal.*;
import net.luckperms.api.LuckPerms;
// --- END LIFESTEAL IMPORTS ---


public class Login extends JavaPlugin implements Listener {
    private DiscordLinking discordLinking;
    private DiscordLinkDatabase discordLinkDatabase;
    private WebhookClient linkWebhookClient;
    private LoginSystem loginSystem;
    private LoginDatabase loginDatabase;
    private WebhookClient loginWebhookClient;
    private OrdersDatabase ordersDatabase;
    private OrderSystem orderSystem;
    private WebhookClient orderWebhookClient;
    private OrderMenu orderMenu;
    private OrderFilling orderFilling;
    private OrderManage orderManage;
    // --- (This was in your file, preserving it) ---
    private OrderAdminMenu orderAdminMenu;
    // --- END ---
    private DamageIndicator damageIndicator;
    private int defaultOrderLimit;
    private ScoreboardManager scoreboardManager;
    private CoinflipDatabase coinflipDatabase;
    private CoinflipSystem coinflipSystem;
    private CoinflipMenu coinflipMenu;
    private CoinflipManageMenu coinflipManageMenu;
    private WebhookClient coinflipWebhookClient;
    private Economy vaultEconomy = null;
    private LeaderboardDisplayManager leaderboardManager;
    private BukkitTask leaderboardUpdateTask;

    private String coinflipPrefixString;

    // --- STAFF SYSTEM ADDITIONS ---
    private ModerationDatabase moderationDatabase;
    private WebhookClient staffWebhookClient;
    // Maps to track who is viewing whose inventory
    // Key: Staff UUID, Value: Target UUID
    private final Map<UUID, UUID> viewingInventories = new ConcurrentHashMap<>();
    // Key: Staff UUID, Value: true (if admin)
    private final Map<UUID, Boolean> adminCheckMap = new ConcurrentHashMap<>();

    // --- NEW DISCORD MOD FIELD ---
    private DiscordModConfig discordModConfig;
    private WebhookClient discordStaffLogWebhook; // <-- NEW WEBHOOK

    // --- NEW LAGCLEAR FIELD ---
    private LagClearConfig lagClearConfig;
    private LagClearLogger lagClearLogger; // <-- FIELD ADDED
    // --- END NEW FIELD ---

    // --- LIFESTEAL FIELDS ---
    private DatabaseManager databaseManager;
    private ItemManager itemManager;
    private LifestealManager lifestealManager;
    private DeadPlayerManager deadPlayerManager;
    private ReviveMenu reviveMenu;
    private CombatLogManager combatLogManager;
    private LuckPerms luckPermsApi;
    // --- END LIFESTEAL FIELDS ---


    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("items.yml", false);

        this.lagClearConfig = new LagClearConfig(this);
        this.discordModConfig = new DiscordModConfig(this);
        loadCoinflipConfig();

        this.defaultOrderLimit = getConfig().getInt("order-system.default-order-limit", 3);

        if (!setupEconomy()) {
            disableWithError("Vault dependency not found or no Economy plugin detected! Coinflip and Order systems require Vault.");
            return;
        }

        if (getServer().getPluginManager().getPlugin("LuckPerms") != null) {
            RegisteredServiceProvider<LuckPerms> provider = Bukkit.getServicesManager().getRegistration(LuckPerms.class);
            if (provider != null) {
                this.luckPermsApi = provider.getProvider();
                getLogger().info("Successfully hooked into LuckPerms.");
            }
        } else {
            getLogger().warning("LuckPerms not found! /sethearts rank check will not work.");
            this.luckPermsApi = null;
        }

        this.discordLinkDatabase = new DiscordLinkDatabase(this);
        discordLinkDatabase.connect();
        if (discordLinkDatabase.getConnection() == null) { disableWithError("Link DB failed."); return; }

        this.loginDatabase = new LoginDatabase(this);
        loginDatabase.connect();
        if (loginDatabase.getConnection() == null) { disableWithError("Login DB failed."); return; }

        this.ordersDatabase = new OrdersDatabase(this);
        ordersDatabase.connect();
        if (ordersDatabase.getConnection() == null) { disableWithError("Orders DB failed."); return; }

        this.coinflipDatabase = new CoinflipDatabase(this);
        coinflipDatabase.connect();
        if (coinflipDatabase.getConnection() == null) { disableWithError("Coinflip DB failed."); return; }

        this.moderationDatabase = new ModerationDatabase(this);

        this.databaseManager = new DatabaseManager(this);
        if (!this.databaseManager.initializeDatabase()) {
            disableWithError("Failed to initialize Lifesteal database! Disabling plugin.");
            return;
        }
        this.databaseManager.createTables();

        this.linkWebhookClient = initializeWebhook("link-system-log-webhook", "Link-System");
        this.loginWebhookClient = initializeWebhook("login-system-log-webhook", "Login-System");
        this.orderWebhookClient = initializeWebhook("order-system-log-webhook", "Order-System");
        this.coinflipWebhookClient = initializeWebhook("coinflip-system-log-webhook", "Coinflip-System");
        this.staffWebhookClient = initializeWebhook("staff-system-log-webhook", "Staff-System");
        this.discordStaffLogWebhook = initializeWebhook("discord-staff-log-webhook", "Discord-Staff-System");

        this.loginSystem = new LoginSystem(this, loginDatabase, discordLinkDatabase, loginWebhookClient);
        getServer().getPluginManager().registerEvents(loginSystem, this);

        this.discordLinking = new DiscordLinking(this, linkWebhookClient, discordModConfig);

        this.damageIndicator = new DamageIndicator(this);
        getServer().getPluginManager().registerEvents(damageIndicator, this);

        this.orderSystem = new OrderSystem(this, ordersDatabase, orderWebhookClient);
        this.orderFilling = new OrderFilling(this, orderSystem);
        this.orderMenu = new OrderMenu(this, orderSystem, orderFilling);
        this.orderManage = new OrderManage(this, orderSystem);
        this.orderAdminMenu = new OrderAdminMenu(this, orderSystem);

        this.coinflipMenu = new CoinflipMenu(this, coinflipDatabase, vaultEconomy);
        this.coinflipSystem = new CoinflipSystem(this, coinflipDatabase, vaultEconomy, coinflipWebhookClient, coinflipMenu.getPlayersChallengingSet());
        this.coinflipMenu.setCoinflipSystem(coinflipSystem);
        this.coinflipManageMenu = new CoinflipManageMenu(this, coinflipDatabase, vaultEconomy);

        if (getServer().getPluginManager().getPlugin("Citizens") != null) {
            getServer().getPluginManager().registerEvents(new CoinflipNpcListener(this, coinflipMenu), this);
            getLogger().info("Citizens found, Coinflip NPC listener registered.");
        } else {
            getLogger().warning("Citizens plugin not found! Coinflip NPC click will not work.");
        }
        getServer().getPluginManager().registerEvents(coinflipMenu, this);
        getServer().getPluginManager().registerEvents(coinflipManageMenu, this);

        getServer().getPluginManager().registerEvents(orderAdminMenu, this);
        getServer().getPluginManager().registerEvents(new ModerationListener(this, moderationDatabase), this);

        this.leaderboardManager = new LeaderboardDisplayManager(this);
        long delay = 20L * 10;
        long refreshTicks = 20L * getConfig().getLong("leaderboards.refresh-seconds", 60);
        this.leaderboardUpdateTask = new LeaderboardUpdateTask(this.leaderboardManager).runTaskTimer(this, delay, refreshTicks);

        this.itemManager = new ItemManager(this);
        this.lifestealManager = new LifestealManager(this, itemManager, databaseManager);
        this.deadPlayerManager = new DeadPlayerManager(this, databaseManager);
        this.reviveMenu = new ReviveMenu(this, itemManager, deadPlayerManager);
        this.combatLogManager = new CombatLogManager(this, itemManager, lifestealManager);

        getServer().getPluginManager().registerEvents(new LifestealListener(this, itemManager, lifestealManager, deadPlayerManager, reviveMenu), this);
        getServer().getPluginManager().registerEvents(combatLogManager, this);

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getPluginManager().registerEvents(new LeaderboardProtectionListener(this.leaderboardManager), this);

        getLogger().info("Initializing ClearLag components...");
        getServer().getPluginManager().registerEvents(new HopperLimit(this), this);
        getServer().getPluginManager().registerEvents(new ArmorStandLimit(this), this);
        long countdownInterval = 20L;
        new CleanupTask(this, this.lagClearConfig).runTaskTimer(this, countdownInterval, countdownInterval);
        long tpsCheckInterval = 200L;
        new TPSWatcher(this).runTaskTimer(this, tpsCheckInterval, tpsCheckInterval);
        getLogger().info("ClearLag components enabled.");

        this.scoreboardManager = new ScoreboardManager(this);

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found! Scoreboard placeholders will not work.");
        }
        if (getServer().getPluginManager().getPlugin("Skript") == null) {
            getLogger().warning("Skript not found! Scoreboard variables via SkriptUtils will not work.");
        }

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEnabled() || Bukkit.isStopping()) {
                getLogger().warning("Plugin disabled before Discord startup. Skipping bot init.");
                return;
            }
            Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
                try {
                    String botToken = getConfig().getString("bot-token");
                    if (isConfigValueInvalid(botToken, "YOUR_MAIN_BOT_TOKEN_HERE")) {
                        disableWithError("Bot token invalid.");
                        return;
                    }

                    discordLinking.startBot(botToken);
                    getLogger().info("Discord bot connected successfully and ready.");

                    if (!isEnabled() || Bukkit.isStopping()) {
                        getLogger().warning("Plugin disabled during Discord startup. Shutting down JDA.");
                        if (discordLinking.getJDA() != null) {
                            discordLinking.getJDA().shutdownNow();
                        }
                        return;
                    }

                    Bukkit.getScheduler().runTask(this, () -> {
                        try {
                            if (!isEnabled() || Bukkit.isStopping()) {
                                getLogger().warning("Plugin disabled before post-start steps. Aborting.");
                                if (discordLinking.getJDA() != null) {
                                    discordLinking.getJDA().shutdownNow();
                                }
                                return;
                            }
                            DiscordCommandRegistrar.register(discordLinking.getJDA());
                            getLogger().info("Discord slash command listeners registered.");

                            long lagClearChannelId = getConfig().getLong("lagclear-log-channel-id", 0);
                            this.lagClearLogger = this.lagClearLogger = new LagClearLogger(this);
                            getLogger().info("LagClear Logger wired to shared JDA.");
                        } catch (Throwable postErr) {
                            getLogger().severe("Post-start Discord steps failed: " + postErr.getMessage());
                            postErr.printStackTrace();
                            if (discordLinking.getJDA() != null) {
                                discordLinking.getJDA().shutdownNow();
                            }
                        }
                    });
                } catch (Exception e) {
                    getLogger().severe("Async Discord startup failed: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        }, 20L);

        getLogger().info(getName() + " v" + getDescription().getVersion() + " Enabled Successfully!");
    }


    // --- Reload method for leaderboards (unchanged) ---
    public void reloadLeaderboards() {
        reloadConfig();
        if (leaderboardManager != null) {
            leaderboardManager.reloadConfigAndUpdateAll();
        }
        if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
            this.leaderboardUpdateTask.cancel();
        }
        long delay = 20L * 10;
        long refreshTicks = 20L * getConfig().getLong("leaderboards.refresh-seconds", 60);
        this.leaderboardUpdateTask = new LeaderboardUpdateTask(this.leaderboardManager).runTaskTimer(this, delay, refreshTicks);
    }

    // --- setupEconomy (unchanged) ---
    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getLogger().severe("Vault plugin not found! Required for Orders and Coinflip.");
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().severe("No Economy plugin found by Vault! Required for Orders and Coinflip.");
            return false;
        }
        vaultEconomy = rsp.getProvider();
        getLogger().info("Hooked Vault Economy: " + vaultEconomy.getName());
        return vaultEconomy != null;
    }

    // --- initializeWebhook (unchanged) ---
    private WebhookClient initializeWebhook(String configKey, String systemName) {
        String url = getConfig().getString(configKey);
        if (!isConfigValueInvalid(url, "YOUR_" + systemName.toUpperCase().replace("-", "_") + "_WEBHOOK_URL_HERE") && url != null && url.startsWith("https://discord.com/api/webhooks/")) {
            try {
                WebhookClientBuilder builder = new WebhookClientBuilder(url);
                builder.setThreadFactory((job) -> {
                    Thread t = new Thread(job);
                    t.setName(systemName + "-WH");
                    t.setDaemon(true);
                    return t;
                });
                getLogger().info(systemName + " Webhook enabled.");
                WebhookClient client = builder.build();
                return client;
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid Webhook URL [" + systemName + "]: " + e.getMessage());
            }
        } else {
            getLogger().info(systemName + " Webhook disabled (check '" + configKey + "').");
        }
        return null;
    }

    // --- Helper methods (unchanged) ---
    private boolean isConfigValueInvalid(String value, String placeholder) { return value == null || value.isEmpty() || value.equals(placeholder); }
    private void disableWithError(String message) { getLogger().severe(message + " Disabling plugin."); getServer().getPluginManager().disablePlugin(this); }

    // --- Command Registration (unchanged) ---
    private void registerCommands() {
        // --- MODIFIED NULL CHECK ---
        if (discordLinking == null || loginSystem == null || orderSystem == null || orderMenu == null || orderManage == null || orderAdminMenu == null || vaultEconomy == null || coinflipSystem == null || coinflipMenu == null || coinflipManageMenu == null || leaderboardManager == null || moderationDatabase == null ||
                databaseManager == null || itemManager == null || lifestealManager == null) { // <-- LIFESTEAL NULL CHECK
            getLogger().severe("Cannot register commands - one or more systems failed initialization!"); return;
        }
        // --- END MODIFICATION ---

        DiscordLinkCmd discordCmd = new DiscordLinkCmd(this, discordLinking, discordLinkDatabase);
        LoginSystemCmd loginCmd = new LoginSystemCmd(this, loginSystem, loginDatabase, discordLinkDatabase);
        OrderCmd orderCmd = new OrderCmd(this, orderSystem, orderMenu, orderManage, orderAdminMenu);
        CoinflipCmd coinflipCmd = new CoinflipCmd(this, coinflipDatabase, vaultEconomy, coinflipManageMenu, coinflipSystem, coinflipMenu);

        setCommandExecutor("discord", discordCmd); setCommandExecutor("unlink", discordCmd); setCommandExecutor("adminunlink", discordCmd);
        setCommandExecutor("register", loginCmd); setCommandExecutor("login", loginCmd);
        setCommandExecutor("changepassword", loginCmd);
        setCommandExecutor("unregister", loginCmd); setCommandExecutor("loginhistory", loginCmd); setCommandExecutor("checkalt", loginCmd);
        setCommandExecutor("adminchangepass", loginCmd);
        setCommandExecutor("order", orderCmd);
        setCommandExecutor("coinflip", coinflipCmd);

        LeaderboardCommand leaderboardCmd = new LeaderboardCommand(this, this.leaderboardManager);
        getCommand("leaderboard").setExecutor(leaderboardCmd);
        getCommand("leaderboard").setTabCompleter(leaderboardCmd);

        KillLeaderboardCommand killLeaderboardCmd = new KillLeaderboardCommand(this, this.leaderboardManager);
        getCommand("killleaderboard").setExecutor(killLeaderboardCmd);
        getCommand("killleaderboard").setTabCompleter(killLeaderboardCmd);

        // --- UPDATED CONSTRUCTOR ---
        LagClearCommand lagClearCmd = new LagClearCommand(this, this.lagClearConfig);
        // --- END UPDATE ---
        getCommand("lagclear").setExecutor(lagClearCmd);
        getCommand("lagclear").setTabCompleter(lagClearCmd);

        // --- STAFF SYSTEM COMMANDS ---
        MuteCommand muteExecutor = new MuteCommand(this, moderationDatabase);
        setCommandExecutor("mute", muteExecutor);
        setCommandExecutor("muteinfo", muteExecutor);
        setCommandExecutor("unmute", muteExecutor);

        BanCommand banExecutor = new BanCommand(this, moderationDatabase);
        setCommandExecutor("ban", banExecutor);
        setCommandExecutor("ipban", banExecutor);
        setCommandExecutor("baninfo", banExecutor);
        setCommandExecutor("unban", banExecutor);
        setCommandExecutor("unbanip", banExecutor);

        CheckInvCommand checkInvExecutor = new CheckInvCommand(this);
        setCommandExecutor("checkinv", checkInvExecutor);
        setCommandExecutor("admincheckinv", checkInvExecutor);
        // --- END STAFF SYSTEM COMMANDS ---

        // --- LIFESTEAL COMMANDS ---
        LifestealCommands lifestealCmds = new LifestealCommands(this, itemManager, lifestealManager, luckPermsApi);
        setCommandExecutor("withdrawhearts", lifestealCmds);
        setCommandExecutor("sethearts", lifestealCmds);
        setCommandExecutor("checkhearts", lifestealCmds);
        // Register lsgive with tab completion
        getCommand("lsgive").setExecutor(lifestealCmds);
        getCommand("lsgive").setTabCompleter(lifestealCmds);
        // --- END LIFESTEAL COMMANDS ---

        setCommandExecutor("scoreboard", this); // This was after lagclear, moved it after staff commands

    } // --- End registerCommands() ---

    // --- Helper for setting executor (unchanged) ---
    private void setCommandExecutor(String commandName, org.bukkit.command.CommandExecutor executor) { org.bukkit.command.PluginCommand command = getCommand(commandName); if (command != null) { command.setExecutor(executor); } else { getLogger().severe("Failed register command '" + commandName + "'! In plugin.yml?"); } }

    // --- onCommand for Scoreboard (unchanged) ---
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("scoreboard")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("login.scoreboard.reload")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                this.reloadConfig();
                if (scoreboardManager != null) {
                    scoreboardManager.loadConfig();
                    Bukkit.getOnlinePlayers().forEach(scoreboardManager::updateScoreboard);
                }
                sender.sendMessage("§aScoreboard configuration has been reloaded!");
                return true;
            }
            sender.sendMessage("§cUsage: /scoreboard reload");
            return true;
        }
        return false;
    } // --- End onCommand() ---

    // --- onDisable (REVISED) ---
    @Override
    public void onDisable() {
        try {
            getLogger().info("Shutting down " + getName() + "...");

            // --- Reset scoreboards safely ---
            if (scoreboardManager != null) {
                if (Bukkit.getOnlinePlayers() != null && !Bukkit.getOnlinePlayers().isEmpty()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try {
                            if (player != null && player.isOnline() &&
                                    player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // --- Stop scheduled tasks ---
            if (this.leaderboardUpdateTask != null && !this.leaderboardUpdateTask.isCancelled()) {
                this.leaderboardUpdateTask.cancel();
            }

            // --- LIFESTEAL SHUTDOWN (SAVE DATA) ---
            if (lifestealManager != null) {
                lifestealManager.saveAllOnlinePlayerData();
            }
            if (combatLogManager != null) {
                combatLogManager.shutdown();
            }
            // --- END LIFESTEAL SHUTDOWN ---

            // --- Close DBs ---
            if (discordLinkDatabase != null) discordLinkDatabase.disconnect();
            if (loginDatabase != null) loginDatabase.disconnect();
            if (ordersDatabase != null) ordersDatabase.disconnect();
            if (coinflipDatabase != null) coinflipDatabase.disconnect();
            if (moderationDatabase != null) moderationDatabase.closeConnection(); // --- STAFF SYSTEM ADDITION ---
            if (databaseManager != null) databaseManager.closeConnection(); // <-- LIFESTEAL DB CLOSE

            // --- Stop JDA first (before webhooks) ---
            if (discordLinking != null && discordLinking.getJDA() != null) {
                getLogger().info("Shutting down Discord JDA...");
                discordLinking.getJDA().shutdownNow();
            }

            // --- Detach lag logger (shared JDA) ---
            if (lagClearLogger != null) {
                lagClearLogger.shutdown(); // currently a no-op, but explicit
            }

            // --- Close webhooks ---
            if(linkWebhookClient != null) linkWebhookClient.close();
            if(loginWebhookClient != null) loginWebhookClient.close();
            if(orderWebhookClient != null) orderWebhookClient.close();
            if(coinflipWebhookClient != null) coinflipWebhookClient.close();
            if(itemManager != null) itemManager.closeWebhook(); // <-- LIFESTEAL WEBHOOK CLOSE
            if(staffWebhookClient != null) staffWebhookClient.close(); // --- STAFF SYSTEM ADDITION ---
            if(discordStaffLogWebhook != null) discordStaffLogWebhook.close(); // <-- NEWLY ADDED

            // --- Safety: kill OkHttp TaskRunner global executors ---
            try {
                Class<?> taskRunner = Class.forName("okhttp3.internal.concurrent.TaskRunner");
                Object instance = taskRunner.getDeclaredField("INSTANCE").get(null);
                taskRunner.getMethod("shutdown").invoke(instance);
                getLogger().info("OkHttp TaskRunner shut down.");
            } catch (Throwable ignored) {}

            getLogger().info(getName() + " disabled cleanly.");
        } catch (Throwable e) {
            getLogger().severe("Error during onDisable: " + e.getMessage());
            e.printStackTrace();
        }
    } // --- End onDisable() ---

    // --- Getters (MODIFIED) ---
    public DiscordLinkDatabase getDatabase() { return discordLinkDatabase; }
    public LoginDatabase getLoginDatabase() { return loginDatabase; }
    public OrdersDatabase getOrdersDatabase() { return ordersDatabase; }
    public DiscordLinking getDiscordLinking() { return discordLinking; }
    public LoginSystem getLoginSystem() { return loginSystem; }
    public OrderSystem getOrderSystem() { return orderSystem; }
    public Login getPlugin() { return this; }
    public OrderMenu getOrderMenu() { return orderMenu; }
    public OrderFilling getOrderFilling() { return orderFilling; }
    public OrderManage getOrderManage() { return orderManage; }
    public OrderAdminMenu getOrderAdminMenu() { return orderAdminMenu; }
    public int getDefaultOrderLimit() {
        return defaultOrderLimit;
    }
    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }
    public CoinflipDatabase getCoinflipDatabase() { return coinflipDatabase; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public WebhookClient getCoinflipWebhookClient() { return coinflipWebhookClient; }
    public CoinflipMenu getCoinflipMenu() {
        return coinflipMenu;
    }
    public LeaderboardDisplayManager getLeaderboardManager() {
        return leaderboardManager;
    }

    // --- NEW GETTER ---
    public LagClearConfig getLagClearConfig() {
        return lagClearConfig;
    }

    public LagClearLogger getLagClearLogger() { // <-- GETTER ADDED
        return this.lagClearLogger;
    }
    // --- END GETTER ---

    // --- NEW GETTER ---
    public DiscordModConfig getDiscordModConfig() {
        return discordModConfig;
    }
    // --- END GETTER ---

    // --- STAFF SYSTEM GETTERS ---
    public ModerationDatabase getModerationDatabase() { // <-- ADDED GETTER
        return moderationDatabase;
    }

    public Map<UUID, UUID> getViewingInventories() {
        return viewingInventories;
    }
    public Map<UUID, Boolean> getAdminCheckMap() {
        return adminCheckMap;
    }
    public boolean isAdminChecking(UUID staffUUID) {
        return adminCheckMap.getOrDefault(staffUUID, false);
    }
    // --- END STAFF SYSTEM GETTERS ---

    // --- LIFESTEAL GETTERS ---
    public DatabaseManager getDatabaseManager() { return databaseManager; }
    public ItemManager getItemManager() { return itemManager; }
    public LifestealManager getLifestealManager() { return lifestealManager; }
    public DeadPlayerManager getDeadPlayerManager() { return deadPlayerManager; }
    // --- END LIFESTEAL GETTERS ---

    // --- End Getters ---

    // --- onPlayerJoin (unchanged) ---
    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        if (scoreboardManager != null) {
            scoreboardManager.updateScoreboard(event.getPlayer());
        }
    }

    // --- Coinflip Player Quit Handler (unchanged) ---
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        coinflipDatabase.loadPlayerPendingCoinflips(playerUUID).whenCompleteAsync((games, error) -> {
            if (error != null || games == null || games.isEmpty()) {
                return;
            }

            double totalRefund = games.stream().mapToDouble(CoinflipGame::getAmount).sum();
            List<Long> gameIds = games.stream()
                    .map(CoinflipGame::getGameId)
                    .collect(Collectors.toList());

            if (totalRefund <= 0) {
                return;
            }

            CompletableFuture<?>[] removalFutures = gameIds.stream()
                    .map(coinflipDatabase::removeCoinflip)
                    .toArray(CompletableFuture[]::new);

            CompletableFuture.allOf(removalFutures).whenComplete((v, removalError) -> {
                if (removalError != null) {
                    getLogger().severe("Error removing coinflips for " + player.getName() + " on quit: " + removalError.getMessage());
                }

                Bukkit.getScheduler().runTask(this, () -> { // Use 'this'
                    EconomyResponse refundResp = vaultEconomy.depositPlayer(Bukkit.getOfflinePlayer(playerUUID), totalRefund);
                    if (refundResp.transactionSuccess()) {
                        getLogger().info("Refunded " + player.getName() + " " + vaultEconomy.format(totalRefund) + " for " + gameIds.size() + " cancelled coinflips on quit.");
                    } else {
                        getLogger().severe("CRITICAL: Failed to refund " + player.getName() + " " + vaultEconomy.format(totalRefund) + " on quit. Error: " + refundResp.errorMessage);
                    }
                });
            });
        });
    }

    // --- sendCoinflipLog (unchanged) ---
    public void sendCoinflipLog(String message) {
        getLogger().info("[Coinflip Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (coinflipWebhookClient != null) {
            coinflipWebhookClient.send("[Coinflip] " + message).exceptionally(error -> {
                getLogger().warning("Failed send coinflip webhook: ".concat(error.getMessage()));
                return null;
            });
        }
    }

    // --- STAFF SYSTEM LOG SENDER ---
    public void sendStaffLog(String message) {
        getLogger().info("[Staff Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (staffWebhookClient != null) {
            staffWebhookClient.send("[Staff] " + message).exceptionally(error -> {
                getLogger().warning("Failed send staff webhook: ".concat(error.getMessage()));
                return null;
            });
        }
    }
    // --- END STAFF SYSTEM LOG SENDER ---

    // --- NEW DISCORD STAFF LOG SENDER ---
    public void sendDiscordStaffLog(String message) {
        getLogger().info("[Discord Staff Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (discordStaffLogWebhook != null) {
            discordStaffLogWebhook.send("[Discord Staff] " + message).exceptionally(error -> {
                getLogger().warning("Failed send Discord staff webhook: ".concat(error.getMessage()));
                return null;
            });
        }
    }
    // --- END NEW LOG SENDER ---

    // --- onAnimationInventoryClick (unchanged) ---
    @EventHandler
    public void onAnimationInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.DARK_GRAY + "Coin Flipping...")) {
            event.setCancelled(true);
        }
    }

    // --- Coinflip Config & Formatting (unchanged) ---
    public void loadCoinflipConfig() {
        this.coinflipPrefixString = getConfig().getString("coinflip_prefix", "<#47D5F0>M<#49CBED>i<#4AC1E9>n<#4CB7E6>e<#4DADE2>A<#4FA2DF>u<#5098DB>r<#528ED8>o<#5384D4>r<V<#557AD1>a") + " "; // Removed legacy &r
    }

    public Component formatMessage(String legacyText) {
        Component prefix = Component.empty();
        try {
            prefix = MiniMessage.miniMessage().deserialize(this.coinflipPrefixString);
        } catch (Exception e) {
            getLogger().warning("Failed to parse coinflip_prefix with MiniMessage. Falling back to legacy. Error: " + e.getMessage());
            prefix = LegacyComponentSerializer.legacyAmpersand().deserialize(this.coinflipPrefixString);
        }

        Component message = LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
        return prefix.append(message);
    }

    public String getCoinflipPrefix() {
        return coinflipPrefixString;
    }

} // --- End of Login class ---
