package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.blockentity.LeadChestBlockEntity;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class LeadChestGeoModel extends GeoModel<LeadChestBlockEntity> {
    private static final ResourceLocation MODEL = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "geo/lead_chest.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "textures/entity/storage/lead_chest.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(
            ApocalypseFirstLight.MOD_ID, "animations/lead_chest.animation.json");

    @Override
    public ResourceLocation getModelResource(LeadChestBlockEntity leadChest) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(LeadChestBlockEntity leadChest) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(LeadChestBlockEntity leadChest) {
        return ANIMATIONS;
    }

    @Override
    public RenderType getRenderType(LeadChestBlockEntity leadChest, ResourceLocation texture) {
        return RenderType.entityCutoutNoCull(texture);
    }
}
