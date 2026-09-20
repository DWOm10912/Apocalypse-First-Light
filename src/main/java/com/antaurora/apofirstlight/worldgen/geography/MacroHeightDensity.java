package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Ocean seafloor only, extended at Y62 through dry COAST; never a LAND datum. */
public record MacroHeightDensity(MacroGeography geography) implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<MacroHeightDensity> CODEC =
            KeyDispatchDataCodec.of(Codec.unit(() -> new MacroHeightDensity(null)));

    public MacroHeightDensity withSeed(long seed) { return new MacroHeightDensity(MacroGeography.forSeed(seed)); }

    @Override public double compute(FunctionContext context) {
        if (geography == null) throw new IllegalStateException("AFL macro height used before RandomState seed binding");
        var sample = geography.sample(context.blockX(), context.blockZ());
        if (sample.isWater()) return sample.surfaceHeight();
        return MacroGeography.SEA_LEVEL - 1;
    }
    @Override public double minValue() { return -32; }
    @Override public double maxValue() { return MacroGeography.SEA_LEVEL - 1; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
