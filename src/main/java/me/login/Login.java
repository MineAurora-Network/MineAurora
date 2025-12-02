package me.login;

import me.login.discord.DiscordModule;
import me.login.discord.linking.DiscordLinkDatabase;
import me.login.discord.linking.DiscordLinking;
import me.login.discord.store.TicketModule;
import me.login.discord.moderation.DiscordModConfig;
import me.login.level.LevelModule;
import me.login.loginsystem.*;
import me.login.dungeon.DungeonModule;
import me.login.misc.hologram.HologramModule;
import me.login.misc.generator.GenModule;
import me.login.misc.dailyreward.DailyRewardDatabase;
import me.login.misc.dailyreward.DailyRewardModule;
import me.login.misc.firesale.FiresaleModule;
import me.login.misc.playtimerewards.PlaytimeRewardModule;
import me.login.misc.tab.TabManager;
import me.login.misc.tokens.TokenManager;
import me.login.misc.tokens.TokenModule;
import me.login.misc.milestones.MilestoneModule;
import me.login.premiumfeatures.creatorcode.CreatorCodeModule;
import me.login.misc.rank.RankManager;
import me.login.misc.rank.RankModule;
import me.login.misc.dailyquests.QuestsModule;
import me.login.moderation.ModerationModule;
import me.login.ordersystem.OrderModule;
import me.login.ordersystem.gui.OrderAlertMenu;
import me.login.ordersystem.gui.OrderMenu;
import me.login.pets.PetsLogger;
import me.login.pets.PetsModule;
import me.login.scoreboard.ScoreboardManager;
import me.login.clearlag.LagClearConfig;
import me.login.clearlag.LagClearLogger;
import me.login.clearlag.LagClearModule;
import me.login.misc.rtp.RTPLogger;
import me.login.misc.rtp.RTPModule;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import me.login.leaderboards.LeaderboardModule;
import java.io.File;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import me.login.lifesteal.LifestealModule;
import me.login.lifesteal.LifestealLogger;
import me.login.lifesteal.DatabaseManager;
import me.login.lifesteal.ItemManager;
import me.login.lifesteal.LifestealManager;
import me.login.lifesteal.DeadPlayerManager;
import me.login.items.CustomArmorModule;
import net.luckperms.api.LuckPerms;

public class Login extends JavaPlugin implements Listener {
    private DiscordModule discordModule;
    private TicketModule ticketModule;
    private ModerationModule moderationModule;
    private LevelModule levelModule;
    private DungeonModule dungeonModule;
    private RankModule rankModule;
    private TokenModule tokenModule;
    private LagClearModule lagClearModule;
    private LifestealModule lifestealModule;
    private DailyRewardModule dailyRewardModule;
    private PlaytimeRewardModule playtimeRewardModule;
    private CreatorCodeModule creatorCodeModule;
    private FiresaleModule firesaleModule;
    private LeaderboardModule leaderboardModule;
    private GenModule genModule;
    private OrderModule orderModule;
    private PetsModule petsModule;
    private QuestsModule questsModule;
    private RTPModule rtpModule;
    private HologramModule hologramModule;
    private me.login.misc.hub.HubHeadModule hubHeadModule;
    private me.login.premiumfeatures.credits.CreditsModule creditsModule;
    private CustomArmorModule customArmorModule;
    private LoginModule loginModule;
    private MilestoneModule milestoneModule;

    // --- MANAGERS / LOGGERS ---
    private LagClearLogger lagClearLogger;
    private LifestealLogger lifestealLogger;
    private PetsLogger petsLogger;
    private RTPLogger rtpLogger;
    private ScoreboardManager scoreboardManager;
    private TabManager tabManager;
    private TokenManager tokenManager;
    private DamageIndicator damageIndicator;
    private DailyRewardDatabase dailyRewardDatabase;
    private CoinflipModule coinflipModule;

    // --- UTILS / API ---
    private Economy vaultEconomy = null;
    private LuckPerms luckPermsApi;
    private MiniMessage miniMessage;
    private FileConfiguration itemsConfig;
    private File itemsFile;
    private String serverPrefix;
    private int defaultOrderLimit;

    // --- CACHES ---
    private final Map<UUID, UUID> viewingInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> adminCheckMap = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();

        try {
            YamlConfiguration.loadConfiguration(new File(getDataFolder(), "config.yml"));
        } catch (Exception e) {
            getLogger().severe("==================================================");
            getLogger().severe("CRITICAL ERROR: config.yml is invalid!");
            getLogger().severe("The plugin cannot read the bot token.");
            getLogger().severe("Please fix the YAML syntax errors.");
            getLogger().severe("==================================================");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        itemsFile = new File(getDataFolder(), "items.yml");
        if (!itemsFile.exists()) {
            saveResource("items.yml", false);
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        this.miniMessage = MiniMessage.miniMessage();
        this.serverPrefix = getConfig().getString("server-prefix", "<gray>[<gold>Server</gold>]<reset> ");
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

        // --- Core Systems ---
        this.loginModule = new LoginModule(this);

        this.coinflipModule = new CoinflipModule(this);
        if (!coinflipModule.initDatabase()) {
            disableWithError("Coinflip DB failed.");
            return;
        }

        this.dailyRewardDatabase = new DailyRewardDatabase(this);
        dailyRewardDatabase.connect();
        dailyRewardDatabase.createTables();

        this.damageIndicator = new DamageIndicator(this);
        getServer().getPluginManager().registerEvents(damageIndicator, this);

        this.lagClearModule = new LagClearModule(this);
        if (!this.lagClearModule.init()) {
            getLogger().severe("Failed to initialize LagClear Module!");
        }

        this.questsModule = new QuestsModule(this, this.lagClearModule);

        getServer().getPluginManager().registerEvents(this, this);

        this.scoreboardManager = new ScoreboardManager(this);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().warning("PlaceholderAPI not found! Scoreboard placeholders will not work.");
        }
        if (getServer().getPluginManager().getPlugin("Skript") == null) {
            getLogger().warning("Skript not found! Scoreboard variables via SkriptUtils will not work.");
        }

        rtpLogger = new RTPLogger(this);
        try {
            this.rtpModule = new RTPModule(this, rtpLogger);
            this.rtpModule.enable();
        } catch (Exception e) {
            getLogger().severe("Failed to enable RTP Module!");
            e.printStackTrace();
        }

        try {
            if (rtpModule == null) {
                getLogger().severe("RTP Module failed to load, Hologram Module will be disabled.");
            } else {
                hologramModule = new HologramModule(this, rtpModule);
                hologramModule.enable();
            }
        } catch (Exception e) {
            getLogger().severe("Failed to enable Hologram Module!");
            e.printStackTrace();
        }

        getLogger().info("Initializing Hub Heads...");
        hubHeadModule = new me.login.misc.hub.HubHeadModule(this);
        hubHeadModule.enable();

        levelModule = new me.login.level.LevelModule(Login.this);
        levelModule.init();

        // --- ASYNC STARTUP (Discord & Heavy Modules) ---
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!isEnabled() || Bukkit.isStopping()) {
                getLogger().warning("Plugin disabled before Discord startup. Skipping bot init.");
                return;
            }

            String token = getConfig().getString("logger-bot-token");
            if (token == null || token.isEmpty()) {
                getLogger().severe("CRITICAL: logger-bot-token is NULL/EMPTY in config! Bot cannot start.");
            } else {
                getLogger().info("Logger Bot Token loaded (starts with): " + (token.length() > 5 ? token.substring(0, 5) + "..." : "INVALID"));
            }

            this.lagClearLogger = new LagClearLogger(this);
            getLogger().info("LagClear Logger initialization requested...");
            getLogger().info("Initializing CustomArmorModule...");
            customArmorModule = new CustomArmorModule(this);
            customArmorModule.enable();

            new BukkitRunnable() {
                int attempts = 0;
                final int maxAttempts = 10;

                @Override
                public void run() {
                    if (!isEnabled()) {
                        this.cancel();
                        return;
                    }

                    // Wait for Logger Bot (Shared JDA)
                    if (lagClearLogger != null && lagClearLogger.getJDA() != null && lagClearLogger.getJDA().getStatus() == JDA.Status.CONNECTED) {
                        getLogger().info("LagClear Logger JDA is now connected. Initializing JDA-dependent modules...");

                        getLogger().info("Initializing DungeonModule...");
                        dungeonModule = new DungeonModule(Login.this);

                        getLogger().info("Initializing ModerationModule...");
                        moderationModule = new ModerationModule(Login.this);
                        moderationModule.enable();

                        getLogger().info("Initializing RankModule...");
                        if (luckPermsApi == null) {
                            getLogger().severe("LuckPerms API not found! RankModule will be disabled.");
                            rankModule = null;
                        } else {
                            rankModule = new RankModule(Login.this);
                            if (!rankModule.init(lagClearLogger, luckPermsApi)) {
                                getLogger().severe("Failed to initialize Rank Module!");
                                rankModule = null;
                            }
                        }

                        // --- NEW DISCORD MODULE INITIALIZATION ---
                        getLogger().info("Initializing DiscordModule (Linking/Store/Mod)...");
                        discordModule = new DiscordModule(Login.this);
                        // We pass ModerationModule so Discord commands can punish players properly
                        discordModule.init(lagClearLogger, rankModule, moderationModule);

                        // NOTE: Login Module depends on Linking, but Linking is now inside DiscordModule
                        // Ensure loginModule can access it via getters later
                        if (discordModule.getLinkingModule() != null) {
                            if (!loginModule.init(discordModule.getLinkingModule(), lagClearLogger.getJDA())) {
                                disableWithError("Login Module Failed to Initialize!");
                                this.cancel();
                                return;
                            }
                        } else {
                            getLogger().severe("Discord Linking Module failed. Login System disabled.");
                        }

                        getLogger().info("Initializing LifestealLogger...");
                        lifestealLogger = new LifestealLogger(Login.this, lagClearLogger.getJDA());

                        getLogger().info("Initializing LifestealModule...");
                        lifestealModule = new LifestealModule(Login.this, luckPermsApi, lifestealLogger);
                        if (!lifestealModule.init()) {
                            getLogger().severe("Failed to initialize Lifesteal Module!");
                        }

                        getLogger().info("Initializing OrderModule...");
                        orderModule = new OrderModule(Login.this);
                        orderModule.enable();

                        getLogger().info("Initializing Coinflip system...");
                        coinflipModule.initLogicAndListeners();

                        getLogger().info("Initializing TokenModule...");
                        tokenModule = new TokenModule(Login.this, luckPermsApi);
                        if (!tokenModule.init()) {
                            getLogger().severe("Failed to initialize Token Module!");
                        }
                        tokenManager = tokenModule.getTokenManager();

                        getLogger().info("Initializing MilestoneModule...");
                        milestoneModule = new MilestoneModule(Login.this);
                        milestoneModule.init();

                        getLogger().info("Initializing DailyRewardModule...");
                        dailyRewardModule = new DailyRewardModule(Login.this, vaultEconomy, dailyRewardDatabase, tokenManager);
                        if (!dailyRewardModule.init()) {
                            getLogger().severe("Failed to initialize DailyReward Module!");
                        }

                        getLogger().info("Initializing PlaytimeRewardModule...");
                        playtimeRewardModule = new PlaytimeRewardModule(Login.this, vaultEconomy, tokenManager);
                        if (!playtimeRewardModule.init()) {
                            getLogger().severe("Failed to initialize PlaytimeReward Module!");
                        }

                        getLogger().info("Initializing QuestsModule...");
                        questsModule.enable();

                        getLogger().info("Initializing GenModule...");
                        genModule = new GenModule(Login.this, lagClearLogger);
                        genModule.init();

                        getLogger().info("Initializing CreditsModule...");
                        creditsModule = new me.login.premiumfeatures.credits.CreditsModule(Login.this);
                        creditsModule.enable();

                        getLogger().info("Initializing CreatorCodeModule...");
                        creatorCodeModule = new CreatorCodeModule(Login.this, creditsModule.getDatabase());
                        if (!creatorCodeModule.init(lagClearLogger)) {
                            getLogger().severe("Failed to initialize Creator Code Module!");
                        }

                        getLogger().info("Initializing PetsLogger...");
                        petsLogger = new PetsLogger(Login.this);
                        getLogger().info("Initializing PetsModule...");
                        petsModule = new PetsModule(Login.this);
                        if (!petsModule.init(petsLogger)) {
                            getLogger().severe("Failed to initialize Pets Module!");
                        }

                        getLogger().info("Initializing FiresaleModule...");
                        firesaleModule = new FiresaleModule(Login.this);
                        firesaleModule.init();

                        getLogger().info("Initializing TabManager...");
                        tabManager = new TabManager(Login.this);
                        tabManager.startUpdater();

                        getLogger().info("Initializing LeaderboardModule...");
                        leaderboardModule = new LeaderboardModule(Login.this);
                        if (!leaderboardModule.init()) {
                            getLogger().severe("Failed to initialize Leaderboard Module!");
                        }

                        this.cancel();
                        return;
                    }

                    attempts++;
                    if (attempts >= maxAttempts) {
                        getLogger().severe("LagClear Logger JDA did not connect after 10 seconds.");
                        getLogger().severe("All Discord-dependent modules will be disabled.");
                        this.cancel();
                    }
                }
            }.runTaskTimer(Login.this, 20L, 20L);
        }, 20L);

        getLogger().info(getName() + " v" + getDescription().getVersion() + " Enabled Successfully!");
    }

    public void reloadItems() {
        if (itemsFile == null) {
            itemsFile = new File(getDataFolder(), "items.yml");
        }
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        if (petsModule != null && petsModule.getPetsConfig() != null) {
            petsModule.getPetsConfig().reloadItemsConfig();
        }
    }

    public FileConfiguration getItems() {
        if (itemsConfig == null) {
            reloadItems();
        }
        return itemsConfig;
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

    private void disableWithError(String message) {
        getLogger().severe(message + " Disabling plugin.");
        getServer().getPluginManager().disablePlugin(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("scoreboard")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("login.scoreboard.reload")) {
                    sender.sendMessage("§cYou do not have permission to use this command.");
                    return true;
                }
                this.reloadConfig();
                this.reloadItems();
                this.serverPrefix = getConfig().getString("server-prefix", "<gray>[<gold>Server</gold>]<reset> ");
                if (tabManager != null) {
                    tabManager.loadConfig();
                }
                if (scoreboardManager != null) {
                    scoreboardManager.loadConfig();
                    Bukkit.getOnlinePlayers().forEach(scoreboardManager::updateScoreboard);
                }
                sender.sendMessage("§aScoreboard and Items configuration has been reloaded!");
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

            if (loginModule != null) loginModule.shutdown();
            if (scoreboardManager != null) {
                // Clear scoreboards logic...
                if (Bukkit.getOnlinePlayers() != null) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try {
                            if (player != null && player.isOnline() && player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }

            // Shutdown modules
            if (discordModule != null) discordModule.shutdown();
            if (moderationModule != null) moderationModule.disable();

            // ... [Keep other shutdown calls] ...
            if (leaderboardModule != null) leaderboardModule.shutdown();
            if (levelModule != null) levelModule.shutdown();
            if (lagClearModule != null) lagClearModule.shutdown();
            if (lifestealModule != null) lifestealModule.shutdown();
            if (dailyRewardModule != null) dailyRewardModule.shutdown();
            if (playtimeRewardModule != null) playtimeRewardModule.shutdown();
            if (genModule != null) genModule.shutdown();
            if (tokenModule != null) tokenModule.shutdown();
            if (creatorCodeModule != null) creatorCodeModule.shutdown();
            if (milestoneModule != null) milestoneModule.shutdown();
            if (rankModule != null) rankModule.shutdown();
            if (ticketModule != null) ticketModule.shutdown();
            if (petsModule != null) petsModule.shutdown();
            if (firesaleModule != null) firesaleModule.disable();
            if (tabManager != null) tabManager.stopUpdater();
            if (orderModule != null) orderModule.disable();
            if (questsModule != null) questsModule.disable();
            if (hubHeadModule != null) hubHeadModule.disable();
            if (creditsModule != null) creditsModule.disable();
            if (dungeonModule != null) dungeonModule.disable();
            if (coinflipModule != null && coinflipModule.getDatabase() != null) coinflipModule.getDatabase().disconnect();
            if (lagClearLogger != null) lagClearLogger.shutdown();
            if (hologramModule != null) hologramModule.disable();

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

    // --- GETTERS ---

    // Updated to use DiscordModule
    public DiscordLinkDatabase getDiscordLinkDatabase() {
        return (discordModule != null && discordModule.getLinkingModule() != null)
                ? discordModule.getLinkingModule().getDiscordLinkDatabase()
                : null;
    }

    public DiscordLinking getDiscordLinking() {
        return (discordModule != null && discordModule.getLinkingModule() != null)
                ? discordModule.getLinkingModule().getDiscordLinking()
                : null;
    }

    public DiscordModConfig getDiscordModConfig() {
        return (discordModule != null) ? discordModule.getModConfig() : null;
    }

    public OrderMenu getOrderMenu() { return (orderModule != null) ? orderModule.getOrderMenu() : null; }
    public LoginDatabase getLoginDatabase() { return (loginModule != null) ? loginModule.getLoginDatabase() : null; }
    public LoginSystem getLoginSystem() { return (loginModule != null) ? loginModule.getLoginSystem() : null; }
    public LoginSystemLogger getLoginSystemLogger() { return (loginModule != null) ? loginModule.getLogger() : null; }
    public Login getPlugin() { return this; }
    public OrderAlertMenu getOrderAlertMenu() { return (orderModule != null) ? orderModule.getOrderAlertMenu() : null; }
    public int getDefaultOrderLimit() { return defaultOrderLimit; }
    public ScoreboardManager getScoreboardManager() { return scoreboardManager; }
    public CoinflipDatabase getCoinflipDatabase() { return (coinflipModule != null) ? coinflipModule.getDatabase() : null; }
    public Economy getVaultEconomy() { return vaultEconomy; }
    public CoinflipMenu getCoinflipMenu() { return (coinflipModule != null) ? coinflipModule.getCoinflipMenu() : null; }
    public CoinflipSystem getCoinflipSystem() { return (coinflipModule != null) ? coinflipModule.getCoinflipSystem() : null; }
    public CoinflipManageMenu getCoinflipManageMenu() { return (coinflipModule != null) ? coinflipModule.getCoinflipManageMenu() : null; }
    public CoinflipAdminMenu getCoinflipAdminMenu() { return (coinflipModule != null) ? coinflipModule.getCoinflipAdminMenu() : null; }
    public me.login.misc.tokens.TokenModule getTokenModule() { return this.tokenModule; }
    public me.login.premiumfeatures.credits.CreditsModule getCreditsModule() { return this.creditsModule; }
    public me.login.misc.hologram.HologramModule getHologramModule() { return this.hologramModule; }
    public LeaderboardModule getLeaderboardModule() { return leaderboardModule; }
    public me.login.loginsystem.LoginModule getLoginModule() { return this.loginModule; }
    public LagClearConfig getLagClearConfig() { return (lagClearModule != null) ? lagClearModule.getLagClearConfig() : null; }
    public MilestoneModule getMilestoneModule() { return this.milestoneModule; }
    public LagClearLogger getLagClearLogger() { return this.lagClearLogger; }
    public me.login.moderation.ModerationDatabase getModerationDatabase() { return (moderationModule != null) ? moderationModule.getDatabase() : null; }
    public Map<UUID, UUID> getViewingInventories() { return viewingInventories; }
    public Map<UUID, Boolean> getAdminCheckMap() { return adminCheckMap; }
    public CustomArmorModule getCustomArmorModule() { return customArmorModule; }
    public boolean isAdminChecking(UUID staffUUID) { return adminCheckMap.getOrDefault(staffUUID, false); }
    public DatabaseManager getDatabaseManager() { return (lifestealModule != null) ? lifestealModule.getDatabaseManager() : null; }
    public LevelModule getLevelModule() { return levelModule; }
    public ItemManager getItemManager() { return (lifestealModule != null) ? lifestealModule.getItemManager() : null; }
    public LifestealManager getLifestealManager() { return (lifestealModule != null) ? lifestealModule.getLifestealManager() : null; }
    public DeadPlayerManager getDeadPlayerManager() { return (lifestealModule != null) ? lifestealModule.getDeadPlayerManager() : null; }
    public DailyRewardDatabase getDailyRewardDatabase() { return this.dailyRewardDatabase; }
    public RankManager getRankManager() { return (rankModule != null) ? rankModule.getManager() : null; }
    public PetsModule getPetsModule() { return petsModule; }
    public TokenManager getTokenManager() { if (this.tokenManager == null && this.tokenModule != null) this.tokenManager = this.tokenModule.getTokenManager(); return this.tokenManager; }
    public JDA getJda() { return (lagClearLogger != null) ? lagClearLogger.getJDA() : null; }
    public MiniMessage getComponentSerializer() { return miniMessage; }
    public String getServerPrefix() { return serverPrefix; }
    public me.login.dungeon.DungeonModule getDungeonModule() { return this.dungeonModule; }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (scoreboardManager != null) {
            scoreboardManager.updateScoreboard(event.getPlayer());
        }
    }

    public void sendStaffLog(String message) {
        LoginSystemLogger lSysLogger = getLoginSystemLogger();
        if (lSysLogger != null) {
            lSysLogger.logAdmin(message);
        } else if (lifestealLogger != null) {
            lifestealLogger.logNormal(message);
        } else if (lagClearLogger != null) {
            lagClearLogger.sendLog(message);
        } else {
            getLogger().info("[StaffLog] " + message);
        }
    }
}