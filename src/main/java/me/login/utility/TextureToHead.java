package me.login.utility;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.lang.reflect.Field;
import java.util.Base64;
import java.util.UUID;

public class TextureToHead {

    /**
     * Applies a custom texture from a Minecraft texture URL to a player head.
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

        GameProfile profile = new GameProfile(UUID.randomUUID(), null);

        // Encode the URL in Base64
        String json = "{\"textures\":{\"SKIN\":{\"url\":\"" + textureUrl + "\"}}}";
        String encodedData = Base64.getEncoder().encodeToString(json.getBytes());

        profile.getProperties().put("textures", new Property("textures", encodedData));

        try {
            Field profileField = skullMeta.getClass().getDeclaredField("profile");
            profileField.setAccessible(true);
            profileField.set(skullMeta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
            // This error means the server version might be incompatible with this reflection
            // but this is the standard way for most Spigot/Paper versions.
        }

        item.setItemMeta(skullMeta);
        return item;
    }
}