package me.login.misc.firesale;

import me.login.Login;
import me.login.misc.firesale.gui.FiresaleGUI;
import me.login.misc.firesale.model.Firesale;
import net.citizensnpcs.api.event.NPCRightClickEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*; // Import Bukkit
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Optional;

public class FiresaleListener implements Listener {

    private final Login plugin;
    private final FiresaleManager manager;
    private final FiresaleGUI gui;
    private final MiniMessage miniMessage; // Added for messages
    private final int npcId;

    public static final String METADATA_KEY = "OPEN_FIRESALE_MENU";
    private static final String PREVIEW_METADATA_KEY = "firesale_preview";

    public FiresaleListener(Login plugin, FiresaleManager manager, FiresaleGUI gui, int npcId) {
        this.plugin = plugin;
        this.manager = manager;
        this.gui = gui;
        this.npcId = npcId;
        this.miniMessage = plugin.getComponentSerializer(); // Initialize MiniMessage
    }

    @EventHandler
    public void onNPCInteract(NPCRightClickEvent event) {
        if (event.getNPC().getId() == npcId) {
            gui.openMainMenu(event.getClicker());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            manager.checkPlayerJoin(event.getPlayer());
        }, 60L);
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.checkPlayerJoin(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (event.getPlayer().hasMetadata(METADATA_KEY)) {
            event.getPlayer().removeMetadata(METADATA_KEY, plugin);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        if (!player.hasMetadata(METADATA_KEY)) {
            return;
        }

        Inventory top = event.getView().getTopInventory();
        if (top == null || top.getSize() != 36 || top.getType() != InventoryType.CHEST) {
            player.removeMetadata(METADATA_KEY, plugin);
            return;
        }

        event.setCancelled(true); // Cancel by default in this menu

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir() || clickedItem.getItemMeta() == null) {
            return;
        }

        PersistentDataContainer pdc = clickedItem.getItemMeta().getPersistentDataContainer();
        if (pdc.has(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING)) {
            String action = pdc.get(FiresaleGUI.NBT_ACTION_KEY, PersistentDataType.STRING);
            if (action == null) return;

            switch (action) {
                case "open_sales_menu":
                    gui.openActiveSalesMenu(player);
                    break;
                case "open_history_menu":
                    gui.openHistoryMenu(player, 0);
                    break;
                case "buy_sale_item":
                    if (pdc.has(FiresaleGUI.NBT_SALE_ID_KEY, PersistentDataType.INTEGER)) {
                        int saleId = pdc.get(FiresaleGUI.NBT_SALE_ID_KEY, PersistentDataType.INTEGER);
                        Optional<Firesale> saleOpt = manager.getSaleById(saleId);

                        if (saleOpt.isPresent()) {
                            Firesale sale = saleOpt.get();

                            // --- NEW ARMOR STAND PREVIEW LOGIC ---
                            if (event.isShiftClick() && event.isRightClick()) {
                                PersistentDataContainer itemPdc = clickedItem.getItemMeta().getPersistentDataContainer();

                                if (itemPdc.has(FiresaleGUI.NBT_DYE_HEX_KEY, PersistentDataType.STRING)) {
                                    String hex = itemPdc.get(FiresaleGUI.NBT_DYE_HEX_KEY, PersistentDataType.STRING);
                                    handleArmorStandPreview(player, hex);
                                    player.closeInventory(); // Close menu after preview
                                    return; // Don't proceed to purchase
                                }
                            }
                            // --- END NEW LOGIC ---

                            manager.attemptPurchase(player, sale);
                        }
                    }
                    break;
                case "history_next_page":
                    int nextPage = pdc.getOrDefault(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER, 0);
                    gui.openHistoryMenu(player, nextPage);
                    break;
                case "history_prev_page":
                    int prevPage = pdc.getOrDefault(FiresaleGUI.NBT_PAGE_KEY, PersistentDataType.INTEGER, 0);
                    gui.openHistoryMenu(player, prevPage);
                    break;
            }
        }
    }

    /**
     * Spawns a temporary armor stand with dyed armor for preview.
     */
    private void handleArmorStandPreview(Player player, String hex) {
        // 1. Parse Color
        org.bukkit.Color bukkitColor;
        try {
            // Remove # if present
            if (hex.startsWith("#")) {
                hex = hex.substring(1);
            }
            int r = Integer.valueOf(hex.substring(0, 2), 16);
            int g = Integer.valueOf(hex.substring(2, 4), 16);
            int b = Integer.valueOf(hex.substring(4, 6), 16);
            bukkitColor = org.bukkit.Color.fromRGB(r, g, b);
        } catch (Exception e) {
            player.sendMessage(miniMessage.deserialize("<red>Could not parse item color for preview.</red>"));
            return;
        }

        // --- FIX: Use hardcoded location ---
        World world = Bukkit.getWorld("lifesteal");
        if (world == null) {
            player.sendMessage(miniMessage.deserialize("<red>Cannot spawn preview: World 'lifesteal' not found.</red>"));
            return;
        }
        // Location(world, x, y, z, yaw, pitch) - Yaw 180 = facing South
        Location spawnLoc = new Location(world, 2.5, 63, -27.5, 180, 0);
        // --- END FIX ---

        // 3. Spawn Armor Stand
        ArmorStand as = world.spawn(spawnLoc, ArmorStand.class);
        as.setInvulnerable(true);
        as.setGravity(false);
        as.setVisible(true); // Make stand visible
        as.setMarker(false); // Make equipment visible
        as.setBasePlate(false); // Hide base plate

        // 4. Create and apply dyed armor
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        ItemStack legs = new ItemStack(Material.LEATHER_LEGGINGS);
        ItemStack boots = new ItemStack(Material.LEATHER_BOOTS);

        for (ItemStack armor : List.of(helmet, chest, legs, boots)) {
            ItemMeta meta = armor.getItemMeta();
            if (meta instanceof LeatherArmorMeta) {
                ((LeatherArmorMeta) meta).setColor(bukkitColor);
                armor.setItemMeta(meta);
            }
        }

        as.getEquipment().setHelmet(helmet);
        as.getEquipment().setChestplate(chest);
        as.getEquipment().setLeggings(legs);
        as.getEquipment().setBoots(boots);

        // 5. Add metadata to prevent clicking it and for particle task
        as.setMetadata(PREVIEW_METADATA_KEY, new FixedMetadataValue(plugin, true));

        // 6. Schedule particle task and removal
        new BukkitRunnable() {
            int ticks = 0;
            final int durationTicks = 7 * 20; // 7 seconds
            final int particleInterval = 5; // every 5 ticks

            @Override
            public void run() {
                if (ticks > durationTicks || as.isDead() || !as.isValid()) {
                    if (as.isValid()) {
                        as.remove();
                    }
                    this.cancel();
                    return;
                }

                // Spawn particles
                if (ticks % particleInterval == 0) {
                    // --- FIX: Reduced particle count from 5 to 2 ---
                    as.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, as.getLocation().clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0);
                }

                ticks++;
            }
        }.runTaskTimer(plugin, 0L, 1L); // Run every tick
    }

    /**
     * Prevents players from interacting with the preview armor stand.
     */
    @EventHandler
    public void onPreviewInteract(PlayerInteractAtEntityEvent event) {
        if (event.getRightClicked().hasMetadata(PREVIEW_METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    /**
     * Prevents the preview armor stand from taking any damage.
     */
    @EventHandler
    public void onPreviewDamage(EntityDamageEvent event) {
        if (event.getEntity().hasMetadata(PREVIEW_METADATA_KEY)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        if (!player.hasMetadata(METADATA_KEY)) return;

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;

            Inventory top = player.getOpenInventory().getTopInventory();

            if (top == null || top.getType() != InventoryType.CHEST) {
                player.removeMetadata(METADATA_KEY, plugin);
            }
        });
    }
}