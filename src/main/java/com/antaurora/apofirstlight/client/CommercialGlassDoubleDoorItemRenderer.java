package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * Commercial Glass Double Door V2 item: the block's Pure Mesh (leaves closed) and the atlas of its finish, through the
 * AFL Mesh item renderer; the item model's views are fitted by tools/build-commercial-glass-double-door-v2.mjs.
 */
public final class CommercialGlassDoubleDoorItemRenderer {
    private static final ResourceLocation GEOMETRY =
            new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/commercial_glass_double_door.geo.json");

    /** block: the silver or the black door. */
    public static AflStaticMeshItemRenderer create(Block block) {
        return new AflStaticMeshItemRenderer(GEOMETRY, new ResourceLocation(ApocalypseFirstLight.MOD_ID,
                block == AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK.get()
                        ? "textures/block/commercial_glass_double_door_black.png" : "textures/block/commercial_glass_double_door.png"), 0.0, 0.0);
    }

    private CommercialGlassDoubleDoorItemRenderer() {}
}
