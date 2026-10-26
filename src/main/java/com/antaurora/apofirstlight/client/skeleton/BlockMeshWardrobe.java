package com.antaurora.apofirstlight.client.skeleton;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.client.survivor.SurvivorIdentityClient;
import com.antaurora.apofirstlight.equipment.AflEquipmentSlots;
import com.antaurora.apofirstlight.registry.AflItems;
import com.antaurora.apofirstlight.survivor.SurvivorAppearance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What the B5 body draws (character creation V1, docs/ui/character_creation_v1.md, user 2026-10-26): the body in the player's
 * skin tone, the briefs, the hair style, and each garment worn (the shirt in the base layer slot, the jacket in the coat
 * slot, the jeans in the legs slot, the shoes in the feet slot), every one its own mesh and texture
 * (skins/block_mesh_v2/b5/, textures/entity/block_mesh_v2/b5/; the b5_export text block in
 * design/characters/block_mesh_v2/b4_blender/b4_male_base.blend). All share B3's 22 bones, so one set of matrices skins
 * them all. Inside out: body, briefs, jeans, shoes, shirt, jacket, hair.
 */
public final class BlockMeshWardrobe {
    private static final String MESHES = "skins/block_mesh_v2/b5/", TEXTURES = "textures/entity/block_mesh_v2/b5/";

    /** One mesh and its texture. */
    public record Layer(String mesh, ResourceLocation texture) {
    }

    private BlockMeshWardrobe() {
    }

    /** The mesh file of a B5 part ("body", "shirt", "hair_short"...). */
    static String mesh(String key) {
        return MESHES + key + ".json";
    }

    private static ResourceLocation texture(String file) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, TEXTURES + file + ".png");
    }

    /** The layers for a look and an outfit mask (SurvivorAppearance.Garment bits): the appearance page's preview. */
    public static List<Layer> layers(int tone, String hair, int outfit) {
        List<Layer> out = new ArrayList<>(7);
        int t = tone < 1 || tone > SurvivorAppearance.TONES ? SurvivorAppearance.DEFAULT_TONE : tone;
        out.add(PARTS.computeIfAbsent("body_t" + t, k -> new Layer(mesh("body"), texture(k))));
        out.add(part("briefs"));
        if (wears(outfit, SurvivorAppearance.Garment.JEANS)) out.add(part("jeans"));
        if (wears(outfit, SurvivorAppearance.Garment.SHOES)) out.add(part("shoes"));
        if (wears(outfit, SurvivorAppearance.Garment.SHIRT)) out.add(part("shirt"));
        if (wears(outfit, SurvivorAppearance.Garment.JACKET)) out.add(part("jacket"));
        if (!"bald".equals(hair)) out.add(part("hair_" + (SurvivorAppearance.HAIR.contains(hair) ? hair : SurvivorAppearance.DEFAULT_HAIR)));
        return out;
    }

    /** The layers a player draws: its survivor's tone and hair (SurvivorIdentityClient), what it wears; null: the defaults, unclothed. */
    public static List<Layer> layers(@Nullable Player player) {
        if (player == null) return layers(SurvivorAppearance.DEFAULT_TONE, SurvivorAppearance.DEFAULT_HAIR, 0);
        SurvivorIdentityClient.Body body = SurvivorIdentityClient.body(player.getUUID());
        return layers(body.tone(), body.hair(), outfit(player));
    }

    /** The garments a player wears, as an outfit mask. */
    static int outfit(Player player) {
        int mask = 0;
        if (is(AflEquipmentSlots.get(player, AflEquipmentSlots.BASE), SurvivorAppearance.Garment.SHIRT)) mask |= SurvivorAppearance.Garment.SHIRT.bit();
        if (is(AflEquipmentSlots.get(player, AflEquipmentSlots.COAT), SurvivorAppearance.Garment.JACKET)) mask |= SurvivorAppearance.Garment.JACKET.bit();
        if (player.getItemBySlot(EquipmentSlot.LEGS).is(AflItems.CLOTHING_JEANS.get())) mask |= SurvivorAppearance.Garment.JEANS.bit();
        if (player.getItemBySlot(EquipmentSlot.FEET).is(AflItems.CLOTHING_SHOES.get())) mask |= SurvivorAppearance.Garment.SHOES.bit();
        return mask;
    }

    private static boolean is(ItemStack stack, SurvivorAppearance.Garment g) {
        return stack.is(g.item());
    }

    private static boolean wears(int outfit, SurvivorAppearance.Garment g) {
        return (outfit & g.bit()) != 0;
    }

    private static final java.util.Map<String, Layer> PARTS = new java.util.HashMap<>();

    /** A part in its own texture, made once (drawn every frame). */
    private static Layer part(String key) {
        return PARTS.computeIfAbsent(key, k -> new Layer(mesh(k), texture(k)));
    }
}
