package com.antaurora.apofirstlight.fluid;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraftforge.fluids.FluidStack;

/** Fluid-only emission; container materials retain normal environmental shading. */
public final class FluidLighting {
    public static final IntegerProperty LIGHT = IntegerProperty.create("fluid_light", 0, 9);

    private FluidLighting() { }

    public static int intrinsic(FluidStack fluid) {
        return fluid.isEmpty() ? 0 : Math.max(0, Math.min(15,
                fluid.getFluid().getFluidType().getLightLevel(fluid)));
    }

    public static int emission(FluidStack fluid, int maximum) {
        int light = intrinsic(fluid);
        return light == 0 ? 0 : Math.max(1, (light * maximum + 7) / 15);
    }

    public static void update(ServerLevel level, BlockPos pos, int light) {
        if (!level.hasChunkAt(pos)) return;
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(LIGHT) && state.getValue(LIGHT) != light) {
            level.setBlock(pos, state.setValue(LIGHT, light), 2);
        }
    }
}
