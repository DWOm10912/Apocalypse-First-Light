package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.cabin.VintageFreezerBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/**
 * The vintage chest freezer's cord and plug (Wood Furniture V2, docs/models/wood_furniture_v2.md; the freezer itself, its lid
 * and pilot lamp are chunk-drawn cabin meshes): {@link PlugCordRenderer}.
 */
public final class VintageFreezerRenderer implements BlockEntityRenderer<VintageFreezerBlockEntity> {
    public VintageFreezerRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(VintageFreezerBlockEntity freezer) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(VintageFreezerBlockEntity freezer, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (freezer.getLevel() != null) PlugCordRenderer.render(freezer.plugCord(), freezer.getLevel(), freezer.getBlockPos(), partialTick, pose, buffers);
    }
}
