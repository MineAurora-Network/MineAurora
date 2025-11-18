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
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
            // Spawn at 118
            this.discordLoc = new Location(hub, 174.5, 118, -1.5);
            this.storeLoc = new Location(hub, 174.5, 118, -9.5);
        }
    }

    public void enable() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getCommand("rotatinghead").setExecutor(new HubHeadCommand(this));
        forceCleanupAll();

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
        }.runTaskTimer(plugin, 20L, 1L);

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.isEnabled()) {
                    this.cancel();
                    return;
                }
                runStrictWatchdog();
            }
        }.runTaskTimer(plugin, 100L, 100L);
    }

    public void disable() {
        forceCleanupAll();
    }

    public void forceCleanupAll() {
        World hub = Bukkit.getWorld("hub");
        if (hub == null) return;
        for (Entity entity : hub.getEntities()) {
            if (entity.getType() == EntityType.ARMOR_STAND &&
                    entity.getPersistentDataContainer().has(ID_KEY, PersistentDataType.STRING)) {
                entity.remove();
            }
        }
        activeHeads.clear();
        activeHolograms.clear();
    }

    private void runStrictWatchdog() {
        World hub = Bukkit.getWorld("hub");
        if (hub == null) return;

        List<Entity> discordHeads = new ArrayList<>();
        List<Entity> discordHolos = new ArrayList<>();
        List<Entity> storeHeads = new ArrayList<>();
        List<Entity> storeHolos = new ArrayList<>();

        for (Entity e : hub.getEntities()) {
            if (e.getType() != EntityType.ARMOR_STAND) continue;
            String id = e.getPersistentDataContainer().get(ID_KEY, PersistentDataType.STRING);
            if (id == null) continue;

            switch (id) {
                case HEAD_DISCORD -> discordHeads.add(e);
                case HEAD_DISCORD + "_holo" -> discordHolos.add(e);
                case HEAD_STORE -> storeHeads.add(e);
                case HEAD_STORE + "_holo" -> storeHolos.add(e);
            }
        }

        enforceSingleEntity(discordHeads);
        enforceSingleEntity(discordHolos);
        enforceSingleEntity(storeHeads);
        enforceSingleEntity(storeHolos);

        if (discordHeads.isEmpty() || discordHolos.isEmpty()) {
            discordHeads.forEach(Entity::remove);
            discordHolos.forEach(Entity::remove);
            spawnHead(HEAD_DISCORD, discordLoc, discordHeadUrl, "<blue><bold>Discord</bold></blue>");
        } else {
            activeHeads.put(HEAD_DISCORD, (ArmorStand) discordHeads.get(0));
            activeHolograms.put(HEAD_DISCORD, (ArmorStand) discordHolos.get(0));
        }

        if (storeHeads.isEmpty() || storeHolos.isEmpty()) {
            storeHeads.forEach(Entity::remove);
            storeHolos.forEach(Entity::remove);
            spawnHead(HEAD_STORE, storeLoc, storeHeadUrl, "<gold><bold>Store</bold></gold>");
        } else {
            activeHeads.put(HEAD_STORE, (ArmorStand) storeHeads.get(0));
            activeHolograms.put(HEAD_STORE, (ArmorStand) storeHolos.get(0));
        }
    }

    private void enforceSingleEntity(List<Entity> entities) {
        if (entities.size() > 1) {
            for (int i = 1; i < entities.size(); i++) {
                entities.get(i).remove();
            }
            entities.subList(1, entities.size()).clear();
        }
    }

    public void spawnHead(String id, Location location, String textureUrl, String title) {
        if (location == null || location.getWorld() == null) return;

        ArmorStand headStand = (ArmorStand) location.getWorld().spawnEntity(location, EntityType.ARMOR_STAND);
        headStand.setVisible(false);
        headStand.setGravity(false);
        headStand.setBasePlate(false);
        headStand.setInvulnerable(true);
        headStand.setSmall(false);
        headStand.setMarker(false);

        ItemStack headItem = new ItemStack(Material.PLAYER_HEAD);
        headItem = TextureToHead.applyTexture(headItem, textureUrl);
        headStand.getEquipment().setHelmet(headItem);

        headStand.getPersistentDataContainer().set(ID_KEY, PersistentDataType.STRING, id);
        activeHeads.put(id, headStand);

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

        if (head == null || !head.isValid() || origin == null) return;

        // --- MATH: 118 to 119 (0 to +1 block) ---
        // Sine: -1 to 1
        // Sine + 1: 0 to 2
        // (Sine + 1) * 0.5: 0 to 1
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
            if (p.hasPermission("mineaurora.admin")) return;
            String cmd = event.getMessage().split(" ")[0].toLowerCase();
            if (!cmd.equals("/mvtp")) {
                event.setCancelled(true);
                p.sendMessage(miniMessage.deserialize("<red>You cannot use commands in the Hub except /mvtp!</red>"));
            }
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() &&
                event.getFrom().getBlockY() == event.getTo().getBlockY() &&
                event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        Player p = event.getPlayer();
        if (!p.getWorld().getName().equals("hub")) return;

        Location loc = p.getLocation();
        Block blockBelow = loc.getBlock().getRelative(BlockFace.DOWN);

        if (blockBelow.getType() == Material.MAGENTA_GLAZED_TERRACOTTA) {
            if (blockBelow.getX() == 174 && blockBelow.getY() == 118 && blockBelow.getZ() == -5) {
                Vector direction = p.getLocation().getDirection();
                direction.setY(1.2);
                direction.multiply(3.5);
                p.setVelocity(direction);
                p.playSound(p.getLocation(), Sound.ENTITY_BAT_TAKEOFF, 1f, 1f);
                p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.5, 0.5, 0.5, 0.1);
            }
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

        if (id.startsWith(HEAD_DISCORD)) {
            String link = plugin.getConfig().getString("discord-server-link", "https://discord.gg/example");
            if (!link.startsWith("http")) {
                link = "https://" + link;
            }
            sendClickableLink(player, prefix, "<blue>Click here to join our Discord!</blue>", link);
        } else if (id.startsWith(HEAD_STORE)) {
            String link = plugin.getConfig().getString("store-link", "https://store.example.com");
            if (!link.startsWith("http")) {
                link = "https://" + link;
            }
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