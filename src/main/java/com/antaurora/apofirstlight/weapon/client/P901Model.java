package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.P901Item;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class P901Model extends GeoModel<P901Item> {
    @Override
    public ResourceLocation getModelResource(P901Item item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/p9_01.geo.json");
    }
    @Override
    public ResourceLocation getTextureResource(P901Item item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/p9_01.png");
    }
    @Override
    public ResourceLocation getAnimationResource(P901Item item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/p9_01.animation.json");
    }
}
