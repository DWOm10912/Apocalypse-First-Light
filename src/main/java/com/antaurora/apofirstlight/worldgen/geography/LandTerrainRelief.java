package com.antaurora.apofirstlight.worldgen.geography;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.TerrainProvider;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

/** Recipe expanded BEFORE RandomState noise wiring; never a runtime heightfield. */
public record LandTerrainRelief(DensityFunction continents, DensityFunction erosion,
                               DensityFunction ridges, DensityFunction foldedRidges,
                               DensityFunction baseNoise, DensityFunction jaggedNoise,
                               boolean initial) implements DensityFunction {
    public static final KeyDispatchDataCodec<LandTerrainRelief> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("continents").forGetter(LandTerrainRelief::continents),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("erosion").forGetter(LandTerrainRelief::erosion),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("ridges").forGetter(LandTerrainRelief::ridges),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("folded_ridges").forGetter(LandTerrainRelief::foldedRidges),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("base_noise").forGetter(LandTerrainRelief::baseNoise),
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("jagged_noise").forGetter(LandTerrainRelief::jaggedNoise),
                    Codec.BOOL.optionalFieldOf("initial", false).forGetter(LandTerrainRelief::initial)
            ).apply(instance, LandTerrainRelief::new)));

    /** Mirrors NoiseRouterData.registerTerrainNoises/overworld, with only C/E inputs replaced. */
    public DensityFunction resolve(long seed) {
        var geography = MacroGeography.forSeed(seed);
        var c = coordinate(new LandTerrainBias(continents, erosion, true, geography));
        var e = coordinate(new LandTerrainBias(continents, erosion, false, geography));
        var r = coordinate(ridges);
        var folded = coordinate(foldedRidges);
        DensityFunction offset = splineWithBlending(DensityFunctions.add(
                DensityFunctions.constant((double) -0.50375F),
                DensityFunctions.spline(TerrainProvider.overworldOffset(c, e, folded, false))),
                DensityFunctions.blendOffset());
        DensityFunction factor = splineWithBlending(DensityFunctions.spline(
                TerrainProvider.overworldFactor(c, e, r, folded, false)), DensityFunctions.constant(10));
        DensityFunction depth = DensityFunctions.add(
                DensityFunctions.yClampedGradient(-64, 320, 1.5, -1.5), offset);
        if (initial) {
            // Vanilla preliminary surface density: no jaggedness, base noise, or cave roof.
            return slide(DensityFunctions.add(gradient(DensityFunctions.cache2d(factor), depth),
                    DensityFunctions.constant(-0.703125)).clamp(-64, 64));
        }
        DensityFunction jaggedness = splineWithBlending(DensityFunctions.spline(
                TerrainProvider.overworldJaggedness(c, e, r, folded, false)), DensityFunctions.zero());
        DensityFunction jagged = DensityFunctions.mul(jaggedness, jaggedNoise.halfNegative());
        // Do not interpolate baseNoise separately. The final graph interpolates the entire composition.
        return DensityFunctions.add(gradient(factor, DensityFunctions.add(depth, jagged)), baseNoise);
    }

    private static DensityFunctions.Spline.Coordinate coordinate(DensityFunction function) {
        return new DensityFunctions.Spline.Coordinate(Holder.direct(function));
    }
    private static DensityFunction splineWithBlending(DensityFunction value, DensityFunction fallback) {
        return DensityFunctions.flatCache(DensityFunctions.cache2d(
                DensityFunctions.lerp(DensityFunctions.blendAlpha(), fallback, value)));
    }
    private static DensityFunction gradient(DensityFunction factor, DensityFunction depth) {
        return DensityFunctions.mul(DensityFunctions.constant(4),
                DensityFunctions.mul(depth, factor).quarterNegative());
    }
    private static DensityFunction slide(DensityFunction value) {
        DensityFunction top = DensityFunctions.lerp(
                DensityFunctions.yClampedGradient(240, 256, 1, 0), -0.078125, value);
        return DensityFunctions.lerp(DensityFunctions.yClampedGradient(-64, -40, 0, 1), 0.1171875, top);
    }

    @Override public double compute(FunctionContext context) {
        throw new IllegalStateException("AFL terrain recipe used before RandomState expansion");
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new LandTerrainRelief(continents.mapAll(visitor), erosion.mapAll(visitor),
                ridges.mapAll(visitor), foldedRidges.mapAll(visitor), baseNoise.mapAll(visitor),
                jaggedNoise.mapAll(visitor), initial));
    }
    @Override public double minValue() { return -1_000_000; }
    @Override public double maxValue() { return 1_000_000; }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
