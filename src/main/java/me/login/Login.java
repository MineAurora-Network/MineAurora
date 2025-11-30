package me.login;

import me.login.discord.store.TicketModule;
import me.login.discord.linking.*;
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
import me.login.moderation.commands.AdminCommandsModule;
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
import me.login.items.CustomArmorModule;
import net.luckperms.api.LuckPerms;

public class Login extends JavaPlugin implements Listener {
    private me.login.level.LevelModule levelModule;
    private DungeonModule dungeonModule;
    private me.login.misc.hub.HubHeadModule hubHeadModule;
    private me.login.premiumfeatures.credits.CreditsModule creditsModule;
    private QuestsModule questsModule;
    private HologramModule hologramModule;
    private DiscordLinkingModule discordLinkingModule;
    private LoginModule loginModule;
    private GenModule genModule;
    private OrderModule orderModule;
    private CustomArmorModule customArmorModule;
    private DamageIndicator damageIndicator;
    private RTPLogger rtpLogger;
    private int defaultOrderLimit;
    private RTPModule rtpModule;
    private ScoreboardManager scoreboardManager;
    private Economy vaultEconomy = null;
    private LeaderboardModule leaderboardModule;
    private CoinflipModule coinflipModule;
    private ModerationDatabase moderationDatabase;
    private final Map<UUID, UUID> viewingInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> adminCheckMap = new ConcurrentHashMap<>();
    private DiscordModConfig discordModConfig;
    private LagClearModule lagClearModule;
    private LagClearLogger lagClearLogger;
    private LuckPerms luckPermsApi;
    private LifestealModule lifestealModule;
    private LifestealLogger lifestealLogger;
    private DailyRewardModule dailyRewardModule;
    private DailyRewardDatabase dailyRewardDatabase;
    private PlaytimeRewardModule playtimeRewardModule;
    private TokenModule tokenModule;
    private TokenManager tokenManager;
    private CreatorCodeModule creatorCodeModule;
    private RankModule rankModule;
    private TicketModule ticketModule;
    private FiresaleModule firesaleModule;
    private TabManager tabManager;
    private AdminCommandsModule adminCommandsModule;
    private PetsModule petsModule;
    private PetsLogger petsLogger;
    private MiniMessage miniMessage;
    private String serverPrefix;
    private File itemsFile;
    private FileConfiguration itemsConfig;
    private MilestoneModule milestoneModule;

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

        this.loginModule = new LoginModule(this);

        this.coinflipModule = new CoinflipModule(this);
        if (!coinflipModule.initDatabase()) {
            disableWithError("Coinflip DB failed.");
            return;
        }

        this.dailyRewardDatabase = new DailyRewardDatabase(this);
        dailyRewardDatabase.connect();
        dailyRewardDatabase.createTables();

        this.moderationDatabase = new ModerationDatabase(this);

        this.damageIndicator = new DamageIndicator(this);
        getServer().getPluginManager().registerEvents(damageIndicator, this);
        getServer().getPluginManager().registerEvents(new ModerationListener(this, moderationDatabase), this);


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

                    if (lagClearLogger != null && lagClearLogger.getJDA() != null && lagClearLogger.getJDA().getStatus() == JDA.Status.CONNECTED) {
                        getLogger().info("LagClear Logger JDA is now connected. Initializing JDA-dependent modules...");

                        getLogger().info("Initializing DungeonModule...");
                        dungeonModule = new DungeonModule(Login.this);

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

                        getLogger().info("Initializing DiscordLinkingModule...");
                        discordLinkingModule = new DiscordLinkingModule(Login.this);
                        RankManager rankManager = (rankModule != null) ? rankModule.getManager() : null;
                        if (!discordLinkingModule.init(lagClearLogger, discordModConfig, rankManager)) {
                            getLogger().severe("Failed to initialize DiscordLinking Module! Disabling plugin.");
                            getServer().getPluginManager().disablePlugin(Login.this);
                            this.cancel();
                            return;
                        }

                        if (!loginModule.init(discordLinkingModule, lagClearLogger.getJDA())) {
                            disableWithError("Login Module Failed to Initialize!");
                            this.cancel();
                            return;
                        }

                        getLogger().info("Registering commands...");
                        registerModerationCommands();

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

                        getLogger().info("Initializing TicketModule...");
                        ticketModule = new TicketModule(Login.this, discordLinkingModule.getDiscordLinking(), rankManager);
                        ticketModule.init();

                        getLogger().info("Initializing FiresaleModule...");
                        firesaleModule = new FiresaleModule(Login.this);
                        firesaleModule.init();

                        getLogger().info("Initializing TabManager...");
                        tabManager = new TabManager(Login.this);
                        tabManager.startUpdater();

                        getLogger().info("Initializing AdminCommandsModule...");
                        adminCommandsModule = new AdminCommandsModule(Login.this);
                        adminCommandsModule.enable();

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

    private void registerModerationCommands() {
        if (moderationDatabase == null) {
            getLogger().severe("Cannot register Moderation commands - Database failed!");
            return;
        }

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

    private void setCommandExecutor(String commandName, org.bukkit.command.CommandExecutor executor) {
        org.bukkit.command.PluginCommand command = getCommand(commandName);
        if (command != null) {
            command.setExecutor(executor);
        } else {
            getLogger().severe("Failed register command '" + commandName + "'! In plugin.yml?");
        }
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

            if (loginModule != null) {
                loginModule.shutdown();
            }

            if (scoreboardManager != null) {
                if (Bukkit.getOnlinePlayers() != null && !Bukkit.getOnlinePlayers().isEmpty()) {
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        try {
                            if (player != null && player.isOnline() &&
                                    player.getScoreboard() != Bukkit.getScoreboardManager().getMainScoreboard()) {
                                player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

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
            if (adminCommandsModule != null) adminCommandsModule.disable();
            if (orderModule != null) orderModule.disable();
            if (discordLinkingModule != null) discordLinkingModule.shutdown();
            if (questsModule != null) questsModule.disable();
            if (hubHeadModule != null) hubHeadModule.disable();
            if (this.creditsModule != null) {
                this.creditsModule.disable();
            }
            if (this.dungeonModule != null) this.dungeonModule.disable();
            if (coinflipModule != null && coinflipModule.getDatabase() != null) {
                coinflipModule.getDatabase().disconnect();
            }
            if (moderationDatabase != null) moderationDatabase.closeConnection();
            if (lagClearLogger != null) {
                lagClearLogger.shutdown();
            }
            if (hologramModule != null) {
                hologramModule.disable();
            }

            try {
                Class<?> taskRunner = Class.forName("okhttp3.internal.concurrent.TaskRunner");
                Object instance = taskRunner.getDeclaredField("INSTANCE").get(null);
                taskRunner.getMethod("shutdown").invoke(instance);
                getLogger().info("OkHttp TaskRunner shut down.");
            } catch (Throwable ignored) {
            }

            getLogger().info(getName() + " disabled cleanly.");
        } catch (Throwable e) {
            getLogger().severe("Error during onDisable: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public OrderMenu getOrderMenu() {
        if (this.orderModule == null) {
            return null;
        }
        return this.orderModule.getOrderMenu();
    }

    public DiscordLinkDatabase getDatabase() {
        return (discordLinkingModule != null) ? discordLinkingModule.getDiscordLinkDatabase() : null;
    }

    public DiscordLinkDatabase getDiscordLinkDatabase() {
        return (discordLinkingModule != null) ? discordLinkingModule.getDiscordLinkDatabase() : null;
    }

    public LoginDatabase getLoginDatabase() {
        return (loginModule != null) ? loginModule.getLoginDatabase() : null;
    }

    public DiscordLinking getDiscordLinking() {
        return (discordLinkingModule != null) ? discordLinkingModule.getDiscordLinking() : null;
    }

    public LoginSystem getLoginSystem() {
        return (loginModule != null) ? loginModule.getLoginSystem() : null;
    }

    public LoginSystemLogger getLoginSystemLogger() {
        return (loginModule != null) ? loginModule.getLogger() : null;
    }

    public Login getPlugin() {
        return this;
    }

    public OrderAlertMenu getOrderAlertMenu() {
        return (orderModule != null) ? orderModule.getOrderAlertMenu() : null;
    }

    public int getDefaultOrderLimit() {
        return defaultOrderLimit;
    }

    public ScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public CoinflipDatabase getCoinflipDatabase() {
        return (coinflipModule != null) ? coinflipModule.getDatabase() : null;
    }

    public Economy getVaultEconomy() {
        return vaultEconomy;
    }

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

    public me.login.misc.tokens.TokenModule getTokenModule() {
        return this.tokenModule;
    }

    public me.login.premiumfeatures.credits.CreditsModule getCreditsModule() {
        return this.creditsModule;
    }

    public me.login.misc.hologram.HologramModule getHologramModule() {
        return this.hologramModule;
    }

    public LeaderboardModule getLeaderboardModule() {
        return leaderboardModule;
    }

    public me.login.loginsystem.LoginModule getLoginModule() {
        return this.loginModule;
    }

    public LagClearConfig getLagClearConfig() {
        return (lagClearModule != null) ? lagClearModule.getLagClearConfig() : null;
    }

    public MilestoneModule getMilestoneModule() {
        return this.milestoneModule;
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

    public CustomArmorModule getCustomArmorModule() {
        return customArmorModule;
    }

    public boolean isAdminChecking(UUID staffUUID) {
        return adminCheckMap.getOrDefault(staffUUID, false);
    }

    public DatabaseManager getDatabaseManager() {
        return (lifestealModule != null) ? lifestealModule.getDatabaseManager() : null;
    }

    public LevelModule getLevelModule() {
        return levelModule;
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

    public DailyRewardDatabase getDailyRewardDatabase() {
        return this.dailyRewardDatabase;
    }

    public RankManager getRankManager() {
        return (rankModule != null) ? rankModule.getManager() : null;
    }

    public PetsModule getPetsModule() {
        return petsModule;
    }

    public TokenManager getTokenManager() {
        if (this.tokenManager == null && this.tokenModule != null) {
            this.tokenManager = this.tokenModule.getTokenManager();
        }
        return this.tokenManager;
    }

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

    public JDA getJda() {
        return (lagClearLogger != null) ? lagClearLogger.getJDA() : null;
    }

    public MiniMessage getComponentSerializer() {
        return miniMessage;
    }

    public String getServerPrefix() {
        return serverPrefix;
    }
    public me.login.dungeon.DungeonModule getDungeonModule() {
        return this.dungeonModule;
    }
}