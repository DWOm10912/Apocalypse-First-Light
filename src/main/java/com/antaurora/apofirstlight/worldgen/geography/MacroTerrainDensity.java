package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Macro routing only: LAND returns its vanilla graph unchanged; the old envelope is ocean-only. */
public record MacroTerrainDensity(DensityFunction land, DensityFunction terrain,
                                  DensityFunction underground, boolean initial,
                                  MacroGeography geography) implements DensityFunction {
    public static final KeyDispatchDataCodec<MacroTerrainDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("land").forGetter(MacroTerrainDensity::land),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("terrain").forGetter(MacroTerrainDensity::terrain),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("underground").forGetter(MacroTerrainDensity::underground),
                    Codec.BOOL.optionalFieldOf("initial", false).forGetter(MacroTerrainDensity::initial)
            ).apply(instance, (land, terrain, underground, initial) ->
                    new MacroTerrainDensity(land, terrain, underground, initial, null))));

    public MacroTerrainDensity withSeed(long seed) {
        return new MacroTerrainDensity(land, terrain, underground, initial, MacroGeography.forSeed(seed));
    }
    @Override public double compute(FunctionContext context) {
        if (geography == null) throw new IllegalStateException("AFL macro router used before seed binding");
        double distance = geography.sample(context.blockX(), context.blockZ()).coastDistance();
        // Crucially, no support, clamp, or shallow cave envelope is evaluated in LAND.
        if (distance >= MacroGeography.COAST_WIDTH) return land.compute(context);
        double ocean = initial ? terrain.compute(context) : oceanDensity(context);
        if (distance <= 0) return ocean;
        // Only the existing dry COAST band (0..48 blocks) joins the two independent graphs.
        double t = distance / MacroGeography.COAST_WIDTH;
        t = t * t * (3 - 2 * t);
        return ocean + (land.compute(context) - ocean) * t;
    }
    private double oceanDensity(FunctionContext context) {
        double depth = terrain.compute(context) * 8;
        double surface = Math.max(-1, Math.min(1, depth / 8));
        if (depth <= 6) return surface;
        double original = underground.compute(context);
        double t = Math.max(0, Math.min(1, (depth - 6) / 18));
        t = t * t * (3 - 2 * t);
        return Math.min(surface, surface + (original - surface) * t);
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new MacroTerrainDensity(land.mapAll(visitor), terrain.mapAll(visitor),
                underground.mapAll(visitor), initial, geography));
    }
    @Override public double minValue() {
        return Math.min(land.minValue(), initial ? terrain.minValue() : Math.min(-1, underground.minValue()));
    }
    @Override public double maxValue() {
        return Math.max(land.maxValue(), initial ? terrain.maxValue() : 1);
    }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
