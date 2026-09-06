package com.antaurora.apofirstlight.weapon.client;

import com.antaurora.apofirstlight.ApocalypseFirstLight;
import com.antaurora.apofirstlight.weapon.ServicePistolItem;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public final class ServicePistolModel extends GeoModel<ServicePistolItem> {
    @Override
    public ResourceLocation getModelResource(ServicePistolItem item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "geo/service_pistol.geo.json");
    }
    @Override
    public ResourceLocation getTextureResource(ServicePistolItem item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "textures/item/service_pistol.png");
    }
    @Override
    public ResourceLocation getAnimationResource(ServicePistolItem item) {
        return new ResourceLocation(ApocalypseFirstLight.MOD_ID, "animations/service_pistol.animation.json");
    }
}
