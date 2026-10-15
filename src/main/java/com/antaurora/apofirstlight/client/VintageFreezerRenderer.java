package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity;
import com.antaurora.apofirstlight.client.blockmesh.AflAnimatedBlockMeshRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The vintage chest freezer (Wood Furniture V2, docs/models/wood_furniture_v2.md): its cord and plug ({@link PlugCordRenderer}),
 * then the cabinet through the generic animated mesh renderer (Furniture Mesh V1, 2026-10-15: the lid's swing, the lit
 * pilot lamp; resting parts drawn by the chunk).
 */
public final class VintageFreezerRenderer implements BlockEntityRenderer<VintageFreezerBlockEntity> {
    private final AflAnimatedBlockMeshRenderer<VintageFreezerBlockEntity> cabinet;

    public VintageFreezerRenderer(BlockEntityRendererProvider.Context context) {
        cabinet = new AflAnimatedBlockMeshRenderer<>(context);
    }

    @Override
    public boolean shouldRenderOffScreen(VintageFreezerBlockEntity freezer) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return AflAnimatedBlockMeshRenderer.VIEW_DISTANCE;
    }

    @Override
    public void render(VintageFreezerBlockEntity freezer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (freezer.getLevel() == null) return;
        PlugCordRenderer.render(freezer.plugCord(), freezer.getLevel(), freezer.getBlockPos(), partialTick, pose, buffers);
        cabinet.render(freezer, partialTick, pose, buffers, light, overlay);
    }
}
