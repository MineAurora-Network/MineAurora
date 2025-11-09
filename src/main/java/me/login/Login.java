package me.login;

import club.minnced.discord.webhook.WebhookClient;
import club.minnced.discord.webhook.WebhookClientBuilder;
import me.login.discordlinking.*;
import me.login.discordcommand.DiscordCommandRegistrar;
import me.login.discordcommand.DiscordModConfig;
import me.login.loginsystem.*;
import me.login.misc.dailyreward.DailyRewardDatabase; // IMPORT DailyRewardDatabase
import me.login.misc.dailyreward.DailyRewardModule; // IMPORT DailyRewardModule
import me.login.misc.playtimerewards.PlaytimeRewardModule; // IMPORT PlaytimeRewardModule
import me.login.misc.tokens.TokenModule; // IMPORT TokenModule
import me.login.misc.creatorcode.CreatorCodeModule; // IMPORT CreatorCodeModule
import me.login.misc.rank.RankModule; // IMPORT RankModule
import me.login.ordersystem.*;
import me.login.scoreboard.ScoreboardManager;
import me.login.clearlag.LagClearConfig;
import me.login.clearlag.LagClearLogger;
import me.login.clearlag.LagClearModule;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import me.login.coinflip.*;
import net.dv8tion.jda.api.JDA;
import org.bukkit.scheduler.BukkitRunnable;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.ChatColor;
import me.login.leaderboards.LeaderboardModule;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.login.moderation.ModerationDatabase;
import me.login.moderation.ModerationListener;
import me.login.moderation.BanCommand;
import me.login.moderation.CheckInvCommand;
import me.login.moderation.MuteCommand;
import me.login.lifesteal.LifestealModule;
import me.login.lifesteal.LifestealLogger;
import me.login.lifesteal.DatabaseManager;
import me.login.lifesteal.ItemManager;
import me.login.lifesteal.LifestealManager;
import me.login.lifesteal.DeadPlayerManager;

import net.luckperms.api.LuckPerms;


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
    private OrderAdminMenu orderAdminMenu;
    private DamageIndicator damageIndicator;
    private int defaultOrderLimit;
    private ScoreboardManager scoreboardManager;
    private Economy vaultEconomy = null;
    private LeaderboardModule leaderboardModule;
    private CoinflipModule coinflipModule;
    private ModerationDatabase moderationDatabase;
    private WebhookClient staffWebhookClient;
    private final Map<UUID, UUID> viewingInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> adminCheckMap = new ConcurrentHashMap<>();
    private DiscordModConfig discordModConfig;
    private WebhookClient discordStaffLogWebhook;
    private LagClearModule lagClearModule;
    private LagClearLogger lagClearLogger;
    private LuckPerms luckPermsApi;
    private LifestealModule lifestealModule;
    private LifestealLogger lifestealLogger;
    private DailyRewardModule dailyRewardModule; // ADD DailyRewardModule INSTANCE
    private DailyRewardDatabase dailyRewardDatabase; // ADD DailyRewardDatabase INSTANCE
    private PlaytimeRewardModule playtimeRewardModule; // ADD PlaytimeRewardModule INSTANCE
    private TokenModule tokenModule; // ADD TokenModule INSTANCE
    private CreatorCodeModule creatorCodeModule; // ADD CreatorCodeModule INSTANCE
    private RankModule rankModule; // ADD RankModule INSTANCE


    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("items.yml", false);
        getServer().getPluginManager().registerEvents(new me.login.misc.GuiCleanup.MetaDataRemover(this), this);

        this.discordModConfig = new DiscordModConfig(this);
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

        this.coinflipModule = new CoinflipModule(this);
        if (!coinflipModule.initDatabase()) {
            disableWithError("Coinflip DB failed."); return;
        }

        // --- ADDED DATABASE INIT FOR DAILY REWARDS ---
        this.dailyRewardDatabase = new DailyRewardDatabase(this);
        dailyRewardDatabase.connect();
        dailyRewardDatabase.createTables();
        // --- END DATABASE INIT ---

        this.moderationDatabase = new ModerationDatabase(this);

        this.linkWebhookClient = initializeWebhook("link-system-log-webhook", "Link-System");
        this.loginWebhookClient = initializeWebhook("login-system-log-webhook", "Login-System");
        this.orderWebhookClient = initializeWebhook("order-system-log-webhook", "Order-System");
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

        getServer().getPluginManager().registerEvents(orderAdminMenu, this);
        getServer().getPluginManager().registerEvents(new ModerationListener(this, moderationDatabase), this);

        this.leaderboardModule = new LeaderboardModule(this);
        if (!this.leaderboardModule.init()) {
            getLogger().severe("Failed to initialize Leaderboard Module!");
        }

        this.lagClearModule = new LagClearModule(this);
        if (!this.lagClearModule.init()) {
            getLogger().severe("Failed to initialize LagClear Module!");
        }

        registerCommands();
        getServer().getPluginManager().registerEvents(this, this);

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

                            this.lagClearLogger = new LagClearLogger(this);
                            getLogger().info("LagClear Logger initialization requested...");

                            new BukkitRunnable() {
                                int attempts = 0;
                                final int maxAttempts = 10; // Wait 10 seconds (10 * 20 ticks)

                                @Override
                                public void run() {
                                    if (!isEnabled()) {
                                        this.cancel();
                                        return;
                                    }

                                    if (lagClearLogger != null && lagClearLogger.getJDA() != null && lagClearLogger.getJDA().getStatus() == JDA.Status.CONNECTED) {
                                        getLogger().info("LagClear Logger JDA is now connected. Initializing JDA-dependent modules...");

                                        getLogger().info("Initializing LifestealLogger...");
                                        lifestealLogger = new LifestealLogger(Login.this, lagClearLogger.getJDA());

                                        getLogger().info("Initializing LifestealModule...");
                                        lifestealModule = new LifestealModule(Login.this, luckPermsApi, lifestealLogger);
                                        if (!lifestealModule.init()) {
                                            getLogger().severe("Failed to initialize Lifesteal Module!");
                                        }

                                        getLogger().info("Initializing Coinflip system...");
                                        coinflipModule.initLogicAndListeners();

                                        // --- ADDED DAILY REWARD MODULE INITIALIZATION ---
                                        getLogger().info("Initializing DailyRewardModule...");
                                        dailyRewardModule = new DailyRewardModule(Login.this, vaultEconomy, dailyRewardDatabase);
                                        if (!dailyRewardModule.init()) {
                                            getLogger().severe("Failed to initialize DailyReward Module!");
                                        }
                                        // --- END DAILY REWARD ---

                                        // --- ADDED PLAYTIME REWARD MODULE INITIALIZATION ---
                                        getLogger().info("Initializing PlaytimeRewardModule...");
                                        playtimeRewardModule = new PlaytimeRewardModule(Login.this, vaultEconomy, dailyRewardDatabase);
                                        if (!playtimeRewardModule.init()) {
                                            getLogger().severe("Failed to initialize PlaytimeReward Module!");
                                        }
                                        // --- END PLAYTIME REWARD ---

                                        // --- ADDED TOKEN MODULE INITIALIZATION ---
                                        getLogger().info("Initializing TokenModule...");
                                        tokenModule = new TokenModule(Login.this, luckPermsApi, dailyRewardDatabase);
                                        if (!tokenModule.init()) {
                                            getLogger().severe("Failed to initialize Token Module!");
                                        }
                                        // --- END TOKEN MODULE ---

                                        // --- ADDED CREATOR CODE MODULE INITIALIZATION ---
                                        getLogger().info("Initializing CreatorCodeModule...");
                                        creatorCodeModule = new CreatorCodeModule(Login.this);
                                        if (!creatorCodeModule.init(lagClearLogger)) {
                                            getLogger().severe("Failed to initialize Creator Code Module!");
                                        }
                                        // --- END CREATOR CODE MODULE ---

                                        // --- ADDED RANK MODULE INITIALIZATION ---
                                        getLogger().info("Initializing RankModule...");
                                        if (luckPermsApi == null) {
                                            getLogger().severe("LuckPerms API not found! RankModule will be disabled.");
                                        } else {
                                            rankModule = new RankModule(Login.this);
                                            if (!rankModule.init(lagClearLogger, luckPermsApi)) {
                                                getLogger().severe("Failed to initialize Rank Module!");
                                            }
                                        }
                                        // --- END RANK MODULE INITIALIZATION ---

                                        this.cancel();
                                        return;
                                    }

                                    attempts++;
                                    if (attempts >= maxAttempts) {
                                        getLogger().severe("LagClear Logger JDA did not connect after 10 seconds. Coinflip & Lifesteal logging will be disabled.");
                                        getLogger().severe("DailyRewardModule will also fail to initialize logging!");
                                        getLogger().severe("PlaytimeRewardModule will also fail to initialize logging!");
                                        getLogger().severe("TokenModule will also fail to initialize logging!");
                                        getLogger().severe("CreatorCodeModule will also fail to initialize logging!");
                                        getLogger().severe("RankModule will also fail to initialize!");
                                        this.cancel();
                                    }
                                }
                            }.runTaskTimer(Login.this, 20L, 20L);

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

    private boolean isConfigValueInvalid(String value, String placeholder) { return value == null || value.isEmpty() || value.equals(placeholder); }
    private void disableWithError(String message) { getLogger().severe(message + " Disabling plugin."); getServer().getPluginManager().disablePlugin(this); }

    private void registerCommands() {
        if (discordLinking == null || loginSystem == null || orderSystem == null || orderMenu == null || orderManage == null || orderAdminMenu == null || vaultEconomy == null || moderationDatabase == null) {
            getLogger().severe("Cannot register commands - one or more systems failed initialization!"); return;
        }

        DiscordLinkCmd discordCmd = new DiscordLinkCmd(this, discordLinking, discordLinkDatabase);
        LoginSystemCmd loginCmd = new LoginSystemCmd(this, loginSystem, loginDatabase, discordLinkDatabase);
        OrderCmd orderCmd = new OrderCmd(this, orderSystem, orderMenu, orderManage, orderAdminMenu);

        setCommandExecutor("discord", discordCmd); setCommandExecutor("unlink", discordCmd); setCommandExecutor("adminunlink", discordCmd);
        setCommandExecutor("register", loginCmd); setCommandExecutor("login", loginCmd);
        setCommandExecutor("changepassword", loginCmd);
        setCommandExecutor("unregister", loginCmd); setCommandExecutor("loginhistory", loginCmd); setCommandExecutor("checkalt", loginCmd);
        setCommandExecutor("adminchangepass", loginCmd);
        setCommandExecutor("order", orderCmd);

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

        setCommandExecutor("scoreboard", this);
    }

    private void setCommandExecutor(String commandName, org.bukkit.command.CommandExecutor executor) { org.bukkit.command.PluginCommand command = getCommand(commandName); if (command != null) { command.setExecutor(executor); } else { getLogger().severe("Failed register command '" + commandName + "'! In plugin.yml?"); } }

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
    }

    @Override
    public void onDisable() {
        try {
            getLogger().info("Shutting down " + getName() + "...");

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

            if (leaderboardModule != null) {
                leaderboardModule.shutdown();
            }

            if (lagClearModule != null) {
                lagClearModule.shutdown();
            }

            if (lifestealModule != null) {
                lifestealModule.shutdown();
            }

            if (dailyRewardModule != null) {
                dailyRewardModule.shutdown();
            }

            if (playtimeRewardModule != null) {
                playtimeRewardModule.shutdown();
            }

            if (tokenModule != null) {
                tokenModule.shutdown();
            }

            if (creatorCodeModule != null) {
                creatorCodeModule.shutdown();
            }

            if (rankModule != null) {
                rankModule.shutdown();
            }

            if (discordLinkDatabase != null) discordLinkDatabase.disconnect();
            if (loginDatabase != null) loginDatabase.disconnect();
            if (ordersDatabase != null) ordersDatabase.disconnect();
            if (coinflipModule != null && coinflipModule.getDatabase() != null) {
                coinflipModule.getDatabase().disconnect();
            }
            if (moderationDatabase != null) moderationDatabase.closeConnection();

            if (discordLinking != null && discordLinking.getJDA() != null) {
                getLogger().info("Shutting down Discord JDA...");
                discordLinking.getJDA().shutdownNow();
            }

            if (lagClearLogger != null) {
                lagClearLogger.shutdown();
            }

            if(linkWebhookClient != null) linkWebhookClient.close();
            if(loginWebhookClient != null) loginWebhookClient.close();
            if(orderWebhookClient != null) orderWebhookClient.close();
            if(staffWebhookClient != null) staffWebhookClient.close();
            if(discordStaffLogWebhook != null) discordStaffLogWebhook.close();


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
    }

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

    public CoinflipDatabase getCoinflipDatabase() {
        return (coinflipModule != null) ? coinflipModule.getDatabase() : null;
    }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public CoinflipMenu getCoinflipMenu() {
        return (coinflipModule != null) ? coinflipModule.getCoinflipMenu() : null;
    }
    public CoinflipSystem getCoinflipSystem() {
        return (coinflipModule != null) ? coinflipModule.getCoinflipSystem() : null;
    }
    public CoinflipManageMenu getCoinflipManageMenu() {
        return (coinflipModule != null) ? coinflipModule.getCoinflipManageMenu() : null;
    }
    public CoinflipAdminMenu getCoinflipAdminMenu() {
        return (coinflipModule != null) ? coinflipModule.getCoinflipAdminMenu() : null;
    }

    public LeaderboardModule getLeaderboardModule() {
        return leaderboardModule;
    }

    public LagClearConfig getLagClearConfig() {
        return (lagClearModule != null) ? lagClearModule.getLagClearConfig() : null;
    }

    public LagClearLogger getLagClearLogger() {
        return this.lagClearLogger;
    }

    public DiscordModConfig getDiscordModConfig() {
        return discordModConfig;
    }

    public ModerationDatabase getModerationDatabase() {
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

    public DatabaseManager getDatabaseManager() {
        return (lifestealModule != null) ? lifestealModule.getDatabaseManager() : null;
    }
    public ItemManager getItemManager() {
        return (lifestealModule != null) ? lifestealModule.getItemManager() : null;
    }
    public LifestealManager getLifestealManager() {
        return (lifestealModule != null) ? lifestealModule.getLifestealManager() : null;
    }
    public DeadPlayerManager getDeadPlayerManager() {
        return (lifestealModule != null) ? lifestealModule.getDeadPlayerManager() : null;
    }

    // --- ADDED GETTER FOR DAILY REWARD DATABASE ---
    public DailyRewardDatabase getDailyRewardDatabase() {
        return this.dailyRewardDatabase; // Return the instance from the main class
    }
    // --- END GETTER ---

    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        if (scoreboardManager != null) {
            scoreboardManager.updateScoreboard(event.getPlayer());
        }
    }

    public void sendStaffLog(String message) {
        getLogger().info("[Staff Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (staffWebhookClient != null) {
            staffWebhookClient.send("[Staff] " + message).exceptionally(error -> {
                getLogger().warning("Failed send staff webhook: ".concat(error.getMessage()));
                return null;
            });
        }
    }

    public void sendDiscordStaffLog(String message) {
        getLogger().info("[Discord Staff Log] " + ChatColor.stripColor(message.replace("`", "").replace("*", "")));
        if (discordStaffLogWebhook != null) {
            discordStaffLogWebhook.send("[Discord Staff] " + message).exceptionally(error -> {
                getLogger().warning("Failed send Discord staff webhook: ".concat(error.getMessage()));
                return null;
            });
        }
    }
}