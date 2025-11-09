package me.login.utility;

// Import the modern Paper PlayerProfile
import com.destroystokyo.paper.profile.PlayerProfile;
// Import the standard Bukkit PlayerTextures
import org.bukkit.profile.PlayerTextures;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.net.URL;
import java.util.UUID;

public class TextureToHead {

    /**
     * Creates a player head ItemStack from a given texture URL.
     * This implementation is for the Paper API, which uses a Paper
     * PlayerProfile but a Bukkit PlayerTextures object.
     *
     * @param url The URL of the skin texture (e.g., from minecraft-heads.com).
     * @return An ItemStack (Material.PLAYER_HEAD) with the custom texture applied.
     */
    public static ItemStack createHeadFromUrl(URL url) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) head.getItemMeta();

        // 1. Create a Paper PlayerProfile.
        //    (The cast is redundant, as the warning said, so we remove it).
        PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());

        // 2. Get the textures. This method on the Paper profile returns
        //    the standard 'org.bukkit.profile.PlayerTextures' type.
        PlayerTextures textures = profile.getTextures();

        // 3. Set the skin URL on the Bukkit PlayerTextures object.
        //    This will now resolve correctly.
        textures.setSkin(url);

        // 4. Set the modified textures back to the Paper profile.
        //    This now works because 'textures' is the correct type.
        profile.setTextures(textures);

        // 5. Set the Paper profile to the SkullMeta.
        skullMeta.setPlayerProfile(profile);

        // Apply the meta back to the item
        head.setItemMeta(skullMeta);

        return head;
    }
}