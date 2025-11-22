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
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
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

    // --- INTERACTION LOGIC (DEBUGGED) ---
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Player player = event.getPlayer();
        GameSession session = gameManager.getSession(player);
        if (session == null) return;

        // 1. Chest Logic (Special Case)
        if (block.getType() == Material.CHEST && block.getLocation().equals(session.getDungeon().getRewardChestLocation())) {
            event.setCancelled(true);
            DungeonGUI.openRewardChest(player, rewardManager.generateChestRewards(player.getUniqueId()));
            rewardManager.addRun(player.getUniqueId());
            return;
        }

        // 2. COAL BLOCK CHECK (All active doors are Coal Blocks)
        if (block.getType() != Material.COAL_BLOCK) {
            return; // Ignore interactions with non-doors
        }

        ItemStack held = player.getInventory().getItemInMainHand();
        boolean isKey = MobManager.isKey(held);

        // 3. Entry Door (Room 0)
        if (session.getCurrentRoomId() == 0) {
            if (session.getDungeon().getEntryDoor().containsBlock(block.getLocation())) {
                session.openDoor(session.getDungeon().getEntryDoor());
                session.advanceRoom();
                session.startRoom();
                DungeonUtils.msg(player, "Entered Dungeon!");
            }
            return;
        }

        // 4. Room Doors
        DungeonRoom currentRoom = session.getDungeon().getRoom(session.getCurrentRoomId());
        if (currentRoom != null && currentRoom.getDoorRegion() != null) {
            // Check if clicked block is inside this room's door region
            if (currentRoom.getDoorRegion().containsBlock(block.getLocation())) {

                // Debug Info
                if (!session.hasKey()) {
                    DungeonUtils.error(player, "Door Locked: Kill all mobs to spawn the Key.");
                    return;
                }

                if (isKey) {
                    held.setAmount(held.getAmount() - 1); // Remove key
                    session.setHasKey(false); // Reset logic flag
                    session.openDoor(currentRoom.getDoorRegion());
                    session.advanceRoom();
                    session.startRoom();
                    DungeonUtils.msg(player, "<green>Door unlocked! Proceeding...</green>");
                } else {
                    DungeonUtils.error(player, "Door Locked: Right-click with the <dark_red>Ominous Trial Key</dark_red>.");
                    plugin.getLogger().info("[Debug] Player clicked door but item was not key. Item: " + held.getType());
                }
                return;
            }
        }

        // 5. Boss Room Door (Room 6 -> 7)
        if (session.getDungeon().getBossRoomDoor() != null && session.getDungeon().getBossRoomDoor().containsBlock(block.getLocation())) {
            if (session.getCurrentRoomId() == 6) {
                if (!session.hasKey()) { DungeonUtils.error(player, "Key required!"); return; }
                if (isKey) {
                    held.setAmount(held.getAmount() - 1);
                    session.setHasKey(false);
                    session.openDoor(session.getDungeon().getBossRoomDoor());
                    session.advanceRoom();
                    session.startRoom();
                } else { DungeonUtils.error(player, "Use the Key!"); }
            }
            return;
        }

        // 6. Treasure Door
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
        Player killer = event.getEntity().getKiller();

        if (killer != null) {
            GameSession session = gameManager.getSession(killer);
            if (session != null) {
                if (entity.equals(session.getBossEntity())) {
                    session.handleBossDeath();
                } else {
                    session.handleMobDeath(entity);
                    if (session.isBossActive()) session.checkBossHealth();
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gameManager.endSession(event.getPlayer());
    }
}