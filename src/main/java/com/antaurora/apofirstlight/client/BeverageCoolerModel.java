package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.BeverageCoolerBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class BeverageCoolerModel extends GeoModel<BeverageCoolerBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "geo/beverage_cooler.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "textures/entity/beverage_cooler.png");
    private static final ResourceLocation ANIMATION = new ResourceLocation(ApocalypseFirstLight.MOD_ID,
            "animations/beverage_cooler.animation.json");

    @Override public ResourceLocation getModelResource(BeverageCoolerBlockEntity entity) { return MODEL; }
    @Override public ResourceLocation getTextureResource(BeverageCoolerBlockEntity entity) { return TEXTURE; }
    @Override public ResourceLocation getAnimationResource(BeverageCoolerBlockEntity entity) { return ANIMATION; }
    @Override public RenderType getRenderType(BeverageCoolerBlockEntity entity, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }
}
