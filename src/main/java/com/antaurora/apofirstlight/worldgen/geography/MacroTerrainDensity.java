package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Bounded surface envelope over the existing cave/noodle density graph, never an empty ocean column. */
public record MacroTerrainDensity(DensityFunction surfaceHeight, DensityFunction underground) implements DensityFunction {
    public static final KeyDispatchDataCodec<MacroTerrainDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("surface_height").forGetter(MacroTerrainDensity::surfaceHeight),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("underground").forGetter(MacroTerrainDensity::underground)
            ).apply(instance, MacroTerrainDensity::new)));

    @Override public double compute(FunctionContext context) {
        double depth = surfaceHeight.compute(context) - context.blockY();
        double surface = Math.max(-1, Math.min(1, depth / 8));
        // A six-block roof prevents shoreline holes, dry ocean columns and startup burial cavities.
        // Below 24 blocks the existing cave graph is fully active, including noodle/spaghetti caves.
        if (depth <= 6) return surface;
        double original = underground.compute(context);
        double t = Math.max(0, Math.min(1, (depth - 6) / 18));
        t = t * t * (3 - 2 * t);
        return Math.min(surface, surface + (original - surface) * t);
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new MacroTerrainDensity(surfaceHeight.mapAll(visitor), underground.mapAll(visitor)));
    }
    @Override public double minValue() { return Math.min(-1, underground.minValue()); }
    @Override public double maxValue() { return 1; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
