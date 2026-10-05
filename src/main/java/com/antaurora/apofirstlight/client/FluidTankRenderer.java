package com.antaurora.apofirstlight.client;

import com.antaurora.apofirstlight.block.FluidTankBlock;
import com.antaurora.apofirstlight.blockentity.FluidTankBlockEntity;
import com.antaurora.apofirstlight.fluid.FluidTankStructures;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.fluids.FluidStack;

/**
 * Fluid Tank V2: each cell draws its part of its tank's fluid. The tank fills from the bottom layer up; the surface
 * height over the whole tank is linear in the fill (from the bottom deck to the top deck). A cell's fluid reaches its
 * joined sides (so the joined tank shows one body of fluid) and stops inside the glass on the others; it draws a face
 * only where nothing of the same tank lies beyond it.
 */
public final class FluidTankRenderer implements BlockEntityRenderer<FluidTankBlockEntity> {
    public FluidTankRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(FluidTankBlockEntity tank, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight, int packedOverlay) {
        FluidStack fluid = tank.getFluid();
        if (fluid.isEmpty()) return;
        BlockState state = tank.getBlockState();
        if (!(state.getBlock() instanceof FluidTankBlock)) return;
        FluidTankStructures.Shape shape = tank.shape();
        float fill = Mth.clamp((float) fluid.getAmount() / Math.max(1, tank.getCapacity()), 0.0F, 1.0F);
        float bottom = FluidTankRenderGeometry.INNER_MIN_Y_PIXELS, top = shape.sy() * 16.0F - (16.0F - FluidTankRenderGeometry.INNER_MAX_Y_PIXELS);
        float surface = Mth.lerp(fill, bottom, top);
        float base = shape.layerOf(tank.getBlockPos()) * 16.0F;
        boolean up = state.getValue(FluidTankBlock.JOINED.get(Direction.UP)), down = state.getValue(FluidTankBlock.JOINED.get(Direction.DOWN));
        float y0 = down ? 0.0F : FluidTankRenderGeometry.INNER_MIN_Y_PIXELS;
        float y1 = Math.min(up ? 16.0F : FluidTankRenderGeometry.INNER_MAX_Y_PIXELS, surface - base);
        if (y1 <= y0) return;
        boolean n = joined(state, Direction.NORTH), s = joined(state, Direction.SOUTH), w = joined(state, Direction.WEST), e = joined(state, Direction.EAST);
        int faces = 0;
        if (!n) faces |= FluidRenderHelper.faceBit(Direction.NORTH);
        if (!s) faces |= FluidRenderHelper.faceBit(Direction.SOUTH);
        if (!w) faces |= FluidRenderHelper.faceBit(Direction.WEST);
        if (!e) faces |= FluidRenderHelper.faceBit(Direction.EAST);
        if (!down) faces |= FluidRenderHelper.faceBit(Direction.DOWN);
        if (!up || surface - base < 16.0F) faces |= FluidRenderHelper.faceBit(Direction.UP);   // the surface, or the top deck
        FluidRenderHelper.renderBox(fluid, false, poseStack, buffer, packedLight, packedOverlay,
                w ? 0.0F : FluidTankRenderGeometry.MIN_X, y0 / 16.0F, n ? 0.0F : FluidTankRenderGeometry.MIN_Z,
                e ? 1.0F : FluidTankRenderGeometry.MAX_X, y1 / 16.0F, s ? 1.0F : FluidTankRenderGeometry.MAX_Z, faces);
    }

    private static boolean joined(BlockState state, Direction direction) {
        return state.getValue(FluidTankBlock.JOINED.get(direction));
    }
}
