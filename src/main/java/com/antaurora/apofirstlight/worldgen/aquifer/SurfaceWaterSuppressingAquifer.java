package com.antaurora.apofirstlight.worldgen.aquifer;

import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Aquifer;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Per-NoiseChunk decorator; no global or thread-local generation state. */
public final class SurfaceWaterSuppressingAquifer implements Aquifer {
    private final Aquifer delegate;
    private final Predicate<DensityFunction.FunctionContext> suppressWater;
    private boolean suppressed;

    public SurfaceWaterSuppressingAquifer(Aquifer delegate,
                                          Predicate<DensityFunction.FunctionContext> suppressWater) {
        this.delegate = delegate;
        this.suppressWater = suppressWater;
    }

    @Override
    @Nullable
    public BlockState computeSubstance(DensityFunction.FunctionContext context, double density) {
        BlockState state = delegate.computeSubstance(context, density);
        suppressed = state != null && state.is(Blocks.WATER) && suppressWater.test(context);
        // Vanilla null means solid terrain: noise fill falls back to the terrain material,
        // while WorldCarver leaves its existing block intact. Never replace water with AIR.
        return suppressed ? null : state;
    }

    @Override
    public boolean shouldScheduleFluidUpdate() {
        return !suppressed && delegate.shouldScheduleFluidUpdate();
    }
}
