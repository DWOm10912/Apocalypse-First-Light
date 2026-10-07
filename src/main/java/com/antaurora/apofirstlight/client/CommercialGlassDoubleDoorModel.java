package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.antaurora.apofirstlight.registry.AflBlocks;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class CommercialGlassDoubleDoorModel extends GeoModel<CommercialGlassDoubleDoorBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "geo/commercial_glass_double_door.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/entity/commercial_glass_double_door.png");
    /** The black anodized variant (tools/build-storefront-glazing-v1.mjs recolours the silver texture). */
    private static final ResourceLocation TEXTURE_BLACK = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/entity/commercial_glass_double_door_black.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "animations/commercial_glass_double_door.animation.json");

    @Override
    public ResourceLocation getModelResource(CommercialGlassDoubleDoorBlockEntity door) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(CommercialGlassDoubleDoorBlockEntity door) {
        return door.getBlockState().is(AflBlocks.COMMERCIAL_GLASS_DOUBLE_DOOR_BLACK.get()) ? TEXTURE_BLACK : TEXTURE;
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
