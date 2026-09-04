package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity> {
    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidTankBlockEntity tank, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) {
            return;
        }

        int stackSize = tank.getStackSize();
        int stackIndex = tank.getStackIndex();
        float fillRatio = Mth.clamp((float) fluid.getAmount() / tank.getCapacity(), 0.0F, 1.0F);
        float globalMinYPixels = FluidTankRenderGeometry.INNER_MIN_Y_PIXELS;
        float globalMaxYPixels = stackSize * 16.0F
                - (16.0F - FluidTankRenderGeometry.INNER_MAX_Y_PIXELS);
        float globalFluidTopPixels = Mth.lerp(fillRatio, globalMinYPixels, globalMaxYPixels);
        float memberBasePixels = stackIndex * 16.0F;
        float memberMinPixels = stackIndex == 0 ? FluidTankRenderGeometry.INNER_MIN_Y_PIXELS : 0.0F;
        float memberMaxPixels = stackIndex + 1 == stackSize
                ? FluidTankRenderGeometry.INNER_MAX_Y_PIXELS : 16.0F;
        float localTopPixels = Mth.clamp(globalFluidTopPixels - memberBasePixels,
                memberMinPixels, memberMaxPixels);
        if (localTopPixels <= memberMinPixels) {
            return;
        }

        boolean containsSurface = globalFluidTopPixels <= memberBasePixels + memberMaxPixels;
        FluidRenderHelper.renderTankCuboid(fluid, poseStack, buffer, packedLight, packedOverlay,
                FluidTankRenderGeometry.MIN_X, memberMinPixels / 16.0F, FluidTankRenderGeometry.MIN_Z,
                FluidTankRenderGeometry.MAX_X, localTopPixels / 16.0F, FluidTankRenderGeometry.MAX_Z,
                containsSurface);
    }
}
