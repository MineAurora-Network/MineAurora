package me.login.misc.hub;

import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;

public class HubHeadModule implements Listener {

    private final Login plugin;
    private final MiniMessage miniMessage;

    private final Map<String, ArmorStand> activeHeads = new HashMap<>();
    private final Map<String, ArmorStand> activeHolograms = new HashMap<>();

    public static final String HEAD_DISCORD = "discord";
    public static final String HEAD_STORE = "store";
    private final NamespacedKey ID_KEY;

    private final String discordHeadUrl = "http://textures.minecraft.net/texture/3b94843d340abadbd6401ef4ec74dcec4b669661065bd1a01f9a5905a8919c72";
    private final String storeHeadUrl = "http://textures.minecraft.net/texture/cfe2449d227fa991f2a7102709d9982de1f78f41182628bdae39462254c681a3";

    private final Location discordLoc;
    private final Location storeLoc;

    public HubHeadModule(Login plugin) {
        this.plugin = plugin;
        this.miniMessage = MiniMessage.miniMessage();
        this.ID_KEY = new NamespacedKey(plugin, "hub_head_id");

        World hub = Bukkit.getWorld("hub");
        if (hub == null) {
            plugin.getLogger().severe("World 'hub' not found! HubHeadModule cannot set locations.");
            this.discordLoc = null;
            this.storeLoc = null;
        } else {
            // Coordinates
            this.discordLoc = new Location(hub, 174.5, 118, -1.5);
            this.storeLoc = new Location(hub, 174.5, 118, -9.5);
        }
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("rotatinghead").setExecutor(new HubHeadCommand(this));

        // Initialize Heads with delay to ensure worlds are fully ready
        Bukkit.getScheduler().runTask(plugin, () -> {
            initializeLocation(HEAD_DISCORD, discordLoc, discordHeadUrl, "<blue><bold>Discord</bold></blue>");
            initializeLocation(HEAD_STORE, storeLoc, storeHeadUrl, "<gold><bold>Store</bold></gold>");
        });

        // Animation Task
        new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    this.cancel();
                    return;
                }
                angle += 0.05;
                animateHead(HEAD_DISCORD, discordLoc, angle);
                animateHead(HEAD_STORE, storeLoc, angle);
            }
        }.runTaskTimer(plugin, 40L, 1L);
    }

    public void disable() {
        // We do NOT remove entities here. We leave them persistent.
        // We only un-force chunks if we want to save memory, but user requested "always active".
        // To be safe and clean, we stop tracking them, but the entities stay in the world.
        activeHeads.clear();
        activeHolograms.clear();

        // Optional: Un-force chunks on disable to prevent ghost chunks when server is off?
        // If the server restarts, enable() will re-force them.
        if (discordLoc != null && discordLoc.getWorld() != null) discordLoc.getChunk().setForceLoaded(false);
        if (storeLoc != null && storeLoc.getWorld() != null) storeLoc.getChunk().setForceLoaded(false);
    }

    /**
     * securely loads the chunk, finds existing entities, or spawns new ones.
     */
    private void initializeLocation(String id, Location loc, String texture, String title) {
        if (loc == null || loc.getWorld() == null) return;

        // 1. Force Load Chunk
        Chunk chunk = loc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }
        chunk.setForceLoaded(true); // Keep this chunk loaded ALWAYS to prevent "doubling" issues

        // 2. Scan for EXISTING entities in this chunk to prevent duplicates
        // This handles cases where the server crashed or plugin reloaded without running disable()
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND) {
                String storedId = entity.getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING);
                if (storedId != null) {
                    if (storedId.equals(id)) {
                        // Found existing Head
                        if (activeHeads.containsKey(id)) {
                            entity.remove(); // Duplicate found in same scan
                        } else {
                            activeHeads.put(id, (ArmorStand) entity);
                        }
                    } else if (storedId.equals(id + "_holo")) {
                        // Found existing Hologram
                        if (activeHolograms.containsKey(id)) {
                            entity.remove(); // Duplicate
                        } else {
                            activeHolograms.put(id, (ArmorStand) entity);
                        }
                    }
                }
            }
        }

        // 3. If missing, Spawn
        if (!activeHeads.containsKey(id)) {
            spawnHeadEntity(id, loc, texture);
        }
        if (!activeHolograms.containsKey(id)) {
            spawnHoloEntity(id, loc, title);
        }
    }

    public void spawnHead(String id, Location location, String textureUrl, String title) {
        // Wrapper for command usage - forces a respawn
        if (activeHeads.containsKey(id)) activeHeads.get(id).remove();
        if (activeHolograms.containsKey(id)) activeHolograms.get(id).remove();

        spawnHeadEntity(id, location, textureUrl);
        spawnHoloEntity(id, location, title);
    }

    private void spawnHeadEntity(String id, Location location, String textureUrl) {
        ArmorStand headStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        headStand.setVisible(false);
        headStand.setGravity(false);
        headStand.setBasePlate(false);
        headStand.setInvulnerable(true);
        headStand.setSmall(false);
        headStand.setMarker(false); // Must be false to hold items properly in some versions, or adjust pose

        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        headItem = TextureToHead.applyTexture(headItem, textureUrl);
        headStand.getEquipment().setHelmet(headItem);

        headStand.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id);
        activeHeads.put(id, headStand);
    }

    private void spawnHoloEntity(String id, Location location, String title) {
        Location holoLoc = location.clone().add(0, 0.5, 0);
        ArmorStand holoStand = (ArmorStand) location.getWorld().spawnEntity(holoLoc, EntityType.ARMOR_STAND);
        holoStand.setVisible(false);
        holoStand.setGravity(false);
        holoStand.setMarker(true);
        holoStand.setCustomNameVisible(true);
        holoStand.customName(miniMessage.deserialize(title));

        holoStand.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id + "_holo");
        activeHolograms.put(id, holoStand);
    }

    private void animateHead(String id, Location origin, double angle) {
        ArmorStand head = activeHeads.get(id);
        ArmorStand holo = activeHolograms.get(id);

        // If entities became invalid (killed by command, etc), try to re-init
        if (head == null || !head.isValid() || holo == null || !holo.isValid()) {
            // We can optionally try to respawn here, but be careful of lag loops.
            // For now, we just skip animation.
            return;
        }

        double yOffset = (Math.sin(angle) + 1) * 0.5;
        Location newHeadLoc = origin.clone().add(0, yOffset, 0);
        float newYaw = (head.getLocation().getYaw() + 4f) % 360;
        newHeadLoc.setYaw(newYaw);

        head.teleport(newHeadLoc);

        if (holo != null && holo.isValid()) {
            Location newHoloLoc = newHeadLoc.clone().add(0, 2.0, 0);
            holo.teleport(newHoloLoc);
        }

        Color dustColor = id.equals(HEAD_DISCORD) ? Color.fromRGB(88, 101, 242) : Color.fromRGB(255, 170, 0);
        Particle.DustOptions dustOptions = new Particle.DustOptions(dustColor, 1.0f);

        head.getWorld().spawnParticle(
                Particle.DUST,
                newHeadLoc.clone().add(0, 1.75, 0),
                1, 0.1, 0.1, 0.1, 0,
                dustOptions
        );
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        if (p.getWorld().getName().equals("hub")) {
            if (p.isOp()) return;
            String cmd = event.getMessage().split(" ")[0].toLowerCase();
            if (!cmd.equals("/mvtp")) {
                event.setCancelled(true);
                p.sendMessage(miniMessage.deserialize("<red>You cannot use commands in the Hub except /mvtp!</red>"));
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        if (!p.getWorld().getName().equals("hub")) return;

        Location loc = p.getLocation();
        Block blockBelow = loc.getBlock().getRelative(BlockFace.DOWN);

        // Generalized Magenta Glazed Terracotta Launch
        if (blockBelow.getType() == Material.MAGENTA_GLAZED_TERRACOTTA) {
            Vector direction = p.getLocation().getDirection().normalize();
            direction.multiply(3.8);
            direction.setY(0.8); // Lift
            p.setVelocity(direction);
            p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        if (event.getPlayer().getWorld().getName().equalsIgnoreCase("hub")) {
            event.setRespawnLocation(new Location(Bukkit.getWorld("hub"), 178.5, 118, -5.5));
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;

        String id = stand.getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING);
        if (id == null) return;

        event.setCancelled(true);

        Player player = event.getPlayer();
        String prefix = plugin.getConfig().getString("server_prefix", "<gray>[<gold>Server</gold>] ");

        // Check if it's the holo or the head
        if (id.startsWith(HEAD_DISCORD)) {
            String link = plugin.getConfig().getString("discord-server-link", "https://discord.gg/example");
            if (!link.startsWith("http")) link = "https://" + link;
            sendClickableLink(player, prefix, "<blue>Click here to join our Discord!</blue>", link);
        } else if (id.startsWith(HEAD_STORE)) {
            String link = plugin.getConfig().getString("store-link", "https://store.example.com");
            if (!link.startsWith("http")) link = "https://" + link;
            sendClickableLink(player, prefix, "<gold>Click here to visit our Store!</gold>", link);
        }
    }

    private void sendClickableLink(Player player, String prefix, String text, String url) {
        Component prefixComp = miniMessage.deserialize(prefix + " ");
        Component messageComp = miniMessage.deserialize(text)
                .hoverEvent(HoverEvent.showText(Component.text("Click to open!")))
                .clickEvent(ClickEvent.openUrl(url));
        player.sendMessage(prefixComp.append(messageComp));
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (isHubHead(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (isHubHead(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
    }

    private boolean isHubHead(Entity entity) {
        return entity.getPersistentDataContainer().has(ID_KEY, PersistentDataType.STRING);
    }

    public String getDiscordHeadUrl() { return discordHeadUrl; }
    public String getStoreHeadUrl() { return storeHeadUrl; }
    public Location getDiscordLoc() { return discordLoc; }
    public Location getStoreLoc() { return storeLoc; }
}