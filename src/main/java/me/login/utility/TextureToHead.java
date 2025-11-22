package me.login.utility;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

public class TextureToHead {

    /**
     * Creates a new PLAYER_HEAD ItemStack with the given texture URL applied.
     *
     * @param textureUrl The full texture URL.
     * @return The textured head ItemStack.
     */
    public static ItemStack getHead(String textureUrl) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        return applyTexture(head, textureUrl);
    }

    /**
     * Applies a custom texture from a Minecraft texture URL to a player head.
     * Uses a stable UUID based on the URL so the client caches the texture immediately.
     *
     * @param item       The ItemStack (must be PLAYER_HEAD)
     * @param textureUrl The full texture URL (e.g., "http://textures.minecraft.net/texture/...")
     * @return The modified ItemStack with the new texture.
     */
    public static ItemStack applyTexture(ItemStack item, String textureUrl) {
        if (item == null || item.getType() != Material.PLAYER_HEAD) {
            return item;
        }
        if (textureUrl == null || textureUrl.isEmpty()) {
            return item;
        }

        SkullMeta skullMeta = (SkullMeta) item.getItemMeta();
        if (skullMeta == null) {
            return item;
        }

        // Encode the texture URL as base64 JSON
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        String encodedData = Base64.getEncoder().encodeToString(json.getBytes());

        try {
            // --- OPTIMIZATION FIX ---
            // Generate a consistent UUID from the texture URL.
            // This allows the Minecraft client to CACHE the skin.
            UUID uuid = UUID.nameUUIDFromBytes(textureUrl.getBytes(StandardCharsets.UTF_8));

            PlayerProfile profile = Bukkit.createProfile(uuid, "CustomHead");
            profile.setProperty(new ProfileProperty("textures", encodedData));

            // Apply the profile directly to the skull meta
            skullMeta.setPlayerProfile(profile);

            item.setItemMeta(skullMeta);
        } catch (Throwable t) {
            t.printStackTrace();
        }

        return item;
    }
}