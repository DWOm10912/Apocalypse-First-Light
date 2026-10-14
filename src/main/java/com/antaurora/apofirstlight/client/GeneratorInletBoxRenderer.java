package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.GeneratorInletBoxBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * Generator Inlet Box V1: the generic AFL Animated Block Mesh renderer plus its 15 m cord (PlugCordRenderer; nothing while
 * the cord hangs coiled on the hook, which is the mesh's coil part).
 */
public final class GeneratorInletBoxRenderer implements BlockEntityRenderer<GeneratorInletBoxBlockEntity> {
    private final AflAnimatedBlockMeshRenderer<GeneratorInletBoxBlockEntity> body;

    public GeneratorInletBoxRenderer(BlockEntityRendererProvider.Context context) {
        body = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(GeneratorInletBoxBlockEntity inlet) {
        return true;
    }

    /** The chunk draws the resting parts at any distance; the cord stays with the renderer (out to the cord's reach). */
    @Override
    public int getViewDistance() {
        return AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(GeneratorInletBoxBlockEntity inlet, float partialTick, PoseStack pose, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        if (inlet.getLevel() == null) return;
        PlugCordRenderer.render(inlet.plugCord(), inlet.getLevel(), inlet.getBlockPos(), partialTick, pose, buffers);
        body.render(inlet, partialTick, pose, buffers, packedLight, packedOverlay);
    }
}
