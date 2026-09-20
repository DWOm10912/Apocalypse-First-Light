package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/** Terrain-only C/E remapping before vanilla splines. No Y, target height, or density postprocessing. */
public record LandTerrainBias(DensityFunction continents, DensityFunction erosion,
                              boolean continentalness, MacroGeography geography) implements DensityFunction {
    public static final KeyDispatchDataCodec<LandTerrainBias> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(LandTerrainBias::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("erosion").forGetter(LandTerrainBias::erosion),
                    Codec.BOOL.fieldOf("continentalness").forGetter(LandTerrainBias::continentalness)
            ).apply(instance, (c, e, selectC) -> new LandTerrainBias(c, e, selectC, null))));

    public LandTerrainBias withSeed(long seed) {
        return new LandTerrainBias(continents, erosion, continentalness, MacroGeography.forSeed(seed));
    }
    @Override public double compute(FunctionContext context) {
        if (geography == null) throw new IllegalStateException("AFL terrain bias used before seed binding");
        double radius = Math.hypot(context.blockX(), context.blockZ());
        var sample = geography.sample(context.blockX(), context.blockZ());
        boolean mainland = sample.landmassRole() == MacroGeographySample.LandmassRole.MAINLAND;
        double outsideStartup = smooth((radius - MacroGeography.STARTUP_MAINLAND_RESERVE)
                / MacroGeography.STARTUP_MAINLAND_RESERVE);
        double outer = mainland ? smooth((radius - MacroGeography.MAINLAND_CORE_RADIUS) / 1024) : 0;
        double c = clamp(continents.compute(context), -1, 1);
        double e = clamp(erosion.compute(context), -1, 1);
        double rolling = descending(e, -.10, -.45) * outsideStartup
                * (mainland ? lerp(.65, 1, outer) : .50);
        double highland = descending(e, -.40, -.60) * outer;
        double mountain = descending(e, -.70, -.82) * outer * smooth((c - .35) / .30);
        if (continentalness) {
            // Inland parameter space, not minimum surface density. No vanilla ocean C values.
            return lerp(lerp(.12 + .04 * c, .35, highland), .65, mountain);
        }
        return lerp(lerp(lerp(.65 + .04 * e, .15, rolling), -.35, highland), -.80, mountain);
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new LandTerrainBias(continents.mapAll(visitor), erosion.mapAll(visitor),
                continentalness, geography));
    }
    // Conservative bounds include floating-point interpolation roundoff.
    @Override public double minValue() { return continentalness ? .07 : -.81; }
    @Override public double maxValue() { return .70; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
    private static double descending(double value, double start, double full) { return smooth((start - value) / (start - full)); }
    private static double clamp(double value, double min, double max) { return Math.max(min, Math.min(max, value)); }
    private static double smooth(double t) { t = clamp(t, 0, 1); return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
}
