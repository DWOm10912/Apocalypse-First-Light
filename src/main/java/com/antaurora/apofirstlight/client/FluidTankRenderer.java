package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.util.Mth;
import net.minecraftforge.fluids.FluidStack;

public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity> {
    private static final float TANK_FLUID_EPSILON_MODEL = 0.125F;
    private static final float INNER_MIN_X_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    private static final float INNER_MAX_X_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;
    private static final float INNER_MIN_Y_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    private static final float INNER_MAX_Y_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;
    private static final float INNER_MIN_Z_PIXELS = 1.0F + TANK_FLUID_EPSILON_MODEL;
    private static final float INNER_MAX_Z_PIXELS = 15.0F - TANK_FLUID_EPSILON_MODEL;

    private static final float MIN_X = INNER_MIN_X_PIXELS / 16.0F;
    private static final float MAX_X = INNER_MAX_X_PIXELS / 16.0F;
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

        int stackSize = tank.getStackSize();
        int stackIndex = tank.getStackIndex();
        float fillRatio = Mth.clamp((float) fluid.getAmount() / tank.getCapacity(), 0.0F, 1.0F);
        float globalMinYPixels = INNER_MIN_Y_PIXELS;
        float globalMaxYPixels = stackSize * 16.0F - (16.0F - INNER_MAX_Y_PIXELS);
        float globalFluidTopPixels = Mth.lerp(fillRatio, globalMinYPixels, globalMaxYPixels);
        float memberBasePixels = stackIndex * 16.0F;
        float memberMinPixels = stackIndex == 0 ? INNER_MIN_Y_PIXELS : 0.0F;
        float memberMaxPixels = stackIndex + 1 == stackSize ? INNER_MAX_Y_PIXELS : 16.0F;
        float localTopPixels = Mth.clamp(globalFluidTopPixels - memberBasePixels,
                memberMinPixels, memberMaxPixels);
        if (localTopPixels <= memberMinPixels) {
            return;
        }

        boolean containsSurface = globalFluidTopPixels <= memberBasePixels + memberMaxPixels;
        FluidRenderHelper.renderTankCuboid(fluid, poseStack, buffer, packedLight, packedOverlay,
                MIN_X, memberMinPixels / 16.0F, MIN_Z,
                MAX_X, localTopPixels / 16.0F, MAX_Z, containsSurface);
    }
}
