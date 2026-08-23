package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CommercialGlassDoubleDoorModel extends GeoModel<CommercialGlassDoubleDoorBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "geo/glass_door.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/entity/glass_door.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "animations/glass_door.animation.json");

    @Override
    public ResourceLocation getModelResource(CommercialGlassDoubleDoorBlockEntity door) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CommercialGlassDoubleDoorBlockEntity door) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(CommercialGlassDoubleDoorBlockEntity door) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(CommercialGlassDoubleDoorBlockEntity door, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
