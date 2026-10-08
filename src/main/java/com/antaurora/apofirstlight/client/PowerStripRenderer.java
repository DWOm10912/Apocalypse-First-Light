package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.PowerStripBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/** A power strip's cord and plug (docs/models/power_outlets_v1.md; the strip itself is baked): {@link PlugCordRenderer}. */
public final class PowerStripRenderer implements BlockEntityRenderer<PowerStripBlockEntity> {
    public PowerStripRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(PowerStripBlockEntity strip) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public void render(PowerStripBlockEntity strip, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        if (strip.getLevel() != null) PlugCordRenderer.render(strip.plugCord(), strip.getLevel(), strip.getBlockPos(), partialTick, pose, buffers);
    }
}
