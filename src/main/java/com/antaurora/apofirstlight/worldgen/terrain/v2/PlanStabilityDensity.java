package com.antaurora.apofirstlight.worldgen.terrain.v2;

import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * The near-surface stable layer (`apocalypse_firstlight:plan_stability`, Phase 2 2026-10-10): a positive density that
 * the cave graph adds to the cave-entrance and noodle-cave terms (terrain/plan_caves.json), so no cave opens within the
 * column's stable depth below the planned surface: plains and coastal plain 12 blocks, foothills 6, the fold belt 4
 * (mountain slopes keep cave mouths), sea and estuary floors 6. Strength 1.5 per block of shortfall: at the surface
 * the entrance noise would need to fall below -3.6 (it never does), at the stable depth the term vanishes. The cheese
 * and spaghetti caves only start below 12.5 blocks anyway (vanilla's sloped-cheese threshold 1.5625 at 0.125 per block).
 * The carvers get the same depth in mixin/WorldCarverStabilityMixin.
 */
public record PlanStabilityDensity(DensityFunction height, TerrainPlanSurface surface) implements DensityFunction {
    public static final KeyDispatchDataCodec<PlanStabilityDensity> CODEC = KeyDispatchDataCodec.of(
            RecordCodecBuilder.mapCodec(instance -> instance.group(
                    DensityFunction.HOLDER_HELPER_CODEC.fieldOf("height").forGetter(PlanStabilityDensity::height)
            ).apply(instance, h -> new PlanStabilityDensity(h, null))));

    public PlanStabilityDensity withSeed(long seed) { return new PlanStabilityDensity(height, TerrainPlanStore.get(seed)); }

    @Override public double compute(FunctionContext context) {
        if (surface == null) throw new IllegalStateException("AFL plan stability used before seed binding");
        int stable = surface.stableDepth(context.blockX(), context.blockZ());
        if (stable <= 0) return 0;
        double depth = height.compute(context) - context.blockY();
        return depth < stable ? 1.5 * (stable - Math.max(depth, -2)) : 0;
    }
    @Override public void fillArray(double[] values, ContextProvider context) { context.fillAllDirectly(values, this); }
    @Override public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new PlanStabilityDensity(height.mapAll(visitor), surface));
    }
    @Override public double minValue() { return 0; }
    @Override public double maxValue() { return 1.5 * (127 + 2); }
    @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() { return CODEC; }
}
