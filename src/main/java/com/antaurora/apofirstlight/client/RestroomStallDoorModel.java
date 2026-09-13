package com.antaurora.apofirstlight.client;
import com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.model.GeoModel;
public final class RestroomStallDoorModel extends GeoModel<RestroomStallDoorBlockEntity> {
    @Override public ResourceLocation getModelResource(RestroomStallDoorBlockEntity d){return new ResourceLocation("apocalypse_firstlight","geo/restroom_stall_door.geo.json");}
    @Override public ResourceLocation getTextureResource(RestroomStallDoorBlockEntity d){return new ResourceLocation("apocalypse_firstlight","textures/entity/restroom_stall_door.png");}
    @Override public ResourceLocation getAnimationResource(RestroomStallDoorBlockEntity d){return new ResourceLocation("apocalypse_firstlight","animations/restroom_stall_door.animation.json");}
    @Override public RenderType getRenderType(RestroomStallDoorBlockEntity d,ResourceLocation t){return RenderType.entityCutoutNoCull(t);}
}
