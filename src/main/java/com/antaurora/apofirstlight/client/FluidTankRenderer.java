package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity> {
    private static final float INNER_MIN_X_PIXELS = 1.02F;
    private static final float INNER_MAX_X_PIXELS = 14.98F;
    private static final float INNER_MIN_Y_PIXELS = 1.02F;
    private static final float INNER_MAX_Y_PIXELS = 14.98F;
    private static final float INNER_MIN_Z_PIXELS = 1.02F;
    private static final float INNER_MAX_Z_PIXELS = 14.98F;

    private static final float MIN_X = INNER_MIN_X_PIXELS / 16.0F;
    private static final float MAX_X = INNER_MAX_X_PIXELS / 16.0F;
    private static final float MIN_Y = INNER_MIN_Y_PIXELS / 16.0F;
    private static final float MAX_Y = INNER_MAX_Y_PIXELS / 16.0F;
    private static final float MIN_Z = INNER_MIN_Z_PIXELS / 16.0F;
    private static final float MAX_Z = INNER_MAX_Z_PIXELS / 16.0F;

    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidTankBlockEntity tank, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            return;
        }

        float fillRatio = Mth.clamp((float) fluid.getAmount() / tank.getCapacity(), 0.0F, 1.0F);
        float topY = MIN_Y + (MAX_Y - MIN_Y) * fillRatio;
        FluidRenderHelper.renderCuboid(fluid, poseStack, buffer, packedLight, packedOverlay,
                MIN_X, MIN_Y, MIN_Z, MAX_X, topY, MAX_Z);
    }
}
