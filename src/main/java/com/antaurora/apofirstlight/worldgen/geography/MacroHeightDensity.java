package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Serialized without a seed; bound once before RandomState wires the router's noise instances. */
public record MacroHeightDensity(MacroGeography geography) implements DensityFunction.SimpleFunction {
    public static final KeyDispatchDataCodec<MacroHeightDensity> CODEC =
            KeyDispatchDataCodec.of(Codec.unit(() -> new MacroHeightDensity(null)));

    public MacroHeightDensity withSeed(long seed) { return new MacroHeightDensity(MacroGeography.forSeed(seed)); }

    @Override public double compute(FunctionContext context) {
        if (geography == null) throw new IllegalStateException("AFL macro height used before RandomState seed binding");
        return geography.sample(context.blockX(), context.blockZ()).surfaceHeight();
    }
    @Override public double minValue() { return -32; }
    @Override public double maxValue() { return 144; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
