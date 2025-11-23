package me.login.dungeon.listeners;

import me.login.Login;
import me.login.dungeon.game.GameManager;
import me.login.dungeon.game.GameSession;
import me.login.dungeon.game.MobManager;
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
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

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

    // --- BURNING & SPAWNING ---
    @EventHandler
    public void onMobCombust(EntityCombustEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {
            event.setCancelled(true);
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
        }
    }

    @EventHandler
    public void onMobDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) return;
        LivingEntity entity = (LivingEntity) event.getEntity();

        if (entity.getPersistentDataContainer().has(MobManager.DUNGEON_MOB_KEY, PersistentDataType.BYTE)) {

            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) return;

            boolean isPlayerDamage = false;

            if (event instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) event).getDamager();
                if (damager instanceof Player) {
                    isPlayerDamage = true;
                } else if (damager instanceof Projectile) {
                    if (((Projectile) damager).getShooter() instanceof Player) {
                        isPlayerDamage = true;
                    }
                }
            }

            if (!isPlayerDamage) {
                event.setCancelled(true);
                return;
            }

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!entity.isDead()) MobManager.updateMobName(entity);
            }, 1L);

            if (entity instanceof org.bukkit.entity.Zombie) {
                for (GameSession session : gameManager.getAllSessions()) {
                    if (entity.equals(session.getBossEntity()) && session.isBossInvulnerable()) {
                        event.setCancelled(true);
                    }
                    if (entity.equals(session.getBossEntity())) {
                        session.checkBossHealth();
                    }
                }
            }
        }
    }

    // --- SETUP/NPC/GUI ---
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

    // --- Armor Stand Key Interaction ---
    @EventHandler
    public void onEntityInteract(PlayerInteractAtEntityEvent event) {
        Player player = event.getPlayer();
        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        // Check if clicked entity is the Key Armor Stand
        if (session.isKeyEntity(event.getRightClicked())) {
            event.setCancelled(true);
            session.pickupKey();
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
        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        if (block.getType() == Material.CHEST && block.getLocation().equals(session.getDungeon().getRewardChestLocation())) {
            event.setCancelled(true);
            DungeonGUI.openRewardChest(player, rewardManager.generateChestRewards(player.getUniqueId()));
            rewardManager.addRun(player.getUniqueId());
            return;
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
                // VIRTUAL KEY CHECK: Just check session.hasKey(), no item check!
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