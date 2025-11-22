package me.login.pets;

import de.rapha149.signgui.SignGUI;
import me.login.Login;
import me.login.utility.TextureToHead;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class PetFruitShop implements InventoryHolder {

    private final Login plugin;
    private final PetsConfig config;
    private final PetMessageHandler messageHandler;

    private static final String XP_HEAD_URL = "http://textures.minecraft.net/texture/31d568e16be6c79d674f97ac1e949f8a8f03e3837b6f0b56a539bfc337f1ebd";
    private static final String HUNGER_HEAD_URL = "http://textures.minecraft.net/texture/4fadfa04eb880f07f2965c50990dc66246034a61999c6a271921645ee7334f67";

    public enum ShopType { MAIN, XP, HUNGER }

    private final Inventory inventory;
    private final ShopType type;
    private final NamespacedKey fruitKey;

    public PetFruitShop(Login plugin, PetsConfig config, PetMessageHandler messageHandler) {
        this.plugin = plugin;
        this.config = config;
        this.messageHandler = messageHandler;
        this.inventory = null;
        this.type = null;
        this.fruitKey = new NamespacedKey("mineaurora", "pet_fruit_id");
    }

    private PetFruitShop(Login plugin, PetsConfig config, PetMessageHandler messageHandler, ShopType type, int size, Component title) {
        this.plugin = plugin;
        this.config = config;
        this.messageHandler = messageHandler;
        this.type = type;
        this.inventory = Bukkit.createInventory(this, size, title);
        this.fruitKey = new NamespacedKey("mineaurora", "pet_fruit_id");
    }

    public void openMainMenu(Player player) {
        // FIX: Gray title (no bold)
        PetFruitShop menu = new PetFruitShop(plugin, config, messageHandler, ShopType.MAIN, 27, MiniMessage.miniMessage().deserialize("<gray>Fruit Shop"));
        menu.initializeMainItems();
        player.openInventory(menu.getInventory());
    }

    public void openXpShop(Player player) {
        // FIX: Gray title
        PetFruitShop menu = new PetFruitShop(plugin, config, messageHandler, ShopType.XP, 36, MiniMessage.miniMessage().deserialize("<gray>XP Fruits"));
        menu.initializeSubItems(true);
        player.openInventory(menu.getInventory());
    }

    public void openHungerShop(Player player) {
        // FIX: Gray title
        PetFruitShop menu = new PetFruitShop(plugin, config, messageHandler, ShopType.HUNGER, 36, MiniMessage.miniMessage().deserialize("<gray>Hunger Fruits"));
        menu.initializeSubItems(false);
        player.openInventory(menu.getInventory());
    }

    private void initializeMainItems() {
        ItemStack filler = createFiller();
        for (int i = 0; i < 27; i++) inventory.setItem(i, filler);

        ItemStack xpHead = new ItemStack(Material.PLAYER_HEAD);
        xpHead = TextureToHead.applyTexture(xpHead, XP_HEAD_URL);
        ItemMeta xpMeta = xpHead.getItemMeta();
        // FIX: No Bold, No Italic
        xpMeta.displayName(MiniMessage.miniMessage().deserialize("<green>XP Fruits").decoration(TextDecoration.ITALIC, false));
        xpMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<gray>Click to buy XP fruits").decoration(TextDecoration.ITALIC, false)));
        xpHead.setItemMeta(xpMeta);
        inventory.setItem(11, xpHead);

        ItemStack hungerHead = new ItemStack(Material.PLAYER_HEAD);
        hungerHead = TextureToHead.applyTexture(hungerHead, HUNGER_HEAD_URL);
        ItemMeta hungerMeta = hungerHead.getItemMeta();
        hungerMeta.displayName(MiniMessage.miniMessage().deserialize("<gold>Hunger Fruits").decoration(TextDecoration.ITALIC, false));
        hungerMeta.lore(List.of(MiniMessage.miniMessage().deserialize("<gray>Click to buy Hunger fruits").decoration(TextDecoration.ITALIC, false)));
        hungerHead.setItemMeta(hungerMeta);
        inventory.setItem(15, hungerHead);
    }

    private void initializeSubItems(boolean isXp) {
        int slot = 0;
        List<String> sortedFruits = new ArrayList<>(config.getFruitNames());
        Collections.sort(sortedFruits);

        for (String fruitName : sortedFruits) {
            if (slot > 26) break;
            double xpAmount = config.getFruitXp(fruitName);

            if (isXp && xpAmount <= 0) continue;
            if (!isXp && xpAmount > 0) continue;

            ItemStack item = config.getFruit(fruitName);
            if (item == null) continue;

            double price = config.getFruitPrice(fruitName);

            ItemMeta meta = item.getItemMeta();
            // FIX: Remove Bold from Name
            Component displayName = meta.displayName();
            if (displayName != null) {
                meta.displayName(displayName.decoration(TextDecoration.BOLD, false).decoration(TextDecoration.ITALIC, false));
            }

            List<Component> lore = meta.lore();
            if (lore == null) lore = new ArrayList<>();
            lore.add(Component.empty());
            // FIX: No Italic in price lore
            lore.add(MiniMessage.miniMessage().deserialize("<gray>Price: <gold>$" + String.format("%.2f", price)).decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Left-Click to Buy (1)").decoration(TextDecoration.ITALIC, false));
            lore.add(MiniMessage.miniMessage().deserialize("<yellow>Shift+Right-Click to Bulk Buy").decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);

            meta.getPersistentDataContainer().set(fruitKey, PersistentDataType.STRING, fruitName);
            item.setItemMeta(meta);

            inventory.setItem(slot++, item);
        }

        ItemStack filler = createFiller();
        for (int i = 27; i < 36; i++) inventory.setItem(i, filler);

        ItemStack back = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = back.getItemMeta();
        backMeta.displayName(MiniMessage.miniMessage().deserialize("<red>Back").decoration(TextDecoration.ITALIC, false));
        back.setItemMeta(backMeta);
        inventory.setItem(31, back);
    }

    private ItemStack createFiller() {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.empty());
        filler.setItemMeta(meta);
        return filler;
    }

    public void processBuy(Player player, ItemStack item, int quantity) {
        if (item == null || !item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(fruitKey, PersistentDataType.STRING)) return;

        String fruitId = pdc.get(fruitKey, PersistentDataType.STRING);
        double pricePer = config.getFruitPrice(fruitId);
        double totalCost = pricePer * quantity;

        Economy eco = plugin.getVaultEconomy();

        if (eco == null) {
            messageHandler.sendPlayerMessage(player, "<red>Economy system not found.</red>");
            return;
        }

        if (!eco.has(player, totalCost)) {
            messageHandler.sendPlayerMessage(player, "<red>You do not have enough money! Cost: $" + totalCost + "</red>");
            return;
        }

        EconomyResponse r = eco.withdrawPlayer(player, totalCost);
        if (r.transactionSuccess()) {
            ItemStack giveItem = config.getFruit(fruitId);
            giveItem.setAmount(quantity);

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(giveItem);
            if (!leftover.isEmpty()) {
                for (ItemStack drop : leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), drop);
                }
                messageHandler.sendPlayerMessage(player, "<yellow>Inventory full! Some items dropped at your feet.</yellow>");
            }

            messageHandler.sendPlayerMessage(player, "<green>Purchased " + quantity + "x " + fruitId + " for $" + totalCost + ".</green>");
        } else {
            messageHandler.sendPlayerMessage(player, "<red>Transaction failed: " + r.errorMessage + "</red>");
        }
    }

    public void openSignInput(Player player, ItemStack item) {
        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        if (!pdc.has(fruitKey, PersistentDataType.STRING)) return;
        String fruitId = pdc.get(fruitKey, PersistentDataType.STRING);
        double price = config.getFruitPrice(fruitId);

        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^", "Enter Quantity", "Price: $" + price)
                    .setHandler((p, lines) -> {
                        String input = lines.getLine(0).trim();
                        if (input.isEmpty()) return null;

                        try {
                            int amount = Integer.parseInt(input);
                            if (amount <= 0) {
                                messageHandler.sendPlayerMessage(p, "<red>Quantity must be positive.</red>");
                                return null;
                            }
                            Bukkit.getScheduler().runTask(plugin, () -> processBuy(p, item, amount));
                        } catch (NumberFormatException e) {
                            messageHandler.sendPlayerMessage(p, "<red>Invalid number.</red>");
                        }
                        return null;
                    })
                    .build()
                    .open(player);
        } catch (Exception e) {
            plugin.getLogger().warning("Error opening SignGUI: " + e.getMessage());
        }
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public ShopType getType() {
        return type;
    }
}