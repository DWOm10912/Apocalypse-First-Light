package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.WaterDispenserBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** Water Dispenser V2: the generic AFL Animated Block Mesh renderer plus its power cord (Power Outlets V1). */
public final class WaterDispenserRenderer implements BlockEntityRenderer<WaterDispenserBlockEntity> {
    private final AflAnimatedBlockMeshRenderer<WaterDispenserBlockEntity> body;

    public WaterDispenserRenderer(BlockEntityRendererProvider.Context context) {
        body = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(WaterDispenserBlockEntity dispenser) {
        return true;
    }

    /** The chunk draws the resting parts at any distance: the glass, lit parts and goods stay with them longer. */
    @Override
    public int getViewDistance() {
        return com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(WaterDispenserBlockEntity dispenser, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (dispenser.getLevel() == null) return;
        PlugCordRenderer.render(dispenser.plugCord(), dispenser.getLevel(), dispenser.getBlockPos(), partialTick, pose, buffers);
        body.render(dispenser, partialTick, pose, buffers, packedLight, packedOverlay);
    }
}
