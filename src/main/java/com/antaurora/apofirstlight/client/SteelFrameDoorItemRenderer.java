package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import net.minecraft.resources.ResourceLocation;

/**
 * Steel-frame doors V1 items (steel_door, commercial_wood_door): the right-hinge Pure Mesh, leaf closed, and the door's
 * atlas, through the AFL Mesh item renderer (the wood door's lite and restroom bones are neverRender: the item is the
 * plain door). The item model's views are fitted by tools/build-steel-frame-doors-v1.mjs.
 */
public final class SteelFrameDoorItemRenderer {
    public static AflStaticMeshItemRenderer create(String id) {
        return new AflStaticMeshItemRenderer(new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/" + id + "_right.geo.json"),
                new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/block/" + id + ".png"), 0.0, 0.0);
    }

    private SteelFrameDoorItemRenderer() {}
}
