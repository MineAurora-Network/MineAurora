package me.login.utility;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.Base64;
import java.util.UUID;

public class TextureToHead {

    /**
     * Applies a custom texture from a Minecraft texture URL to a player head.
     * Works on modern Paper (1.17–1.21).
     *
     * @param item The ItemStack (must be PLAYER_HEAD)
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
            // Paper API: create a PlayerProfile and assign texture property
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID(), "CustomHead");
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
