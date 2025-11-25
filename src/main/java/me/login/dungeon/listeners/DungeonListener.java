package me.login.dungeon.listeners;

import me.login.Login;
import me.login.dungeon.game.GameManager;
import me.login.dungeon.game.GameSession;
import me.login.dungeon.game.MobManager;
import me.login.dungeon.game.MobManager.MobMutation;
import me.login.dungeon.gui.DungeonGUI;
import me.login.dungeon.manager.DungeonManager;
import me.login.dungeon.manager.DungeonRewardManager;
import me.login.dungeon.model.Dungeon;
import me.login.dungeon.model.DungeonRoom;
import me.login.dungeon.utils.DungeonLogger;
import me.login.dungeon.utils.DungeonUtils;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class DungeonListener implements Listener {

    private final Login plugin;
    private final DungeonManager dungeonManager;
    private final GameManager gameManager;
    private final DungeonRewardManager rewardManager;
    private final DungeonLogger logger;

    public DungeonListener(Login plugin, DungeonManager dungeonManager, GameManager gameManager, DungeonRewardManager rewardManager, DungeonLogger logger) {
        this.plugin = plugin;
        this.dungeonManager = dungeonManager;
        this.gameManager = gameManager;
        this.rewardManager = rewardManager;
        this.logger = logger;
    }

    // --- NEW: PROTECT MARKERS ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onMarkerDamage(EntityDamageEvent event) {
        // Prevent markers from being destroyed by explosions, players, etc.
        if (event.getEntity().getPersistentDataContainer().has(MobManager.MARKER_KEY, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onMarkerInteract(PlayerInteractAtEntityEvent event) {
        // Prevent right-clicking markers
        if (event.getRightClicked().getPersistentDataContainer().has(MobManager.MARKER_KEY, PersistentDataType.INTEGER)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityTeleport(EntityTeleportEvent event) {
        // Prevent dungeon mobs (like Endermen) from teleporting out of rooms
        if (event.getEntity().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    // --- BLOCK RESTORATION LOGIC ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDungeonBlockBreak(BlockBreakEvent event) {
        if (dungeonManager.isSettingUp(event.getPlayer())) return;

        // Record any block break in a dungeon world for restoration later
        for (GameSession s : gameManager.getAllSessions()) {
            if (s.getDungeon().getSpawnLocation().getWorld().equals(event.getBlock().getWorld())) {
                s.recordBlockChange(event.getBlock());
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (MobManager.IS_SPAWNING) {
            event.setCancelled(false);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (MobManager.IS_SPAWNING) {
            event.setCancelled(false);
            return;
        }
        if (event.getEntity().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(false);
            return;
        }

        String spawnWorld = event.getLocation().getWorld().getName();
        boolean isDungeonWorld = gameManager.getAllSessions().stream()
                .anyMatch(s -> s.getDungeon().getSpawnLocation().getWorld().getName().equals(spawnWorld));
        if (isDungeonWorld) event.setCancelled(true);
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            boolean isPlayer = event.getDamager() instanceof Player;
            boolean isPet = false;

            if (event.getDamager().hasMetadata("pet_owner") || event.getDamager().getCustomName() != null) isPet = true;
            if (event.getDamager() instanceof Tameable) { if (((Tameable) event.getDamager()).getOwner() instanceof Player) isPet = true; }

            if (!isPlayer && !isPet && !(event.getDamager() instanceof Projectile && ((Projectile)event.getDamager()).getShooter() instanceof Player)) {
                event.setCancelled(true);
                return;
            }
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof LivingEntity) {
            LivingEntity damager = (LivingEntity) event.getDamager();
            if (damager.getPersistentDataContainer().has(MobManager.MUTATION_KEY, PersistentDataType.STRING)) {
                String mutationName = damager.getPersistentDataContainer().get(MobManager.MUTATION_KEY, PersistentDataType.STRING);
                MobMutation mutation = MobMutation.valueOf(mutationName);
                Player victim = (Player) event.getEntity();

                if (mutation == MobMutation.CORRUPTED_ZOMBIE) {
                    if (Math.random() < 0.25) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 0, false, false));
                        victim.sendMessage("§aYou were poisoned by a Corrupted Zombie!");
                    }
                }
                else if (mutation == MobMutation.ZOMBIE_KNIGHT) {
                    damager.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 40, 1, false, false));
                }
            }
        }

        if (event.getEntity() instanceof Player && event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof LivingEntity) {
                LivingEntity shooter = (LivingEntity) proj.getShooter();
                if (shooter.getPersistentDataContainer().has(MobManager.MUTATION_KEY, PersistentDataType.STRING)) {
                    String mutationName = shooter.getPersistentDataContainer().get(MobManager.MUTATION_KEY, PersistentDataType.STRING);
                    MobMutation mutation = MobMutation.valueOf(mutationName);
                    Player victim = (Player) event.getEntity();

                    if (mutation == MobMutation.SKELETON_MASTER) {
                        victim.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 0, false, false));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getShooter() instanceof LivingEntity) {
            LivingEntity shooter = (LivingEntity) event.getEntity().getShooter();
            if (shooter.getPersistentDataContainer().has(MobManager.MUTATION_KEY, PersistentDataType.STRING)) {
                String mutationName = shooter.getPersistentDataContainer().get(MobManager.MUTATION_KEY, PersistentDataType.STRING);
                MobMutation mutation = MobMutation.valueOf(mutationName);

                if (mutation == MobMutation.WITHER_GUARD && event.getEntity() instanceof Arrow) {
                    event.getEntity().setFireTicks(100);
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getRightClicked().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            ItemStack hand = event.getPlayer().getInventory().getItemInMainHand();
            if (hand.getType() == Material.LEAD) {
                event.setCancelled(true);
                DungeonUtils.error(event.getPlayer(), "You cannot capture Dungeon Mobs!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        GameSession session = gameManager.getSession(player);

        if (session != null) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                player.spigot().respawn();
                gameManager.failDungeon(player, "You died in the Dungeon!");
            }, 2L);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        GameSession session = gameManager.getSession(player);

        if (session != null) {
            if (!player.getWorld().equals(session.getDungeon().getSpawnLocation().getWorld())) {
                gameManager.endSession(player);
            }
        }
    }

    @EventHandler
    public void onMobCombust(EntityCombustEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onSetupBreak(BlockBreakEvent event) {
        if (dungeonManager.isSettingUp(event.getPlayer())) {
            event.setCancelled(true);
            dungeonManager.handleBlockBreak(event.getPlayer(), event.getBlock().getLocation());
        }
    }
    @EventHandler
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (CitizensAPI.getNPCRegistry().isNPC(event.getRightClicked())) {
            int id = CitizensAPI.getNPCRegistry().getNPC(event.getRightClicked()).getId();
            if (id == plugin.getConfig().getInt("dungeon-npc-id", -1)) DungeonGUI.openStartMenu(event.getPlayer());
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        if (session.isKeyEntity(event.getRightClicked())) {
            event.setCancelled(true);
            session.pickupKey();
        }

        if (session.isBuffOrb(event.getRightClicked())) {
            event.setCancelled(true);
            session.collectBuff();
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        String t = event.getView().getTitle();
        if (t.contains("Dungeon Start") || t.contains("RNG Meter") || t.contains("Dungeon Rewards")) event.setCancelled(true);
    }
    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getCurrentItem() == null) return;
        Player p = (Player) event.getWhoClicked();
        String t = event.getView().getTitle();
        if (t.contains("Dungeon Start")) {
            event.setCancelled(true);
            if (event.getSlot() == 13) {
                Dungeon free = dungeonManager.findEmptyDungeon();
                if (free != null) { gameManager.startDungeon(p, free.getId()); p.closeInventory(); }
                else DungeonUtils.error(p, "No dungeons available!");
            }
        } else if (t.contains("RNG Meter")) {
            event.setCancelled(true);
            ItemStack c = event.getCurrentItem();
            if (c != null && c.hasItemMeta()) {
                String id = c.getItemMeta().getPersistentDataContainer().get(DungeonGUI.ITEM_ID_KEY, PersistentDataType.STRING);
                if (id != null) { rewardManager.setSelectedDrop(p.getUniqueId(), id); DungeonUtils.msg(p, "Selected drop: <gold>"+id); p.closeInventory(); }
            }
        } else if (t.contains("Dungeon Rewards")) {
            event.setCancelled(true);
            if (event.getCurrentItem().getType() != Material.GRAY_STAINED_GLASS_PANE) {
                p.getInventory().addItem(event.getCurrentItem());
                DungeonUtils.msg(p, "<green>Reward claimed!");
                logger.logDrop(p.getName(), event.getCurrentItem().getType().toString(), "Unknown");
                p.closeInventory();
                gameManager.endSession(p);
            }
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();

        if (dungeonManager.isSettingUp(player)) {
            if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                event.setCancelled(true);
                dungeonManager.handleInteract(player, block);
                return;
            }
        }

        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if (block.getLocation().equals(session.getDungeon().getRewardChestLocation())) {
                event.setCancelled(true);
                DungeonGUI.openRewardChest(player, rewardManager.generateChestRewards(player.getUniqueId()));
                rewardManager.addRun(player.getUniqueId());
                return;
            }
            if (session.isChestUsed(block.getLocation()) || session.getDungeon().getChestLocations().contains(block.getLocation())) {
                event.setCancelled(true);
                session.triggerChest(block.getLocation());
                return;
            }
        }

        if (block.getType() != Material.COAL_BLOCK) return;

        if (session.getCurrentRoomId() == 0) {
            if (session.getDungeon().getEntryDoor().containsBlock(block.getLocation())) {
                session.openDoor(session.getDungeon().getEntryDoor());
                session.advanceRoom();
                session.startRoom();
                DungeonUtils.msg(player, "Entered Dungeon!");
            }
            return;
        }

        DungeonRoom currentRoom = session.getDungeon().getRoom(session.getCurrentRoomId());
        if (currentRoom != null && currentRoom.getDoorRegion() != null) {
            if (currentRoom.getDoorRegion().containsBlock(block.getLocation())) {
                if (!session.hasKey()) {
                    DungeonUtils.error(player, "Door Locked: Kill all mobs to spawn the Key.");
                    return;
                }
                session.setHasKey(false);
                session.openDoor(currentRoom.getDoorRegion());
                session.advanceRoom();
                session.startRoom();
                DungeonUtils.msg(player, "<green>Door unlocked! Proceeding...</green>");
                return;
            }
        }

        if (session.getDungeon().getBossRoomDoor() != null && session.getDungeon().getBossRoomDoor().containsBlock(block.getLocation())) {
            if (session.getCurrentRoomId() == 6) {
                if (!session.hasKey()) { DungeonUtils.error(player, "Key required!"); return; }
                session.setHasKey(false);
                session.openDoor(session.getDungeon().getBossRoomDoor());
                session.advanceRoom();
                session.startRoom();
            }
            return;
        }

        if (session.getDungeon().getRewardDoor() != null && session.getDungeon().getRewardDoor().containsBlock(block.getLocation())) {
            if (session.isBossDead()) {
                session.openDoor(session.getDungeon().getRewardDoor());
            } else {
                DungeonUtils.error(player, "Defeat the Boss first!");
            }
        }
    }

    @EventHandler
    public void onMobDeath(EntityDeathEvent event) {
        Entity entity = event.getEntity();

        if (entity.getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            event.getDrops().clear();
        }
        if (entity.getPersistentDataContainer().has(MobManager.UNDEAD_KEY, PersistentDataType.BYTE)) {
            event.getDrops().clear();
        }

        GameSession session = gameManager.getSessionByMob(entity);
        if (session != null) {
            if (entity.equals(session.getBossEntity())) {
                session.handleBossDeath();
            } else {
                session.handleMobDeath(entity);
                if (session.isBossActive()) session.checkBossHealth();
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameManager.endSession(event.getPlayer());
    }
}