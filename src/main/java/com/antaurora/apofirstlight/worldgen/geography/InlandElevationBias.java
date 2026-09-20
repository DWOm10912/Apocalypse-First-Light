package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Continuous X/Z-only offset added after terrain splines, before vanilla offset blending. */
public record InlandElevationBias(MacroGeography geography) implements DensityFunction.SimpleFunction {
    private static final double INLAND_RAMP_WIDTH = 384;
    // Vanilla depth falls by 3 / (320 - (-64)) = 1/128 per block.
    // This offset shifts that gradient by 6 blocks; it is not a minimum surface Y.
    private static final double FULL_OFFSET_BIAS = 0.046875;

    public static final KeyDispatchDataCodec<InlandElevationBias> CODEC = KeyDispatchDataCodec.of(
            Codec.unit(() -> new InlandElevationBias(null)));

    public InlandElevationBias withSeed(long seed) {
        return new InlandElevationBias(MacroGeography.forSeed(seed));
    }

    @Override public double compute(FunctionContext context) {
        if (geography == null) throw new IllegalStateException("AFL inland elevation used before seed binding");
        var sample = geography.sample(context.blockX(), context.blockZ());
        if (sample.nationId() != MacroGeographySample.NationId.MAIN_NATION) return 0;
        double t = Math.max(0, Math.min(1,
                (sample.coastDistance() - MacroGeography.COAST_WIDTH) / INLAND_RAMP_WIDTH));
        return FULL_OFFSET_BIAS * t * t * (3 - 2 * t);
    }

    @Override public double minValue() { return 0; }
    @Override public double maxValue() { return FULL_OFFSET_BIAS; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
