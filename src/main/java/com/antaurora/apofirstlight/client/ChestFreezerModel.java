package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.ChestFreezerBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ChestFreezerModel extends GeoModel<ChestFreezerBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/chest_freezer.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/entity/chest_freezer.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/chest_freezer.animation.json");

    @Override public ResourceLocation getModelResource(ChestFreezerBlockEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(ChestFreezerBlockEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(ChestFreezerBlockEntity entity) { return ANIMATION; }
    @Override public RenderType getRenderType(ChestFreezerBlockEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
