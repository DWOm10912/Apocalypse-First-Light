package com.antaurora.apofirstlight.client;
import com.antaurora.apofirstlight.block.RestroomStallDoorBlock;
import com.antaurora.apofirstlight.blockentity.RestroomStallDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import software.bernie.geckolib.renderer.GeoBlockRenderer;
public final class RestroomStallDoorRenderer extends GeoBlockRenderer<RestroomStallDoorBlockEntity> {
    public RestroomStallDoorRenderer(BlockEntityRendererProvider.Context c){super(new RestroomStallDoorModel());}
    @Override protected void rotateBlock(Direction f,PoseStack p){
        // Export is centered and compensates Gecko's X bake; match HorizontalShapeUtils exactly.
        p.mulPose(com.mojang.math.Axis.YP.rotationDegrees(switch(f){case EAST -> -90;case SOUTH -> 180;case WEST -> 90;default -> 0;}));
        if(animatable.getBlockState().getValue(RestroomStallDoorBlock.HINGE)==DoorHingeSide.RIGHT)p.scale(-1,1,1);
    }
}
