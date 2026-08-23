package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.CommercialGlassDoubleDoorBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.Direction;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

public class CommercialGlassDoubleDoorRenderer extends GeoBlockRenderer<CommercialGlassDoubleDoorBlockEntity> {
    public CommercialGlassDoubleDoorRenderer(BlockEntityRendererProvider.Context context) {
        super(new CommercialGlassDoubleDoorModel());
    }

    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack) {
        super.rotateBlock(facing, poseStack);
        poseStack.translate(0.0, 0.0, 3.0 / 16.0);
    }
}
